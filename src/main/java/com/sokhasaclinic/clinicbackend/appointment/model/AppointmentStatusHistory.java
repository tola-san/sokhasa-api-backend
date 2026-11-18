package com.sokhasaclinic.clinicbackend.appointment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** One status change of an appointment: who, when, from, to and why. Written with the change. */
@Entity
@Table(name = "appointment_status_history", schema = "clinic")
@Getter
public class AppointmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 12)
    private AppointmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 12)
    private AppointmentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_id", updatable = false)
    private String actorId;

    @Column(name = "actor_name", updatable = false)
    private String actorName;

    @Column(updatable = false, length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AppointmentStatusHistory() {
    }

    public AppointmentStatusHistory(UUID appointmentId, AppointmentStatus fromStatus, AppointmentStatus toStatus,
            Actor actor, String reason, Instant changedAt) {
        this.appointmentId = appointmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actor.type();
        this.actorId = actor.id();
        this.actorName = actor.name();
        this.reason = reason;
        this.changedAt = changedAt;
    }
}
