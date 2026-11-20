package com.sokhasaclinic.clinicbackend.notification.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.model.TelegramSubscription;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository;
import com.sokhasaclinic.clinicbackend.notification.repository.PatientTelegramRepository;
import com.sokhasaclinic.clinicbackend.notification.repository.PatientTelegramRepository.BookingView;
import com.sokhasaclinic.clinicbackend.notification.repository.TelegramSubscriptionRepository;
import com.sokhasaclinic.clinicbackend.notification.service.PatientMessageFormatter.Reply;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patient Telegram updates (PT-01 to PT-12): booking link codes, the bot's /start and /stop, and
 * the CONFIRMED, REJECTED and REMINDER messages. Everything is queued in the notification outbox
 * in the caller's transaction, so a Telegram outage never blocks or rolls back reception's action.
 */
@Service
public class PatientTelegramService {

    private static final Logger log = LoggerFactory.getLogger(PatientTelegramService.class);
    static final Duration LINK_LIFETIME = Duration.ofHours(24);
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Set<String> LINKABLE = Set.of("PENDING", "CONFIRMED");

    /** The parts of a Telegram {@code Update} the webhook reads; Telegram sends many more fields. */
    public record Update(@JsonProperty("update_id") Long updateId, Message message) { }

    public record Message(Chat chat, From from, String text) { }

    public record Chat(Long id, String type) { }

    public record From(@JsonProperty("language_code") String languageCode) { }

    private final TelegramProperties properties;
    private final PatientTelegramRepository patients;
    private final TelegramSubscriptionRepository subscriptions;
    private final NotificationJobRepository jobs;
    private final PatientMessageFormatter messages;
    private final Clock clock;

    @Autowired
    public PatientTelegramService(TelegramProperties properties, PatientTelegramRepository patients,
            TelegramSubscriptionRepository subscriptions, NotificationJobRepository jobs,
            PatientMessageFormatter messages) {
        this(properties, patients, subscriptions, jobs, messages, Clock.systemUTC());
    }

    PatientTelegramService(TelegramProperties properties, PatientTelegramRepository patients,
            TelegramSubscriptionRepository subscriptions, NotificationJobRepository jobs,
            PatientMessageFormatter messages, Clock clock) {
        this.properties = properties;
        this.patients = patients;
        this.subscriptions = subscriptions;
        this.jobs = jobs;
        this.messages = messages;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.patientUpdatesEnabled();
    }

    // ---- Link codes (PT-02) ---------------------------------------------------------------------

    /** The booking's "Open Telegram" link, or null when patient updates are off (PT-12). */
    public String linkUrl(UUID appointmentId) {
        if (!enabled()) return null;
        return "https://t.me/" + properties.getPatient().getBotUsername() + "?start=" + code(appointmentId);
    }

