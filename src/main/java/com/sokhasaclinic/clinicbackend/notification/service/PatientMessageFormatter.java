package com.sokhasaclinic.clinicbackend.notification.service;

import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.PatientTelegramRepository.BookingView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Patient Telegram messages (PT-05, PT-06), as Telegram HTML in one block quote, in the booking's
 * language. Every value is HTML-escaped. Never included: notes, email, or the staff rejection reason.
 *
 * <p>Khmer and Chinese wording is a draft for clinic review before patient updates are enabled.
 */
@Component
public class PatientMessageFormatter {

    /** Short replies to chat commands that are not about a linked booking. */
    public enum Reply { HELP, INVALID_LINK, ALREADY_CONNECTED, CLOSED, STOPPED, NOTHING_TO_STOP }

    private record Text(
            String reference, String service, String requested, String status, String date, String time,
            String doctor, String clinic, String phone,
            String linkedTitle, String linkedBody, String waiting, String stopHint,
            String confirmedTitle, String arriveEarly,
            String rejectedTitle, String rejectedBody, String rebook,
            String reminderTitle, String reminderChange,
            Map<Reply, String> replies) { }

    private static final Map<String, Text> TEXT = Map.of(
            "en", new Text("Reference", "Service", "Requested", "Status", "Date", "Time", "Doctor", "Clinic", "Phone",
                    "You're connected", "We'll send updates for your booking here.",
                    "Waiting for reception to confirm", "Send /stop any time to turn off updates.",
                    "Your appointment is confirmed", "Please arrive 10 minutes early.",
                    "We couldn't confirm your request", "The requested time is not available.",
                    "Please book another time on our website or call us on %s.",
                    "Reminder: your appointment is tomorrow", "Need to change it? Call %s.",
                    Map.of(Reply.HELP, "Hello! To get updates, open the Telegram link on your booking confirmation.",
                            Reply.INVALID_LINK, "This link is not valid or has expired. Please call us on %s.",
                            Reply.ALREADY_CONNECTED, "This booking is already connected to another Telegram account.",
                            Reply.CLOSED, "This booking can no longer receive updates.",
                            Reply.STOPPED, "Updates are turned off. You won't get more messages.",
                            Reply.NOTHING_TO_STOP, "You have no active updates.")),
            "km", new Text("លេខយោង", "សេវា", "ពេលដែលបានស្នើ", "ស្ថានភាព", "កាលបរិច្ឆេទ", "ម៉ោង", "វេជ្ជបណ្ឌិត",
                    "គ្លីនិក", "ទូរស័ព្ទ",
                    "អ្នកបានភ្ជាប់រួចរាល់", "យើងនឹងផ្ញើព័ត៌មានថ្មីៗអំពីការកក់របស់អ្នកនៅទីនេះ។",
                    "កំពុងរង់ចាំការបញ្ជាក់ពីផ្នែកទទួលភ្ញៀវ", "ផ្ញើ /stop នៅពេលណាក៏បាន ដើម្បីបិទការជូនដំណឹង។",
                    "ការណាត់ជួបរបស់អ្នកត្រូវបានបញ្ជាក់", "សូមមកដល់មុនម៉ោង ១០ នាទី។",
                    "យើងមិនអាចបញ្ជាក់សំណើរបស់អ្នកបានទេ", "ម៉ោងដែលបានស្នើមិនទំនេរទេ។",
                    "សូមកក់ម៉ោងផ្សេងទៀតនៅលើគេហទំព័ររបស់យើង ឬទូរស័ព្ទមកយើងតាមលេខ %s។",
                    "រំលឹក៖ ការណាត់ជួបរបស់អ្នកគឺនៅថ្ងៃស្អែក", "ចង់ផ្លាស់ប្តូរ? សូមទូរស័ព្ទមក %s។",
                    Map.of(Reply.HELP, "សួស្តី! ដើម្បីទទួលព័ត៌មានថ្មីៗ សូមបើកតំណ Telegram នៅលើទំព័របញ្ជាក់ការកក់របស់អ្នក។",
                            Reply.INVALID_LINK, "តំណនេះមិនត្រឹមត្រូវ ឬផុតកំណត់ហើយ។ សូមទូរស័ព្ទមកយើងតាមលេខ %s។",
                            Reply.ALREADY_CONNECTED, "ការកក់នេះត្រូវបានភ្ជាប់ជាមួយគណនី Telegram ផ្សេងរួចហើយ។",
                            Reply.CLOSED, "ការកក់នេះមិនអាចទទួលព័ត៌មានថ្មីៗបានទៀតទេ។",
                            Reply.STOPPED, "ការជូនដំណឹងត្រូវបានបិទ។ អ្នកនឹងមិនទទួលសារទៀតទេ។",
                            Reply.NOTHING_TO_STOP, "អ្នកមិនមានការជូនដំណឹងដែលកំពុងបើកទេ។")),
            "zh", new Text("预约编号", "服务", "申请时间", "状态", "日期", "时间", "医生", "诊所", "电话",
                    "已连接", "我们会在这里发送您预约的最新消息。",
                    "等待前台确认", "随时发送 /stop 即可关闭通知。",
                    "您的预约已确认", "请提前 10 分钟到达。",
                    "我们无法确认您的预约申请", "您申请的时间已不可用。",
                    "请在我们的网站重新预约，或致电 %s。",
                    "提醒：您的预约在明天", "需要更改？请致电 %s。",
                    Map.of(Reply.HELP, "您好！如需接收通知，请打开预约确认页面上的 Telegram 链接。",
                            Reply.INVALID_LINK, "此链接无效或已过期。请致电 %s。",
                            Reply.ALREADY_CONNECTED, "此预约已连接到另一个 Telegram 账号。",
                            Reply.CLOSED, "此预约已无法接收通知。",
                            Reply.STOPPED, "通知已关闭，您将不再收到消息。",
                            Reply.NOTHING_TO_STOP, "您没有已开启的通知。")));

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TelegramProperties properties;

