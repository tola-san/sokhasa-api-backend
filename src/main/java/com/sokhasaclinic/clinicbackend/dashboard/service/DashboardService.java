package com.sokhasaclinic.clinicbackend.dashboard.service;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentStatusHistoryDto;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentSummaryResponse;
import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentStatusHistoryRepository;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardOverviewResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository historyRepository;

    public DashboardService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository historyRepository) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview(LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();

        long total = appointmentRepository.count();
        long pending = appointmentRepository.countByStatus(Appointment.Status.PENDING);
        long confirmed = appointmentRepository.countByStatus(Appointment.Status.CONFIRMED);
        long rejected = appointmentRepository.countByStatus(Appointment.Status.REJECTED);
        long today = appointmentRepository.countByAppointmentDate(queryDate);

        return new DashboardOverviewResponse(total, pending, confirmed, rejected, today);
    }

    @Transactional(readOnly = true)
    public List<AppointmentSummaryResponse> getUpcomingAppointments(LocalDate date, int limit) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        int safeLimit = limit > 0 && limit <= 100 ? limit : 10;

        return appointmentRepository.findUpcomingConfirmed(queryDate, PageRequest.of(0, safeLimit))
                .stream()
                .map(AppointmentSummaryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentStatusHistoryDto> getRecentActivities(int limit) {
        int safeLimit = limit > 0 && limit <= 100 ? limit : 20;

        return historyRepository.findRecentActivities(PageRequest.of(0, safeLimit))
                .stream()
                .map(AppointmentStatusHistoryDto::fromEntity)
                .toList();
    }
}
