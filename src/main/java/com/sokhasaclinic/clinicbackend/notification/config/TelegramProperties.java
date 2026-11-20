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

    @AssertTrue(message = "Enabled Telegram requires a bot token and a numeric private staff group chat ID")
    public boolean isConfigurationValid() {
        return !enabled || (botToken != null && botToken.matches("[0-9]+:[A-Za-z0-9_-]+")
                && chatId != null && chatId.matches("-[0-9]{1,20}"));
    }
}
