package com.sokhasaclinic.clinicbackend.patient.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTests {

    @ParameterizedTest
    @CsvSource({
            "012 345 678,       +85512345678",
            "012-345-678,       +85512345678",
            "+855 12 345 678,   +85512345678",
            "85512345678,       +85512345678",
            "0085512345678,     +85512345678",
            "(097) 7777 888,    +855977777888",
            "+66 81 234 5678,   +66812345678"
    })
    void normalisesToE164(String raw, String expected) {
        assertEquals(Optional.of(expected), PhoneNumbers.normalize(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "12345", "abc", "+0123456789", "012 345 678 901 234 56", "+855-12-345-678x"})
    void rejectsInvalidNumbers(String raw) {
        assertEquals(Optional.empty(), PhoneNumbers.normalize(raw));
    }
}
