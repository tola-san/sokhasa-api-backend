package com.sokhasaclinic.clinicbackend.notification.worker;

import com.sokhasaclinic.clinicbackend.notification.client.TelegramClient;
import com.sokhasaclinic.clinicbackend.notification.client.TelegramClient.DeliveryException;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private final TelegramProperties properties;
    private final NotificationJobRepository jobs;
    private final TelegramClient telegram;

    public NotificationWorker(TelegramProperties properties, NotificationJobRepository jobs, TelegramClient telegram) {
        this.properties = properties;
        this.jobs = jobs;
        this.telegram = telegram;
    }

    @Scheduled(fixedDelayString = "${clinic.telegram.poll-delay:PT5S}", initialDelayString = "PT10S")
    public void deliver() {
        if (!properties.isEnabled()) return;
        for (int i = 0; i < 10; i++) {
            var claimed = jobs.claim(properties.getRetryMaxAttempts());
            if (claimed.isEmpty()) return;
            var job = claimed.get();
            try {
                // Claim transaction has committed; no network request inside a database transaction.
                telegram.send(job.chatId(), job.message());
            } catch (DeliveryException exception) {
                boolean retry = exception.retryable() && job.attempts() < properties.getRetryMaxAttempts();
                long delay = Math.max(exception.retryAfter(), Math.min(3600L, 30L << (job.attempts() - 1)));
                if (jobs.failed(job, retry, delay, exception.getMessage())) {
                    log.warn("Telegram job {} {} ({})", job.id(), retry ? "queued for retry" : "failed",
                            exception.getMessage());
                }
                // Avoid hammering Telegram with other jobs while it is rate limited or unavailable.
                if (exception.retryable()) return;
                continue;
            }
            if (jobs.sent(job)) log.info("Telegram job {} sent", job.id());
        }
    }
}
