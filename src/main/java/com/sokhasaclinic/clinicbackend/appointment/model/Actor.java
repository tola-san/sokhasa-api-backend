package com.sokhasaclinic.clinicbackend.appointment.model;

import java.util.Objects;

/**
 * The caller behind a status change. {@code id} is the staff member's Keycloak subject and
 * {@code name} their display name at the time of the change; both are {@code null} for a guest.
 */
public record Actor(ActorType type, String id, String name) {

    public Actor {
        Objects.requireNonNull(type, "type");
        if ((type == ActorType.GUEST) != (id == null)) {
            throw new IllegalArgumentException("A guest has no id; a staff member must have one.");
        }
        if (type == ActorType.GUEST && name != null) {
            throw new IllegalArgumentException("A guest has no name.");
        }
    }

    public static Actor guest() {
        return new Actor(ActorType.GUEST, null, null);
    }

    public static Actor staff(String subject) {
        return staff(subject, null);
    }

    /** {@code name} may be null when the token carries no name. Long names are cut to 255 characters. */
    public static Actor staff(String subject, String name) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("A staff actor needs the Keycloak subject.");
        }
        String trimmed = name == null || name.isBlank() ? null : name.strip();
        return new Actor(ActorType.STAFF, subject,
                trimmed != null && trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed);
    }
}
