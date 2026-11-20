package com.sokhasaclinic.clinicbackend.dashboard.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DS-01: the ranges of {@code GET /admin/dashboard/stats}. Each ends with the bucket that contains
 * today, so the last bucket is partial.
 */
public enum StatsRange {
    /** The last 7 clinic days, one bucket per day. */
    WEEK("day", 7),
    /** The last 12 calendar months, one bucket per month. */
    MONTH("month", 12),
    /** The last 5 calendar years, one bucket per year. */
    YEAR("year", 5);

    private final String sqlUnit;
    private final int buckets;

    StatsRange(String sqlUnit, int buckets) {
        this.sqlUnit = sqlUnit;
        this.buckets = buckets;
    }

    /** The PostgreSQL {@code date_trunc} unit for one bucket. */
    public String sqlUnit() {
        return sqlUnit;
    }

    /** First day of each bucket, oldest first. */
    public List<LocalDate> bucketStarts(LocalDate today) {
        LocalDate current = switch (this) {
            case WEEK -> today;
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
        };
        List<LocalDate> starts = new ArrayList<>(buckets);
        for (int back = buckets - 1; back >= 0; back--) {
            starts.add(switch (this) {
                case WEEK -> current.minusDays(back);
                case MONTH -> current.minusMonths(back);
                case YEAR -> current.minusYears(back);
            });
        }
        return starts;
    }
}
