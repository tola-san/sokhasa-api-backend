package com.sokhasaclinic.clinicbackend.notification.repository;

import com.sokhasaclinic.clinicbackend.notification.model.TelegramSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal persistence; callers must validate the booking-to-chat link before writing. */
public interface TelegramSubscriptionRepository extends JpaRepository<TelegramSubscription, UUID> {
    Optional<TelegramSubscription> findByAppointmentId(UUID appointmentId);

    Optional<TelegramSubscription> findByAppointmentIdAndRevokedAtIsNull(UUID appointmentId);

    List<TelegramSubscription> findAllByChatIdAndRevokedAtIsNull(long chatId);
}
