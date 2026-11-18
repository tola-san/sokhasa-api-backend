package com.sokhasaclinic.clinicbackend.settings.repository;

import com.sokhasaclinic.clinicbackend.settings.model.ClinicSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, UUID> {
}
