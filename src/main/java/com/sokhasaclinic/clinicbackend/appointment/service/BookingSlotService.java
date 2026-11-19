package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingSlot;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingSlots;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.BookingPeriod;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingPeriodRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingSettings;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingSlotRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingSlotRepository.Window;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exact-time starts on a 30-minute grid; duration and buffer come from the service. */
@Service
public class BookingSlotService {
    private final BookingSlotRepository slots;
    private final BookingPeriodRepository periods;
    private final AppointmentRepository appointments;
    private final ClinicServiceRepository services;
    private final BookingSettings settings;

    public BookingSlotService(BookingSlotRepository slots, BookingPeriodRepository periods,
            AppointmentRepository appointments, ClinicServiceRepository services, BookingSettings settings) {
        this.slots = slots;
        this.periods = periods;
        this.appointments = appointments;
        this.services = services;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public BookingSlots availability(LocalDate date, UUID serviceId) {
        LocalDate today = LocalDate.now(ClinicTime.ZONE);
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(settings.windowDays()))) {
            throw AppointmentException.invalidField("date", "Choose a date within the booking window.");
        }
        ClinicService service = services.findById(serviceId).filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                .orElseThrow(AppointmentException::serviceNotFound);
        int total = service.getDurationMinutes() + service.getBufferMinutes();
        List<Window> windows = slots.windows(serviceId, date, false);
        List<BookingSlot> result = new ArrayList<>();
        List<BookingPeriod> candidates = periods.findAllByOrderBySortOrderAscStartTimeAsc().stream()
                .filter(p -> !slots.hasLegacyPending(date, p.getCode()))
                .filter(p -> appointments.minutesUsed(date, p.getCode()) + total <= p.getCapacityMinutes()).toList();
        Instant now = Instant.now();
        for (int minute = 0; minute < 24 * 60; minute += 30) {
            LocalTime time = LocalTime.of(minute / 60, minute % 60);
            Instant start = date.atTime(time).atZone(ClinicTime.ZONE).toInstant();
            if (!start.isAfter(now) || candidates.stream().noneMatch(p -> p.contains(time, total))) continue;
            if (windows.stream().anyMatch(w -> fits(w, time, total)
                    && slots.free(w.doctorId(), start, start.plusSeconds(total * 60L), null))) {
                result.add(new BookingSlot(start.atZone(ClinicTime.ZONE).toOffsetDateTime(),
                        start.plusSeconds(service.getDurationMinutes() * 60L).atZone(ClinicTime.ZONE).toOffsetDateTime()));
            }
        }
        return new BookingSlots(date, ClinicTime.ZONE.getId(), List.copyOf(result));
    }

    public BookingPeriod period(LocalTime time, int total) {
        if (time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw AppointmentException.invalidField("preferredStartTime", "Choose a start on the 30-minute grid.");
        }
        return periods.findAllByOrderBySortOrderAscStartTimeAsc().stream().filter(p -> p.contains(time, total))
                .findFirst().orElseThrow(() -> AppointmentException.invalidField("preferredStartTime",
                        "The service and buffer must fit inside one booking period."));
    }

    /** Caller holds the period lock. Lock schedule/doctor rows until the reservation commits. */
    public Window reserve(UUID serviceId, LocalDate date, LocalTime time, int total, String period, Instant now) {
        Instant start = date.atTime(time).atZone(ClinicTime.ZONE).toInstant();
        if (!start.isAfter(now) || slots.hasLegacyPending(date, period)) throw AppointmentException.slotUnavailable();
        return slots.windows(serviceId, date, true).stream().filter(w -> fits(w, time, total))
                .filter(w -> slots.free(w.doctorId(), start, start.plusSeconds(total * 60L), null))
                .findFirst().orElseThrow(AppointmentException::slotUnavailable);
    }

    /** Exact-time confirmations require a verified window, even when reception changes the time. */
    public void validateConfirmation(UUID serviceId, UUID doctorId, LocalDate date, LocalTime time, int total) {
        if (slots.windows(serviceId, date, true).stream()
                .noneMatch(w -> w.doctorId().equals(doctorId) && fits(w, time, total))) {
            throw AppointmentException.slotUnavailable();
        }
    }

    private static boolean fits(Window window, LocalTime time, int total) {
        return !time.isBefore(window.start()) && time.getSecond() == 0 && time.getNano() == 0
                && time.toSecondOfDay() + total * 60 <= window.end().toSecondOfDay();
    }
}
