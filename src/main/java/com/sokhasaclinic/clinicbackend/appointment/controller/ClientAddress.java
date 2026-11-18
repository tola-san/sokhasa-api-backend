package com.sokhasaclinic.clinicbackend.appointment.controller;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client address used for the booking rate limit (AP-12). Bookings reach the backend through
 * the Next.js server, so the website forwards the visitor's address in {@code X-Forwarded-For};
 * the first entry is used. The header can be forged by a caller that reaches the backend directly,
 * so the limit is a guard against accidental floods, not an access control.
 */
final class ClientAddress {

    private ClientAddress() {
    }

    static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].strip();
            if (!first.isEmpty() && first.length() <= 64) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
