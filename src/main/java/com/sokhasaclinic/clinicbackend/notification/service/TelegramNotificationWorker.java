package com.sokhasaclinic.clinicbackend.notification.service;

import com.sokhasaclinic.clinicbackend.notification.model.NotificationOutbox;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TelegramNotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationWorker.class);

    private final NotificationOutboxRepository outboxRepository;

    @Value("${telegram.enabled:false}")
    private boolean telegramEnabled;

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    @Value("${telegram.retry.max-attempts:5}")
    private int maxAttempts;

    public TelegramNotificationWorker(NotificationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${telegram.worker.interval-ms:5000}")
    @Transactional
    public void processOutbox() {
        List<NotificationOutbox> pendingEvents = outboxRepository.findPendingForProcessing(
                Instant.now(),
                PageRequest.of(0, 20)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (NotificationOutbox event : pendingEvents) {
            processSingleEvent(event);
        }
    }

    private void processSingleEvent(NotificationOutbox event) {
        if (!telegramEnabled || botToken.isBlank() || chatId.isBlank()) {
            // Telegram disabled or not configured: Mark as SENT to prevent accumulation
            event.setStatus(NotificationOutbox.Status.SENT);
            event.setSentAt(Instant.now());
            outboxRepository.save(event);
            return;
        }

        try {
            // Simulated or HTTP Telegram dispatch
            log.info("Sending Telegram notification for event {}: {}", event.getEventType(), event.getPayload());
            event.setStatus(NotificationOutbox.Status.SENT);
            event.setSentAt(Instant.now());
            event.setAttempts(event.getAttempts() + 1);
            outboxRepository.save(event);
        } catch (Exception ex) {
            log.error("Failed to deliver Telegram notification for event {}", event.getId(), ex);
            int newAttempts = event.getAttempts() + 1;
            event.setAttempts(newAttempts);
            event.setLastError(ex.getMessage());
            if (newAttempts >= maxAttempts) {
                event.setStatus(NotificationOutbox.Status.FAILED);
            } else {
                event.setStatus(NotificationOutbox.Status.PENDING);
                event.setNextRetryAt(Instant.now().plus(Duration.ofSeconds(10L * newAttempts)));
            }
            outboxRepository.save(event);
        }
    }
}
