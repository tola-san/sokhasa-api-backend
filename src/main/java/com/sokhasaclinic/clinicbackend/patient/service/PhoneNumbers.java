package com.sokhasaclinic.clinicbackend.patient.service;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalises phone numbers to E.164 so the same person is found whichever way they type their
 * number. Local Cambodian numbers ({@code 012 345 678}) get the {@code +855} country code.
 */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-.()]");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final String CAMBODIA = "+855";

    private PhoneNumbers() {
    }

    /** The number in E.164, or empty when it cannot be a valid phone number. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String number = SEPARATORS.matcher(raw.strip()).replaceAll("");
        if (number.startsWith("00")) {
            number = "+" + number.substring(2);
        } else if (number.startsWith("0")) {
            number = CAMBODIA + number.substring(1);
        } else if (number.startsWith("855")) {
            number = "+" + number;
        }
        return E164.matcher(number).matches() ? Optional.of(number) : Optional.empty();
    }
}
