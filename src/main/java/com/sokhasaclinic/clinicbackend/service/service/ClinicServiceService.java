package com.sokhasaclinic.clinicbackend.service.service;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import com.sokhasaclinic.clinicbackend.service.dto.ServiceRequest;
import com.sokhasaclinic.clinicbackend.service.dto.ServiceResponse;
import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import com.sokhasaclinic.clinicbackend.service.model.ServiceTranslation;
import com.sokhasaclinic.clinicbackend.service.repository.ClinicServiceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicServiceService {

    private final ClinicServiceRepository serviceRepository;

    public ClinicServiceService(ClinicServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public ApiResponsePage<ServiceResponse> listServices(ClinicService.Status status, Pageable pageable) {
        Page<ClinicService> page = serviceRepository.findWithFilter(status, pageable);
        return ApiResponsePage.of(page.map(ServiceResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listPublicServices() {
        return serviceRepository.findAllByStatusWithTranslations(ClinicService.Status.ACTIVE)
                .stream()
                .map(ServiceResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(UUID id) {
        ClinicService service = serviceRepository.findByIdWithTranslations(id);
        if (service == null) {
            throw new ResourceNotFoundException("Service not found: " + id, "SERVICE_NOT_FOUND");
        }
        return ServiceResponse.fromEntity(service);
    }

    @Transactional
    public ServiceResponse createService(ServiceRequest request) {
        ClinicService service = ClinicService.builder()
                .durationMinutes(request.durationMinutes())
                .status(request.status() != null ? request.status() : ClinicService.Status.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .translations(new ArrayList<>())
                .build();

        if (request.translations() != null) {
            for (var trDto : request.translations()) {
                ServiceTranslation translation = ServiceTranslation.builder()
                        .clinicService(service)
                        .languageCode(trDto.languageCode().trim().toLowerCase())
                        .name(trDto.name().trim())
                        .description(trDto.description())
                        .build();
                service.getTranslations().add(translation);
            }
        }

        return ServiceResponse.fromEntity(serviceRepository.save(service));
    }

    @Transactional
    public ServiceResponse updateService(UUID id, ServiceRequest request) {
        ClinicService service = serviceRepository.findByIdWithTranslations(id);
        if (service == null) {
            throw new ResourceNotFoundException("Service not found: " + id, "SERVICE_NOT_FOUND");
        }

        service.setDurationMinutes(request.durationMinutes());
        if (request.status() != null) {
            service.setStatus(request.status());
        }
        service.setUpdatedAt(Instant.now());

        service.getTranslations().clear();
        if (request.translations() != null) {
            for (var trDto : request.translations()) {
                ServiceTranslation translation = ServiceTranslation.builder()
                        .clinicService(service)
                        .languageCode(trDto.languageCode().trim().toLowerCase())
                        .name(trDto.name().trim())
                        .description(trDto.description())
                        .build();
                service.getTranslations().add(translation);
            }
        }

        return ServiceResponse.fromEntity(serviceRepository.save(service));
    }

    @Transactional
    public ServiceResponse setServiceStatus(UUID id, ClinicService.Status status) {
        ClinicService service = serviceRepository.findByIdWithTranslations(id);
        if (service == null) {
            throw new ResourceNotFoundException("Service not found: " + id, "SERVICE_NOT_FOUND");
        }
        service.setStatus(status);
        service.setUpdatedAt(Instant.now());
        return ServiceResponse.fromEntity(serviceRepository.save(service));
    }

    @Transactional
    public void deleteService(UUID id) {
        if (!serviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Service not found: " + id, "SERVICE_NOT_FOUND");
        }
        serviceRepository.deleteById(id);
    }
}
