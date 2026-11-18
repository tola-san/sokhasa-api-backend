package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read access to clinic doctors. The doctor editor comes after the MVP. */
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    /** AP-16: doctors reception can assign, in display order. */
    List<Doctor> findByStatusOrderBySortOrderAscFullNameAsc(DoctorStatus status);
}
