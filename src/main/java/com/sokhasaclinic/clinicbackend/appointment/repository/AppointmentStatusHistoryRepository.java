package com.sokhasaclinic.clinicbackend.appointment.repository;

import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Status history rows, written in the same transaction as each change. */
public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {

    /** Oldest first; the identity id keeps changes in the same millisecond in order. */
    List<AppointmentStatusHistory> findByAppointmentIdOrderByIdAsc(UUID appointmentId);
}