    public PatientMessageFormatter(TelegramProperties properties) {
        this.properties = properties;
    }

    public String linked(BookingView booking) {
        Text t = text(booking.languageCode());
        return quote(bold(t.linkedTitle) + "\n\n" + esc(t.linkedBody) + "\n\n"
                + field(t.reference, code(booking.reference())) + "\n"
                + field(t.service, esc(booking.serviceName())) + "\n"
                + field(t.requested, esc(requested(booking))) + "\n\n"
                + field(t.status, esc(t.waiting)))
                + "\n\n" + esc(t.stopHint);
    }

    public String confirmed(BookingView booking) {
        Text t = text(booking.languageCode());
        LocalDate day = booking.startAt().atZone(ClinicTime.ZONE).toLocalDate();
        String time = TIME.format(booking.startAt().atZone(ClinicTime.ZONE))
                + (booking.endAt() == null ? "" : " – " + TIME.format(booking.endAt().atZone(ClinicTime.ZONE)));
        StringBuilder body = new StringBuilder(bold(t.confirmedTitle)).append("\n\n")
                .append(field(t.reference, code(booking.reference()))).append("\n\n")
                .append(field(t.service, esc(booking.serviceName()))).append('\n')
                .append(field(t.date, esc(date(day, booking.languageCode())))).append('\n')
                .append(field(t.time, esc(time)));
        if (booking.doctorName() != null) body.append('\n').append(field(t.doctor, esc(booking.doctorName())));
        body.append("\n\n").append(field(t.clinic, esc(clinic().getClinicName() + ", " + clinic().getClinicAddress())))
                .append('\n').append(field(t.phone, code(clinic().getClinicPhone())));
        return quote(body.toString()) + "\n\n" + esc(t.arriveEarly);
    }

    public String rejected(BookingView booking) {
        Text t = text(booking.languageCode());
        return quote(bold(t.rejectedTitle) + "\n\n"
                + field(t.reference, code(booking.reference())) + "\n"
                + field(t.requested, esc(requested(booking))) + "\n\n"
                + esc(t.rejectedBody) + "\n\n"
                + esc(t.rebook.formatted(clinic().getClinicPhone())));
    }

    public String reminder(BookingView booking) {
        Text t = text(booking.languageCode());
        LocalDate day = booking.startAt().atZone(ClinicTime.ZONE).toLocalDate();
        StringBuilder body = new StringBuilder(bold(t.reminderTitle)).append("\n\n")
                .append(field(t.date, esc(date(day, booking.languageCode())))).append('\n')
                .append(field(t.time, esc(TIME.format(booking.startAt().atZone(ClinicTime.ZONE))))).append('\n')
                .append(field(t.service, esc(booking.serviceName())));
        if (booking.doctorName() != null) body.append('\n').append(field(t.doctor, esc(booking.doctorName())));
        body.append("\n\n").append(field(t.reference, code(booking.reference())));
        return quote(body.toString()) + "\n\n" + esc(t.reminderChange.formatted(clinic().getClinicPhone()));
    }

    public String reply(Reply reply, String languageCode) {
        String template = text(languageCode).replies.get(reply);
        return quote(esc(template.contains("%s") ? template.formatted(clinic().getClinicPhone()) : template));
    }

    /** km, en or zh; anything else gets English. */
    static String language(String code) {
        return code != null && TEXT.containsKey(code) ? code : "en";
    }

    private static Text text(String languageCode) {
        return TEXT.get(language(languageCode));
    }

    private TelegramProperties.Patient clinic() {
        return properties.getPatient();
    }

    private static String requested(BookingView booking) {
        String when = booking.preferredStartTime() == null
                ? booking.preferredPeriod() : TIME.format(booking.preferredStartTime());
        return date(booking.preferredDate(), booking.languageCode()) + ", " + when;
    }

    private static String date(LocalDate day, String languageCode) {
        String language = language(languageCode);
        DateTimeFormatter format = "en".equals(language)
                ? DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH)
                : DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag(language));
        return format.format(day);
    }

    private static String quote(String html) {
        return AppointmentMessageFormatter.HTML_PREFIX + html + "</blockquote>";
    }

    private static String field(String label, String value) {
        return "<b>" + esc(label) + ":</b>  " + value;
    }

    private static String bold(String value) {
        return "<b>" + esc(value) + "</b>";
    }

    private static String code(String value) {
        return "<code>" + esc(value) + "</code>";
    }

    private static String esc(String value) {
        return AppointmentMessageFormatter.text(value);
    }
}
