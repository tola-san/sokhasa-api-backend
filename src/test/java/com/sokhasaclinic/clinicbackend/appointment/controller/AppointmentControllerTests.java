package com.sokhasaclinic.clinicbackend.appointment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.AppointmentDetail;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingSlot;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingSlots;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.PageInfo;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.PageResponse;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService.ListFilter;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService.VersionedDetail;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService.BookingResult;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingRateLimiter;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingSlotService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppointmentControllerTests {

    private static final UUID ID = UUID.fromString("b2d4a1c3-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final UUID SERVICE = UUID.fromString("3f1c2a7e-0b5d-4b8e-9a51-3c0d2e7f9a10");
    private static final String KEY = "6f1c2a7e-0b5d-4b8e-9a51-3c0d2e7f9a10";
    private static final String BOOKING = """
            {"serviceId":"3f1c2a7e-0b5d-4b8e-9a51-3c0d2e7f9a10","preferredDate":"2026-11-20",
             "preferredPeriod":"MORNING","language":"km",
             "patient":{"fullName":"Sok Dara","phone":"012 345 678","email":null},"notes":"First visit"}
            """;

    private AppointmentService appointments;
    private AppointmentQueryService queries;
    private BookingRateLimiter rateLimiter;
    private BookingSlotService slots;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentService.class);
        queries = mock(AppointmentQueryService.class);
        rateLimiter = mock(BookingRateLimiter.class);
        slots = mock(BookingSlotService.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new PublicAppointmentController(appointments, queries, rateLimiter, slots),
                        new AdminAppointmentController(appointments, queries))
                .setControllerAdvice(new AppointmentExceptionHandler())
                .build();
    }

    @Test
    void bookingReturns201WithTheReference() throws Exception {
        when(appointments.request(any(BookingCommand.class), eq(UUID.fromString(KEY))))
                .thenReturn(new BookingResult(snapshot(), false));

        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.reference").value("SK-20261120-0007"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.preferredPeriod").value("MORNING"))
                .andExpect(jsonPath("$.data.patientName").doesNotExist());

        ArgumentCaptor<BookingCommand> command = ArgumentCaptor.forClass(BookingCommand.class);
        verify(appointments).request(command.capture(), eq(UUID.fromString(KEY)));
        verify(rateLimiter).acquire("203.0.113.7");
        org.junit.jupiter.api.Assertions.assertEquals("km", command.getValue().languageCode());
        org.junit.jupiter.api.Assertions.assertEquals("012 345 678", command.getValue().phone());
    }

    @Test
    void exactTimeBodyIsAcceptedAndInvalidTimeFormatsAreRejected() throws Exception {
        when(appointments.request(any(BookingCommand.class), any())).thenReturn(new BookingResult(snapshot(), false));
        String exact = BOOKING.replace("\"preferredPeriod\":\"MORNING\"", "\"preferredStartTime\":\"14:30\"");
        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(exact))
                .andExpect(status().isCreated());
        ArgumentCaptor<BookingCommand> captured = ArgumentCaptor.forClass(BookingCommand.class);
        verify(appointments).request(captured.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalTime.of(14, 30), captured.getValue().preferredStartTime());
        org.junit.jupiter.api.Assertions.assertNull(captured.getValue().preferredPeriod());
        for (String invalid : List.of("14:15", "25:00", "14:30:00", "2:30 pm")) {
            mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(exact.replace("14:30", invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void preferredDoctorSlugIsPassedOnAndMalformedOrUnknownSlugsFail() throws Exception {
        when(appointments.request(any(BookingCommand.class), any())).thenReturn(new BookingResult(snapshot(), false));
        String preferred = BOOKING.replace("\"preferredPeriod\":\"MORNING\"",
                "\"preferredPeriod\":\"MORNING\",\"preferredDoctorSlug\":\"dr-sok\"");
        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(preferred))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.preferredDoctorSlug").doesNotExist());
        ArgumentCaptor<BookingCommand> captured = ArgumentCaptor.forClass(BookingCommand.class);
        verify(appointments).request(captured.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals("dr-sok", captured.getValue().preferredDoctorSlug());

        for (String invalid : List.of("Dr Sok", "dr--sok", "-dr", "dr-".repeat(30))) {
            mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(preferred.replace("dr-sok", invalid)))
                    .andExpect(status().isBadRequest());
        }

        when(appointments.request(any(BookingCommand.class), any()))
                .thenThrow(AppointmentException.doctorNotFound());
        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(preferred))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("DOCTOR_NOT_FOUND"));
    }

    @Test
    void slotAvailabilityIsPublicDataWithClinicOffsetAndNoCache() throws Exception {
        LocalDate date = LocalDate.parse("2026-11-20");
        when(slots.availability(date, SERVICE)).thenReturn(
                new BookingSlots(date, "Asia/Phnom_Penh",
                        List.of(new BookingSlot(
                                OffsetDateTime.parse("2026-11-20T14:00:00+07:00"), OffsetDateTime.parse("2026-11-20T14:30:00+07:00")))));
        mvc.perform(get("/api/v1/public/booking-slots").param("date", date.toString()).param("serviceId", SERVICE.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Phnom_Penh"))
                .andExpect(jsonPath("$.data.slots[0].startAt").value("2026-11-20T14:00:00+07:00"));
        mvc.perform(get("/api/v1/public/booking-slots").param("date", date.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingNeedsAnIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/public/appointments").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.type").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        verifyNoInteractions(appointments);
    }

    @Test
    void bookingWithMissingFieldsListsThem() throws Exception {
        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredPeriod\":\"MORNING\",\"patient\":{\"fullName\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.instance").value("/api/v1/public/appointments"))
                .andExpect(jsonPath("$.errors[?(@.field == 'patient.phone')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'serviceId')]").exists());
        verifyNoInteractions(appointments);
    }

    @Test
    void rateLimitedBookingIs429() throws Exception {
        doThrow(AppointmentException.rateLimited()).when(rateLimiter).acquire(any());

        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("RATE_LIMITED"));
        verifyNoInteractions(appointments);
    }

    @Test
    void periodFullIs409() throws Exception {
        when(appointments.request(any(), any())).thenThrow(AppointmentException.periodFull());

        mvc.perform(post("/api/v1/public/appointments").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("PERIOD_FULL"));
    }

    @Test
    void listPassesTheFilters() throws Exception {
        when(queries.list(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), new PageInfo(1, 50, 0, 0)));

        mvc.perform(get("/api/v1/admin/appointments")
                        .param("status", "PENDING").param("from", "2026-11-16").param("to", "2026-11-22")
                        .param("q", "Sok").param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1));

        verify(queries).list(new ListFilter(AppointmentStatus.PENDING, null,
                LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-22"), "Sok"), 1, 50);
    }

    @Test
    void unknownStatusFilterIs400() throws Exception {
        mvc.perform(get("/api/v1/admin/appointments").param("status", "CANCELLED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void detailCarriesTheETag() throws Exception {
        when(queries.detail(ID)).thenReturn(new VersionedDetail(detail(), 3));

        mvc.perform(get("/api/v1/admin/appointments/" + ID))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.data.reference").value("SK-20261120-0007"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-18T10:05:00+07:00"));
    }

    @Test
    void malformedIdIs404() throws Exception {
        mvc.perform(get("/api/v1/admin/appointments/not-an-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void statusChangeSendsTheVersionAndStaffMember() throws Exception {
        when(queries.detail(ID)).thenReturn(new VersionedDetail(detail(), 4));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-subject-1")
                .claim("name", "Reception One").build();

        mvc.perform(patch("/api/v1/admin/appointments/" + ID + "/arrive").header("If-Match", "\"3\"")
                        .principal(new JwtAuthenticationToken(jwt)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""));

        verify(appointments).arrive(ID, 3L, Actor.staff("kc-subject-1", "Reception One"));
    }

    @Test
    void missingOrMalformedIfMatchIsLeftToTheService() throws Exception {
        when(queries.detail(ID)).thenReturn(new VersionedDetail(detail(), 4));
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-subject-1").build();

        mvc.perform(patch("/api/v1/admin/appointments/" + ID + "/start").principal(new JwtAuthenticationToken(jwt)));
        mvc.perform(patch("/api/v1/admin/appointments/" + ID + "/complete").header("If-Match", "W/\"3\"")
                .principal(new JwtAuthenticationToken(jwt)));

        verify(appointments).start(eq(ID), isNull(), any());
        verify(appointments).complete(eq(ID), eq(-1L), any());
    }

    @Test
    void versionRequiredIs428() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("kc-subject-1").build();
        when(appointments.reject(eq(ID), isNull(), any(), any())).thenThrow(AppointmentException.versionRequired());

        mvc.perform(patch("/api/v1/admin/appointments/" + ID + "/reject").principal(new JwtAuthenticationToken(jwt))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Full\"}"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.type").value("VERSION_REQUIRED"));
    }

    private static AppointmentSnapshot snapshot() {
        Instant now = Instant.parse("2026-11-18T03:05:00Z");
        return new AppointmentSnapshot(ID, "SK-20261120-0007", AppointmentStatus.PENDING, SERVICE, "Sok Dara",
                "+85512345678", null, "km", "First visit", LocalDate.parse("2026-11-20"), "MORNING", 30, 10,
                null, null, null, now, now, 0, null, null);
    }

    private static AppointmentDetail detail() {
        OffsetDateTime created = OffsetDateTime.of(2026, 11, 18, 10, 5, 0, 0, ZoneOffset.ofHours(7));
        return new AppointmentDetail(ID, "SK-20261120-0007", AppointmentStatus.PENDING, "Sok Dara", "+85512345678",
                "Chronic Disease Management", LocalDate.parse("2026-11-20"), "MORNING", null, null, created,
                SERVICE, null, "km", null, 30, 10, null, null, created, null, null, null);
    }
}
