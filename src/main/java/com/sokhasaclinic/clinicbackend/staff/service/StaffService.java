package com.sokhasaclinic.clinicbackend.staff.service;

import com.sokhasaclinic.clinicbackend.common.exception.ConflictException;
import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import com.sokhasaclinic.clinicbackend.staff.dto.StaffRequest;
import com.sokhasaclinic.clinicbackend.staff.dto.StaffResponse;
import com.sokhasaclinic.clinicbackend.staff.dto.UpdateStaffRolesRequest;
import com.sokhasaclinic.clinicbackend.staff.model.Staff;
import com.sokhasaclinic.clinicbackend.staff.repository.StaffRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff() {
        return staffRepository.findAll().stream()
                .map(StaffResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(UUID id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id, "STAFF_NOT_FOUND"));
        return StaffResponse.fromEntity(staff);
    }

    @Transactional
    public StaffResponse createStaff(StaffRequest request) {
        if (staffRepository.existsByUsername(request.username().trim())) {
            throw new ConflictException("Username already exists: " + request.username(), "USERNAME_ALREADY_EXISTS");
        }

        String keycloakId = "kc_" + UUID.randomUUID();

        Staff staff = Staff.builder()
                .keycloakUserId(keycloakId)
                .username(request.username().trim())
                .email(request.email() != null ? request.email().trim() : null)
                .firstName(request.firstName() != null ? request.firstName().trim() : null)
                .lastName(request.lastName() != null ? request.lastName().trim() : null)
                .roles(request.roles().toArray(new String[0]))
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return StaffResponse.fromEntity(staffRepository.save(staff));
    }

    @Transactional
    public StaffResponse updateStaff(UUID id, StaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id, "STAFF_NOT_FOUND"));

        staff.setEmail(request.email() != null ? request.email().trim() : null);
        staff.setFirstName(request.firstName() != null ? request.firstName().trim() : null);
        staff.setLastName(request.lastName() != null ? request.lastName().trim() : null);
        if (request.roles() != null && !request.roles().isEmpty()) {
            staff.setRoles(request.roles().toArray(new String[0]));
        }
        staff.setUpdatedAt(Instant.now());

        return StaffResponse.fromEntity(staffRepository.save(staff));
    }

    @Transactional
    public StaffResponse setStaffActive(UUID id, boolean active) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id, "STAFF_NOT_FOUND"));

        staff.setActive(active);
        staff.setUpdatedAt(Instant.now());

        return StaffResponse.fromEntity(staffRepository.save(staff));
    }

    @Transactional
    public StaffResponse updateStaffRoles(UUID id, UpdateStaffRolesRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id, "STAFF_NOT_FOUND"));

        staff.setRoles(request.roles().toArray(new String[0]));
        staff.setUpdatedAt(Instant.now());

        return StaffResponse.fromEntity(staffRepository.save(staff));
    }
}
