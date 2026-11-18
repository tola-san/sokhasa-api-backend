package com.sokhasaclinic.clinicbackend.appointment.repository;

import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, UUID> {
    List<AppointmentStatusHistory> findAllByAppointmentIdOrderByCreatedAtDesc(UUID appointmentId);

    @Query("SELECT h FROM AppointmentStatusHistory h ORDER BY h.createdAt DESC")
    List<AppointmentStatusHistory> findRecentActivities(Pageable pageable);
}
