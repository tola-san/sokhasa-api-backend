package com.sokhasaclinic.clinicbackend.dashboard.dto;

public record DashboardOverviewResponse(
        long totalAppointments,
        long pendingAppointments,
        long confirmedAppointments,
        long rejectedAppointments,
        long todayAppointments
) {}
