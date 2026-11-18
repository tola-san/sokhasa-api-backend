package com.sokhasaclinic.clinicbackend.common.time;

import java.time.ZoneId;

/**
 * The clinic's time zone (decision BK-08). Timestamps are stored in UTC; dates, booking periods and
 * confirm start times are interpreted in this zone.
 */
public final class ClinicTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private ClinicTime() {
    }
}
