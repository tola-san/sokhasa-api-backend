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
}
