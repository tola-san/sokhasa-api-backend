package com.sokhasaclinic.clinicbackend.notification.controller;

import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService.Update;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telegram calls this for every bot update (PT-03), after it is registered with setWebhook. Only a
 * request carrying the configured secret header is processed. Authenticated updates always get 200,
 * so Telegram does not redeliver ones the bot ignores; replies go through the notification outbox.
 */
@RestController
public class TelegramWebhookController {

    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramProperties properties;
    private final PatientTelegramService patients;

    public TelegramWebhookController(TelegramProperties properties, PatientTelegramService patients) {
        this.properties = properties;
        this.patients = patients;
    }

    @PostMapping("/api/v1/telegram/webhook")
    public ResponseEntity<?> receive(@RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody(required = false) Update update) {
        if (!patients.enabled()) {
            return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Patient Telegram updates are disabled.");
        }
        if (!matches(secret, properties.getPatient().getWebhookSecret())) {
            return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid webhook secret.");
        }
        patients.handle(update);
        return ResponseEntity.ok().build();
    }

    /** Constant-time comparison, so response timing does not reveal the secret. */
    private static boolean matches(String given, String expected) {
        if (given == null || expected == null || expected.isEmpty()) return false;
        return MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    /** The API's usual problem-details shape (type, title, status, detail, instance, timestamp, errors). */
    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String type, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", "/api/v1/telegram/webhook");
        body.put("timestamp", Instant.now().toString());
        body.put("errors", List.of());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
