package com.sokhasaclinic.clinicbackend.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sokhasaclinic.clinicbackend.notification.model.NotificationOutbox;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NotificationOutboxService(NotificationOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEvent(String eventType, UUID aggregateId, Object payload) {
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload for event {}", eventType, e);
            jsonPayload = "{}";
        }

        NotificationOutbox outbox = NotificationOutbox.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(jsonPayload)
                .status(NotificationOutbox.Status.PENDING)
                .attempts(0)
                .createdAt(Instant.now())
                .build();

        outboxRepository.save(outbox);
    }
}
