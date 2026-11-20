package com.sokhasaclinic.clinicbackend.notification.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramClientTests {
    private MockRestServiceServer server;
    private TelegramClient client;

    @BeforeEach
    void setup() {
        TelegramProperties properties = new TelegramProperties();
        properties.setBotToken("123:test-token");
        var builder = RestClient.builder().baseUrl("https://api.telegram.org");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelegramClient(properties, builder.build());
    }

    @Test
    void sendsPlainSummaryWithoutButtonsOrFormatting() {
        server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"chat_id":"-100123", "text":"New booking request"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":12}}", MediaType.APPLICATION_JSON));
        client.send("-100123", "New booking request");
        server.verify();
    }

    @Test
    void sendsBlockQuoteSummariesAsHtml() {
        String html = "<blockquote><b>New booking request</b></blockquote>";
        server.expect(requestTo("https://api.telegram.org/bot123:test-token/sendMessage"))
                .andExpect(content().json("""
                        {"chat_id":"8878232559", "text":"<blockquote><b>New booking request</b></blockquote>",
                         "parse_mode":"HTML"}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":13}}", MediaType.APPLICATION_JSON));
        client.send("8878232559", html);
        server.verify();
    }

    @Test
    void rateLimitPreservesRetryAfterWithoutLeakingResponse() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"ok":false,"error_code":429,"description":"private data", "parameters":{"retry_after":123}}
                        """));
        var error = assertThrows(TelegramClient.DeliveryException.class, () -> client.send("-100123", "patient"));
        assertTrue(error.retryable());
        assertEquals(123, error.retryAfter());
        assertEquals("TELEGRAM_429", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void forbiddenIsPermanent() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        var error = assertThrows(TelegramClient.DeliveryException.class, () -> client.send("-100123", "patient"));
        assertFalse(error.retryable());
    }

    @Test
    void apiFailureInSuccessfulHttpResponseIsStillFailure() {
        server.expect(anything()).andRespond(withSuccess("{\"ok\":false,\"error_code\":500}", MediaType.APPLICATION_JSON));
        assertTrue(assertThrows(TelegramClient.DeliveryException.class,
                () -> client.send("-100123", "patient")).retryable());
    }

    @Test
    void malformedResponseIsRetryable() {
        server.expect(anything()).andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));
        assertTrue(assertThrows(TelegramClient.DeliveryException.class,
                () -> client.send("-100123", "patient")).retryable());
    }
}
