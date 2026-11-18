package com.sokhasaclinic.clinicbackend.appointment.repository;

import com.sokhasaclinic.clinicbackend.appointment.model.BookingPeriod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Booking periods such as MORNING and AFTERNOON (decision BK-04). */
public interface BookingPeriodRepository extends JpaRepository<BookingPeriod, String> {

    List<BookingPeriod> findAllByOrderBySortOrderAscStartTimeAsc();
}
