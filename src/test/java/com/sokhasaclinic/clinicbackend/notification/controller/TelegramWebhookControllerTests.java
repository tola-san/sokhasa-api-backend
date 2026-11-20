package com.sokhasaclinic.clinicbackend.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TelegramWebhookControllerTests {

    static final String SECRET = "test-webhook-secret-0123456789abcdef";
    static final String UPDATE = """
            {"update_id": 42, "message": {"message_id": 7, "date": 1795147609,
              "chat": {"id": 8878232559, "type": "private", "first_name": "Tola"},
              "from": {"id": 8878232559, "is_bot": false, "language_code": "km"},
              "text": "/start abc"}}
            """;

    private final PatientTelegramService patients = mock(PatientTelegramService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties();
        properties.getPatient().setWebhookSecret(SECRET);
        mvc = MockMvcBuilders.standaloneSetup(new TelegramWebhookController(properties, patients)).build();
    }

    @Test
    void authenticatedUpdateIsHandledAndAcknowledged() throws Exception {
        when(patients.enabled()).thenReturn(true);
        mvc.perform(post("/api/v1/telegram/webhook").header(TelegramWebhookController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk());
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(patients).handle(update.capture());
        org.junit.jupiter.api.Assertions.assertEquals(8878232559L, update.getValue().message().chat().id());
        org.junit.jupiter.api.Assertions.assertEquals("private", update.getValue().message().chat().type());
        org.junit.jupiter.api.Assertions.assertEquals("km", update.getValue().message().from().languageCode());
        org.junit.jupiter.api.Assertions.assertEquals("/start abc", update.getValue().message().text());
    }

    @Test
    void missingOrWrongSecretIsRejected() throws Exception {
        when(patients.enabled()).thenReturn(true);
        mvc.perform(post("/api/v1/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/telegram/webhook").header(TelegramWebhookController.SECRET_HEADER, SECRET + "x")
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isUnauthorized());
        verify(patients, never()).handle(any());
    }

    @Test
    void disabledPatientUpdatesReturn404() throws Exception {
        when(patients.enabled()).thenReturn(false);
        mvc.perform(post("/api/v1/telegram/webhook").header(TelegramWebhookController.SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isNotFound());
        verify(patients, never()).handle(any());
    }
}
