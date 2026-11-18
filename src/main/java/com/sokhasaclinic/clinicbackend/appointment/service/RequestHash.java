package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.model.PatientDetails;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * SHA-256 of a normalised booking request, stored with its Idempotency-Key (AP-03). Two submits of
 * the same form, even typed slightly differently ("012 345 678" and "+85512345678"), hash the same.
 */
final class RequestHash {

    private RequestHash() {
    }

    static String of(UUID serviceId, LocalDate date, String period, PatientDetails details) {
        // Unit separators keep "ab" + "c" apart from "a" + "bc".
        String canonical = String.join("\u001f",
                serviceId.toString(), date.toString(), period,
                details.languageCode(), details.fullName(), details.phone(),
                String.valueOf(details.email()), String.valueOf(details.notes()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