    /** Stores the new booking's link code, valid for 24 hours from booking. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingCreated(UUID appointmentId, Instant createdAt) {
        if (!enabled()) return;
        patients.insertLink(appointmentId, sha256(code(appointmentId)), createdAt.plus(LINK_LIFETIME));
    }

    /** Deterministic, so an idempotent booking replay (AP-03) returns the same link. */
    String code(UUID appointmentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getPatient().getLinkSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(appointmentId.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    // ---- Reception changes (PT-05, PT-07) -------------------------------------------------------

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmed(UUID appointmentId) {
        queue(appointmentId, "PATIENT_CONFIRMED");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void rejected(UUID appointmentId) {
        queue(appointmentId, "PATIENT_REJECTED");
    }

    /** PT-10: whether the patient currently receives Telegram updates for this booking. */
    public boolean connected(UUID appointmentId) {
        return subscriptions.findByAppointmentIdAndRevokedAtIsNull(appointmentId).isPresent();
    }

    private void queue(UUID appointmentId, String event) {
        if (!enabled()) return;
        Optional<TelegramSubscription> subscription = subscriptions.findByAppointmentIdAndRevokedAtIsNull(appointmentId);
        if (subscription.isEmpty()) return;
        BookingView booking = patients.view(appointmentId).orElseThrow();
        String message = switch (event) {
            case "PATIENT_CONFIRMED" -> messages.confirmed(booking);
            case "PATIENT_REJECTED" -> messages.rejected(booking);
            case "PATIENT_REMINDER" -> messages.reminder(booking);
            default -> throw new IllegalArgumentException(event);
        };
        jobs.enqueuePatient(appointmentId, event, subscription.get().getChatId(), message,
                subscription.get().getId(), clock.instant());
    }

    // ---- Reminders (PT-08) ----------------------------------------------------------------------

    @Scheduled(cron = "${clinic.telegram.patient.reminder-cron:0 0 17 * * *}", zone = "Asia/Phnom_Penh")
    public void remindTomorrow() {
        if (!enabled()) return;
        int queued = queueReminders(LocalDate.now(clock.withZone(ClinicTime.ZONE)).plusDays(1));
        if (queued > 0) log.info("Queued {} Telegram appointment reminders", queued);
    }

    /** Queues one reminder per confirmed visit on {@code day} with an active subscription. */
    @Transactional
    public int queueReminders(LocalDate day) {
        if (!enabled()) return 0;
        Instant from = day.atStartOfDay(ClinicTime.ZONE).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ClinicTime.ZONE).toInstant();
        int count = 0;
        for (var target : patients.reminderTargets(from, to)) {
            BookingView booking = patients.view(target.appointmentId()).orElseThrow();
            jobs.enqueuePatient(target.appointmentId(), "PATIENT_REMINDER", target.chatId(),
                    messages.reminder(booking), target.subscriptionId(), clock.instant());
            count++;
        }
        return count;
    }

    // ---- Bot commands (PT-03, PT-04, PT-09) -----------------------------------------------------

    /** Handles one authenticated webhook update. Anything that is not a private-chat command is ignored. */
    @Transactional
    public void handle(Update update) {
        if (!enabled() || update == null || update.message() == null) return;
        Message message = update.message();
        if (message.chat() == null || message.chat().id() == null || message.text() == null
                || !"private".equals(message.chat().type()) || message.chat().id() <= 0) {
            return;
        }
        long chatId = message.chat().id();
        String language = PatientMessageFormatter.language(message.from() == null ? null : message.from().languageCode());
        String[] parts = message.text().strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(java.util.Locale.ROOT).replaceFirst("@.*$", "");
        String argument = parts.length > 1 ? parts[1].strip() : "";
        switch (command) {
            case "/start" -> {
                if (argument.isEmpty()) reply(chatId, Reply.HELP, language);
                else link(chatId, argument, language);
            }
            case "/stop" -> stop(chatId, language);
            default -> reply(chatId, Reply.HELP, language);
        }
    }

    private void link(long chatId, String code, String language) {
        if (!CODE.matcher(code).matches()) {
            reply(chatId, Reply.INVALID_LINK, language);
            return;
        }
        var link = patients.lockLink(sha256(code));
        if (link.isEmpty()) {
            reply(chatId, Reply.INVALID_LINK, language);
            return;
        }
        UUID appointmentId = link.get().appointmentId();
        BookingView booking = patients.view(appointmentId).orElseThrow();
        Optional<TelegramSubscription> existing = subscriptions.findByAppointmentId(appointmentId);
        if (existing.isPresent()) {
            TelegramSubscription subscription = existing.get();
            if (subscription.isActive() && subscription.getChatId() == chatId) {
                // Tapping the link again from the same chat: confirm again, still one subscription.
                jobs.enqueuePatient(null, "PATIENT_REPLY", chatId, messages.linked(booking), null, clock.instant());
            } else if (subscription.isActive()) {
                reply(chatId, Reply.ALREADY_CONNECTED, booking.languageCode());
            } else {
                reply(chatId, Reply.CLOSED, booking.languageCode());
            }
            return;
        }
        Instant now = clock.instant();
        if (link.get().usedAt() != null || !now.isBefore(link.get().expiresAt())) {
            reply(chatId, Reply.INVALID_LINK, booking.languageCode());
            return;
        }
        if (!LINKABLE.contains(booking.status())) {
            reply(chatId, Reply.CLOSED, booking.languageCode());
            return;
        }
        TelegramSubscription subscription = subscriptions.saveAndFlush(
                TelegramSubscription.subscribe(appointmentId, chatId, now));
        patients.markLinkUsed(appointmentId, now);
        jobs.enqueuePatient(appointmentId, "PATIENT_LINKED", chatId, messages.linked(booking),
                subscription.getId(), now);
    }

    private void stop(long chatId, String language) {
        var active = subscriptions.findAllByChatIdAndRevokedAtIsNull(chatId);
        Instant now = clock.instant();
        active.forEach(subscription -> subscription.revoke(now));
        subscriptions.saveAllAndFlush(active);
        reply(chatId, active.isEmpty() ? Reply.NOTHING_TO_STOP : Reply.STOPPED, language);
    }

    private void reply(long chatId, Reply reply, String language) {
        jobs.enqueuePatient(null, "PATIENT_REPLY", chatId, messages.reply(reply, language), null, clock.instant());
    }
}
