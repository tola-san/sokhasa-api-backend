package com.sokhasaclinic.clinicbackend.notification.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "clinic.telegram")
@Validated
@Getter
@Setter
public class TelegramProperties {
    private boolean enabled;
    private String botToken = "";
    private String chatId = "";
    @Min(1) @Max(20)
    private int retryMaxAttempts = 5;

    /**
     * A numeric Telegram chat ID: negative for a group (the private reception group, recommended),
     * or positive for one person's private chat with the bot (useful for testing). That person must
     * have sent the bot /start first. Usernames such as {@code @name} are not accepted.
     */
    @AssertTrue(message = "Enabled Telegram requires a bot token and a numeric chat ID "
            + "(negative for the staff group, positive for a private chat with the bot)")
    public boolean isConfigurationValid() {
        return !enabled || (botToken != null && botToken.matches("[0-9]+:[A-Za-z0-9_-]+")
                && chatId != null && chatId.matches("-?[1-9][0-9]{0,19}"));
    }

    /** Patient Telegram updates (PT-01 to PT-12). Off by default (PT-12). */
    private Patient patient = new Patient();

    @Getter
    @Setter
    public static class Patient {
        private boolean enabled;
        /** Bot username without "@", used to build the t.me link (PT-02). */
        private String botUsername = "";
        /** Value Telegram sends in X-Telegram-Bot-Api-Secret-Token (PT-03). */
        private String webhookSecret = "";
        /** HMAC key for link codes (PT-02). */
        private String linkSecret = "";
        /** When the day-before reminder runs, in Asia/Phnom_Penh (PT-08). */
        private String reminderCron = "0 0 17 * * *";
        private String clinicName = "";
        private String clinicAddress = "";
        private String clinicPhone = "";
    }

    @AssertTrue(message = "Patient Telegram updates require staff Telegram to be enabled, a bot username, "
            + "webhook and link secrets of at least 32 characters, and the clinic name, address and phone")
    public boolean isPatientConfigurationValid() {
        if (patient == null || !patient.enabled) return true;
        return enabled
                && patient.botUsername != null && patient.botUsername.matches("[A-Za-z0-9_]{5,32}")
                && patient.webhookSecret != null && patient.webhookSecret.matches("[A-Za-z0-9_-]{32,256}")
                && patient.linkSecret != null && patient.linkSecret.length() >= 32
                && notBlank(patient.clinicName) && notBlank(patient.clinicAddress) && notBlank(patient.clinicPhone);
    }

    /** True when patient updates are on (and therefore fully configured). */
    public boolean patientUpdatesEnabled() {
        return enabled && patient != null && patient.enabled;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
