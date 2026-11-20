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
        assertTrue(properties.isPatientConfigurationValid());
        assertFalse(properties.patientUpdatesEnabled());
    }

    private static TelegramProperties patientReady() {
        TelegramProperties properties = enabled("-5565238118");
        TelegramProperties.Patient patient = properties.getPatient();
        patient.setEnabled(true);
        patient.setBotUsername("Sokhasaclinic_bot");
        patient.setWebhookSecret("a".repeat(32));
        patient.setLinkSecret("b".repeat(32));
        patient.setClinicName("C2G+ Sokhasa");
        patient.setClinicAddress("Sen Sok, Phnom Penh");
        patient.setClinicPhone("095 666 990");
        return properties;
    }

    @Test
    void patientUpdatesNeedEverySetting() {
        assertTrue(patientReady().isPatientConfigurationValid());
        assertTrue(patientReady().patientUpdatesEnabled());

        TelegramProperties staffOff = patientReady();
        staffOff.setEnabled(false);
        assertFalse(staffOff.isPatientConfigurationValid(), "patient updates need staff Telegram on");

        TelegramProperties shortSecret = patientReady();
        shortSecret.getPatient().setWebhookSecret("short");
        assertFalse(shortSecret.isPatientConfigurationValid());

        TelegramProperties badUsername = patientReady();
        badUsername.getPatient().setBotUsername("@Sokhasaclinic_bot");
        assertFalse(badUsername.isPatientConfigurationValid());

        TelegramProperties noPhone = patientReady();
        noPhone.getPatient().setClinicPhone(" ");
        assertFalse(noPhone.isPatientConfigurationValid());
    }
}
