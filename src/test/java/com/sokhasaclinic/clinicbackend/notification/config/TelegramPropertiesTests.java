package com.sokhasaclinic.clinicbackend.notification.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TelegramPropertiesTests {

    private static TelegramProperties enabled(String chatId) {
        TelegramProperties properties = new TelegramProperties();
        properties.setEnabled(true);
        properties.setBotToken("123456:test-token_AB");
        properties.setChatId(chatId);
        return properties;
    }

    @Test
    void acceptsGroupAndPrivateChatIds() {
        assertTrue(enabled("-1003456789012").isConfigurationValid(), "supergroup");
        assertTrue(enabled("-5565238118").isConfigurationValid(), "group");
        assertTrue(enabled("8878232559").isConfigurationValid(), "private chat with the bot");
    }

    @Test
    void rejectsMissingOrNonNumericChatIds() {
        for (String chatId : new String[] {"", "0", "-0", "-", "@reception", "abc", "12 34", "-0123",
                "123456789012345678901"}) {
            assertFalse(enabled(chatId).isConfigurationValid(), chatId);
        }
        assertFalse(enabled(null).isConfigurationValid(), "null");
    }

    @Test
    void disabledTelegramNeedsNoSettings() {
        TelegramProperties properties = new TelegramProperties();
        properties.setChatId("not-a-number");
        assertTrue(properties.isConfigurationValid());
    }
}
