package com.sokhasaclinic.clinicbackend.doctor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sokhasaclinic.clinicbackend.appointment.controller.AppointmentExceptionHandler;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.doctor.controller.DoctorController;
import com.sokhasaclinic.clinicbackend.doctor.dto.*;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DoctorControllerTests {
    DoctorService service;
    MockMvc mvc;
    UUID id = UUID.randomUUID();
    UUID windowId = UUID.randomUUID();
    String base;
    @BeforeEach void setup() {
        service = mock(DoctorService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DoctorController(service)).setControllerAdvice(new AppointmentExceptionHandler()).build();
        base = "/api/v1/admin/doctors/" + id;
    }

    @Test void createReturnsLocationEtagAndClinicOffset() throws Exception {
        var now = OffsetDateTime.parse("2026-11-19T10:00:00+07:00");
        when(service.create(any())).thenReturn(new DoctorResponse(id, "Dr. Sok", DoctorStatus.ACTIVE, 0, 0, now, now));
        mvc.perform(post("/api/v1/admin/doctors").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dr. Sok\",\"status\":\"ACTIVE\",\"sortOrder\":0}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", base))
                .andExpect(header().string("ETag", "\"0\"")).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-19T10:00:00+07:00"));
    }

    @Test void invalidBodiesAndMissingQueryParametersReturnProblems() throws Exception {
        mvc.perform(post("/api/v1/admin/doctors").contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        mvc.perform(post(base + "/booking-windows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + UUID.randomUUID() + "\",\"date\":\"2026-11-21\",\"startTime\":\"2 pm\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base + "/booking-windows")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void windowsExposeLocationAndDeleteHonorsIfMatch() throws Exception {
        var data = new BookingWindowDtos.Response(windowId, id, UUID.randomUUID(), LocalDate.parse("2026-11-21"), "14:00", "17:00", 0);
        when(service.createWindow(eq(id), any())).thenReturn(data);
        mvc.perform(post(base + "/booking-windows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + data.serviceId() + "\",\"date\":\"2026-11-21\",\"startTime\":\"14:00\",\"endTime\":\"17:00\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", base + "/booking-windows/" + windowId))
                .andExpect(header().string("ETag", "\"0\""));
        mvc.perform(delete(base + "/booking-windows/" + windowId).header("If-Match", "\"0\""))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        verify(service).deleteWindow(id, windowId, "\"0\"");
        doThrow(AppointmentException.versionRequired()).when(service).deleteWindow(id, windowId, null);
        mvc.perform(delete(base + "/booking-windows/" + windowId)).andExpect(status().is(428));
        mvc.perform(get("/api/v1/admin/doctors/invalid-id")).andExpect(status().isNotFound());
    }
}
