package com.sokhasaclinic.clinicbackend.staff.repository;

import com.sokhasaclinic.clinicbackend.staff.model.Staff;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Optional<Staff> findByKeycloakUserId(String keycloakUserId);

    Optional<Staff> findByUsername(String username);

    boolean existsByUsername(String username);
}
