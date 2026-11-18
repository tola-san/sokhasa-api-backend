package com.sokhasaclinic.clinicbackend.notification.repository;

import com.sokhasaclinic.clinicbackend.notification.model.NotificationOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    @Query("SELECT n FROM NotificationOutbox n WHERE (n.status = 'PENDING' OR (n.status = 'FAILED' AND n.nextRetryAt <= :now)) ORDER BY n.createdAt ASC")
    List<NotificationOutbox> findPendingForProcessing(@Param("now") Instant now, Pageable pageable);
}
