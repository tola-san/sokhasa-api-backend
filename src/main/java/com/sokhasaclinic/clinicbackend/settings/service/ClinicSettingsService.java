package com.sokhasaclinic.clinicbackend.settings.service;

import com.sokhasaclinic.clinicbackend.settings.dto.ClinicSettingsDto;
import com.sokhasaclinic.clinicbackend.settings.dto.WorkingHoursDto;
import com.sokhasaclinic.clinicbackend.settings.model.ClinicSettings;
import com.sokhasaclinic.clinicbackend.settings.model.ClinicWorkingHours;
import com.sokhasaclinic.clinicbackend.settings.repository.ClinicSettingsRepository;
import com.sokhasaclinic.clinicbackend.settings.repository.ClinicWorkingHoursRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicSettingsService {

    private final ClinicSettingsRepository settingsRepository;
    private final ClinicWorkingHoursRepository workingHoursRepository;

    public ClinicSettingsService(
            ClinicSettingsRepository settingsRepository,
            ClinicWorkingHoursRepository workingHoursRepository) {
        this.settingsRepository = settingsRepository;
        this.workingHoursRepository = workingHoursRepository;
    }

    @Transactional
    public ClinicSettingsDto getClinicSettings() {
        ClinicSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseGet(this::createDefaultSettings);
        return ClinicSettingsDto.fromEntity(settings);
    }

    @Transactional
    public ClinicSettingsDto updateClinicSettings(ClinicSettingsDto dto) {
        ClinicSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseGet(this::createDefaultSettings);

        settings.setName(dto.name());
        settings.setPhone(dto.phone());
        settings.setEmail(dto.email());
        settings.setAddress(dto.address());
        if (dto.timezone() != null) {
            settings.setTimezone(dto.timezone());
        }
        if (dto.supportedLanguages() != null && !dto.supportedLanguages().isEmpty()) {
            settings.setSupportedLanguages(dto.supportedLanguages().toArray(new String[0]));
        }
        settings.setUpdatedAt(Instant.now());

        return ClinicSettingsDto.fromEntity(settingsRepository.save(settings));
    }

    @Transactional
    public List<WorkingHoursDto> getWorkingHours() {
        List<ClinicWorkingHours> hours = workingHoursRepository.findAll();
        if (hours.isEmpty()) {
            hours = initDefaultWorkingHours();
        }
        return hours.stream()
                .sorted(Comparator.comparing(ClinicWorkingHours::getDayOfWeek))
                .map(WorkingHoursDto::fromEntity)
                .toList();
    }

    @Transactional
    public List<WorkingHoursDto> updateWorkingHours(List<WorkingHoursDto> dtos) {
        for (WorkingHoursDto dto : dtos) {
            Optional<ClinicWorkingHours> existing = workingHoursRepository.findByDayOfWeek(dto.dayOfWeek());
            ClinicWorkingHours wh;
            if (existing.isPresent()) {
                wh = existing.get();
                wh.setOpenTime(dto.openTime());
                wh.setCloseTime(dto.closeTime());
                wh.setClosed(dto.isClosed());
            } else {
                wh = ClinicWorkingHours.builder()
                        .dayOfWeek(dto.dayOfWeek())
                        .openTime(dto.openTime())
                        .closeTime(dto.closeTime())
                        .isClosed(dto.isClosed())
                        .build();
            }
            workingHoursRepository.save(wh);
        }
        return getWorkingHours();
    }

    private ClinicSettings createDefaultSettings() {
        ClinicSettings settings = ClinicSettings.builder()
                .name("Sokhasa Clinic")
                .phone("+85512345678")
                .email("info@example.com")
                .address("Phnom Penh, Cambodia")
                .timezone("Asia/Phnom_Penh")
                .supportedLanguages(new String[]{"km", "en", "zh"})
                .updatedAt(Instant.now())
                .build();
        return settingsRepository.save(settings);
    }

    private List<ClinicWorkingHours> initDefaultWorkingHours() {
        List<ClinicWorkingHours> list = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            ClinicWorkingHours wh = ClinicWorkingHours.builder()
                    .dayOfWeek(day)
                    .openTime(LocalTime.of(8, 0))
                    .closeTime(LocalTime.of(17, 0))
                    .isClosed(day == DayOfWeek.SUNDAY)
                    .build();
            list.add(workingHoursRepository.save(wh));
        }
        return list;
    }
}
