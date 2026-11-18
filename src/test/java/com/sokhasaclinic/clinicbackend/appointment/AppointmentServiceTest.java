package com.sokhasaclinic.clinicbackend.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.appointment.dto.ConfirmAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.CreateAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.PublicAppointmentCreatedResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.RejectAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentStatusHistoryRepository;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.common.exception.ConflictException;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.notification.service.NotificationOutboxService;
import com.sokhasaclinic.clinicbackend.patient.dto.PatientRequest;
import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import com.sokhasaclinic.clinicbackend.patient.service.PatientService;
import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import com.sokhasaclinic.clinicbackend.service.repository.ClinicServiceRepository;
import java.time.Instant;
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
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentStatusHistoryRepository statusHistoryRepository;
    @Mock
    private PatientService patientService;
    @Mock
    private ClinicServiceRepository clinicServiceRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private NotificationOutboxService outboxService;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository,
                statusHistoryRepository,
                patientService,
                clinicServiceRepository,
                doctorRepository,
                outboxService
        );
    }

    @Test
    void createPublicBooking_Success() {
        UUID serviceId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime startTime = LocalTime.of(9, 0);

        ClinicService service = ClinicService.builder()
                .id(serviceId)
                .durationMinutes(30)
                .status(ClinicService.Status.ACTIVE)
                .build();

        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .fullName("John Doe")
                .phone("+85512345678")
                .build();

        when(clinicServiceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(patientService.findOrCreatePatient(any(PatientRequest.class))).thenReturn(patient);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                new PatientRequest("John Doe", "+85512345678", "john@example.com"),
                serviceId,
                null,
                date,
                startTime,
                "Checkup"
        );

        PublicAppointmentCreatedResponse response = appointmentService.createPublicBooking(request);

        assertNotNull(response);
        assertEquals(Appointment.Status.PENDING, response.status());
        assertEquals(startTime, response.startTime());
        assertEquals(LocalTime.of(9, 30), response.endTime());
        verify(outboxService).recordEvent(eq("APPOINTMENT_CREATED"), any(UUID.class), any());
    }

    @Test
    void confirmAppointment_OverlappingConflict_ThrowsException() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();

        Doctor doctor = Doctor.builder()
                .id(doctorId)
                .name("Dr. Smith")
                .status(Doctor.Status.ACTIVE)
                .build();

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .status(Appointment.Status.PENDING)
                .appointmentDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findOverlappingDoctorAppointments(eq(doctorId), any(), any(), any(), eq(appointmentId)))
                .thenReturn(List.of(new Appointment()));

        ConfirmAppointmentRequest request = new ConfirmAppointmentRequest(doctorId, "Confirming");

        assertThrows(ConflictException.class, () ->
                appointmentService.confirmAppointment(appointmentId, request, "user-1", "admin")
        );
    }

    @Test
    void rejectAppointment_Success() {
        UUID appointmentId = UUID.randomUUID();

        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .status(Appointment.Status.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        RejectAppointmentRequest request = new RejectAppointmentRequest("Slot unavailable");
        appointmentService.rejectAppointment(appointmentId, request, "user-1", "admin");

        assertEquals(Appointment.Status.REJECTED, appointment.getStatus());
        assertEquals("Slot unavailable", appointment.getRejectionReason());
        verify(outboxService).recordEvent(eq("APPOINTMENT_REJECTED"), eq(appointmentId), any());
    }
}
