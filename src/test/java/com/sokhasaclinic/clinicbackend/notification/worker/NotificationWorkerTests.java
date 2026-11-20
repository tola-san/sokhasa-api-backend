package com.sokhasaclinic.clinicbackend.notification.worker;

import static org.mockito.Mockito.*;

import com.sokhasaclinic.clinicbackend.notification.client.TelegramClient;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository.Job;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationWorkerTests {
    private final TelegramProperties properties = new TelegramProperties();
    private final NotificationJobRepository jobs = mock(NotificationJobRepository.class);
    private final TelegramClient client = mock(TelegramClient.class);
    private final NotificationWorker worker = new NotificationWorker(properties, jobs, client);

    @Test
    void disabledDoesNotClaimOrSend() {
        worker.deliver();
        verifyNoInteractions(jobs, client);
    }

    @Test
    void patientMessageAfterStopIsCancelledNotSent() {
        properties.setEnabled(true);
        UUID subscription = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), "8878232559", "<blockquote>confirmed</blockquote>", 1,
                UUID.randomUUID(), subscription);
        when(jobs.claim(5)).thenReturn(Optional.of(job), Optional.empty());
        when(jobs.subscriptionActive(subscription)).thenReturn(false);
        worker.deliver();
        verify(jobs).cancelled(job);
        verify(client, never()).send(any(), any());
        verify(jobs, never()).sent(any());
    }

    @Test
    void patientMessageWithActiveSubscriptionIsSent() {
        properties.setEnabled(true);
        UUID subscription = UUID.randomUUID();
        Job job = new Job(UUID.randomUUID(), "8878232559", "<blockquote>confirmed</blockquote>", 1,
                UUID.randomUUID(), subscription);
        when(jobs.claim(5)).thenReturn(Optional.of(job), Optional.empty());
        when(jobs.subscriptionActive(subscription)).thenReturn(true);
        worker.deliver();
        verify(client).send(job.chatId(), job.message());
        verify(jobs).sent(job);
    }

    @Test
    void rateLimitedDeliveryRetriesAfterTelegramDelay() {
        properties.setEnabled(true);
        Job job = new Job(UUID.randomUUID(), "-123", "summary", 1, UUID.randomUUID());
        when(jobs.claim(5)).thenReturn(Optional.of(job));
        doThrow(new TelegramClient.DeliveryException("TELEGRAM_429", true, 180))
                .when(client).send(job.chatId(), job.message());
        worker.deliver();
        verify(jobs).failed(job, true, 180, "TELEGRAM_429");
        verify(jobs, never()).sent(any());
        verify(jobs, times(1)).claim(5);
    }

    @Test
    void finalAttemptIsFailedWithoutRetry() {
        properties.setEnabled(true);
        Job job = new Job(UUID.randomUUID(), "-123", "summary", 5, UUID.randomUUID());
        when(jobs.claim(5)).thenReturn(Optional.of(job));
        doThrow(new TelegramClient.DeliveryException("TRANSPORT_ERROR", true, 0))
                .when(client).send(job.chatId(), job.message());
        worker.deliver();
        verify(jobs).failed(job, false, 480, "TRANSPORT_ERROR");
    }
}
