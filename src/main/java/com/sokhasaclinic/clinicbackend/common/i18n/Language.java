package com.sokhasaclinic.clinicbackend.common.i18n;

import java.util.Optional;

/**
 * Languages the clinic publishes content in. Declaration order is the order used in API
 * responses: Khmer, English, Chinese. Every published piece of content must exist in all of them.
 */
public enum Language {
    KM("km"),
    EN("en"),
    ZH("zh");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    /** Lowercase ISO 639-1 code used in URLs, JSON and the database. */
    public String code() {
        return code;
    }

    /** Exact, case-sensitive match on the code: {@code "km"} matches, {@code "KM"} and {@code "km-KH"} do not. */
    public static Optional<Language> fromCode(String code) {
        for (Language language : values()) {
            if (language.code.equals(code)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
