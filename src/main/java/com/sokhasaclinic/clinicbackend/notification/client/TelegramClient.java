package com.sokhasaclinic.clinicbackend.notification.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.sokhasaclinic.clinicbackend.notification.service.AppointmentMessageFormatter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TelegramClient {
    public record Parameters(@JsonProperty("retry_after") Long retryAfter) {}
    public record Result(@JsonProperty("message_id") Long messageId) {}
    public record Response(boolean ok, Result result, @JsonProperty("error_code") Integer errorCode,
            Parameters parameters) {}

    /** Contains only a safe error code; never retains the request URL, token or patient payload. */
    public static class DeliveryException extends RuntimeException {
        private final boolean retryable;
        private final long retryAfter;
        public DeliveryException(String code, boolean retryable, long retryAfter) {
            super(code);
            this.retryable = retryable;
            this.retryAfter = Math.max(0, retryAfter);
        }
        public boolean retryable() { return retryable; }
        public long retryAfter() { return retryAfter; }
    }

    private final RestClient client;
    private final TelegramProperties properties;

    @Autowired
    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().requestFactory(factory).baseUrl("https://api.telegram.org").build();
    }

    // Package-visible transport injection for tests; production origin is fixed.
    TelegramClient(TelegramProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    public void send(String chatId, String message) {
        // New summaries are escaped HTML; jobs queued before that change are plain text and stay so.
        Map<String, String> body = message.startsWith(AppointmentMessageFormatter.HTML_PREFIX)
                ? Map.of("chat_id", chatId, "text", message, "parse_mode", "HTML")
                : Map.of("chat_id", chatId, "text", message);
        try {
            Response response = client.post().uri("/bot" + properties.getBotToken() + "/sendMessage")
                    .body(body)
                    .retrieve().body(Response.class);
            if (response == null) throw new DeliveryException("EMPTY_RESPONSE", true, 0);
            if (!response.ok()) throw failure(response.errorCode(), response.parameters());
            if (response.result() == null || response.result().messageId() == null)
                throw new DeliveryException("INVALID_RESPONSE", true, 0);
        } catch (RestClientResponseException exception) {
            Parameters parameters = null;
            try {
                Response response = exception.getResponseBodyAs(Response.class);
                if (response != null) parameters = response.parameters();
            } catch (RuntimeException ignored) { /* Never log Telegram's raw response. */ }
            throw failure(exception.getStatusCode().value(), parameters);
        } catch (RestClientException exception) {
            throw new DeliveryException("TRANSPORT_ERROR", true, 0);
        }
    }

    private static DeliveryException failure(Integer status, Parameters parameters) {
        int code = status == null ? 0 : status;
        long retryAfter = parameters == null || parameters.retryAfter() == null ? 0 : parameters.retryAfter();
        return new DeliveryException("TELEGRAM_" + code, code == 0 || code == 429 || code >= 500, retryAfter);
    }
}
