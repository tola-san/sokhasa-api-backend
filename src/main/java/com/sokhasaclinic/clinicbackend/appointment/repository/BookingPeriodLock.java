package com.sokhasaclinic.clinicbackend.appointment.repository;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Serialises capacity checks for one date and period with a PostgreSQL transaction-level advisory
 * lock, so simultaneous requests cannot overbook the period (capacity rule, November 17, 2026). It
 * works without a row per date and is released when the transaction ends.
 */
@Component
public class BookingPeriodLock {

    /** First key of the two-key advisory lock, so these locks never collide with other features. */
    private static final int NAMESPACE = 0x426B5031; // "BkP1"

    private final JdbcTemplate jdbc;

    public BookingPeriodLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Blocks until this transaction holds the lock for {@code date} and {@code periodCode}. */
    public void lock(LocalDate date, String periodCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The booking period lock needs an active transaction.");
        }
        jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", NAMESPACE, date + "/" + periodCode);
    }
}
