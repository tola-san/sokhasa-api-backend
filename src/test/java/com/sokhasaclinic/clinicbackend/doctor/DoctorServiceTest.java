package com.sokhasaclinic.clinicbackend.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.doctor.dto.AvailabilityResponse;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorSchedule;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorScheduleRepository;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import com.sokhasaclinic.clinicbackend.service.repository.ClinicServiceRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private DoctorScheduleRepository scheduleRepository;
    @Mock
    private ClinicServiceRepository clinicServiceRepository;
    @Mock
    private AppointmentRepository appointmentRepository;

    private DoctorService doctorService;

    @BeforeEach
    void setUp() {
        doctorService = new DoctorService(
                doctorRepository,
                scheduleRepository,
                clinicServiceRepository,
                appointmentRepository
        );
    }

    @Test
    void checkAvailability_ReturnsSlotsCorrectly() {
        UUID serviceId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 23); // Monday
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        ClinicService service = ClinicService.builder()
                .id(serviceId)
                .durationMinutes(30)
                .build();

        DoctorSchedule schedule = DoctorSchedule.builder()
                .doctorId(doctorId)
                .dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();

        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(scheduleRepository.findActiveSchedules(doctorId, dayOfWeek, date)).thenReturn(List.of(schedule));

        // First slot (08:00 - 08:30) free, second slot (08:30 - 09:00) occupied
        when(appointmentRepository.findOverlappingDoctorAppointments(
                eq(doctorId), eq(date), eq(LocalTime.of(8, 0)), eq(LocalTime.of(8, 30)), any()
        )).thenReturn(List.of());

        when(appointmentRepository.findOverlappingDoctorAppointments(
                eq(doctorId), eq(date), eq(LocalTime.of(8, 30)), eq(LocalTime.of(9, 0)), any()
        )).thenReturn(List.of(new Appointment()));

        AvailabilityResponse response = doctorService.checkAvailability(serviceId, doctorId, date);

        assertNotNull(response);
        assertEquals(2, response.slots().size());
        assertTrue(response.slots().get(0).available());
        assertFalse(response.slots().get(1).available());
    }
}
