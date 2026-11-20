package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.AppointmentDetail;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.AppointmentListItem;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingPeriodAvailability;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.DashboardSummary;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.DoctorResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.PageInfo;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.PageResponse;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.model.BookingPeriod;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingPeriodRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingSettings;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicServiceTranslation;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceTranslationRepository;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the appointment API: the reception list, detail, dashboard summary, doctors and the
 * public period availability. Builds response DTOs, so entities never reach the controllers.
 */
@Service
@Transactional(readOnly = true)
public class AppointmentQueryService {

    /** AP-15: the longest date range the list accepts. */
    static final int MAX_RANGE_DAYS = 62;
    static final int MAX_PAGE_SIZE = 100;
    /** Staff read service names in English. */
    private static final String STAFF_LANGUAGE = "en";

    /** Filters of {@code GET /admin/appointments}; every field is optional. */
    public record ListFilter(AppointmentStatus status, LocalDate date, LocalDate from, LocalDate to, String q) {
    }

    /** A detail with the version for its ETag. */
    public record VersionedDetail(AppointmentDetail data, long version) {
    }

    private final AppointmentRepository appointments;
    private final BookingPeriodRepository periods;
    private final BookingSettings settings;
    private final ClinicServiceRepository services;
    private final ClinicServiceTranslationRepository serviceText;
    private final DoctorRepository doctors;
    private final Clock clock;

    @Autowired
    public AppointmentQueryService(
            AppointmentRepository appointments,
            BookingPeriodRepository periods,
            BookingSettings settings,
            ClinicServiceRepository services,
            ClinicServiceTranslationRepository serviceText,
            DoctorRepository doctors) {
        this(appointments, periods, settings, services, serviceText, doctors, Clock.systemUTC());
    }

    AppointmentQueryService(
            AppointmentRepository appointments,
            BookingPeriodRepository periods,
            BookingSettings settings,
            ClinicServiceRepository services,
            ClinicServiceTranslationRepository serviceText,
            DoctorRepository doctors,
            Clock clock) {
        this.appointments = appointments;
        this.periods = periods;
        this.settings = settings;
        this.services = services;
        this.serviceText = serviceText;
        this.doctors = doctors;
        this.clock = clock;
    }

    /** Oldest request first, so the longest-waiting one is at the top. */
    public PageResponse<AppointmentListItem> list(ListFilter filter, int page, int size) {
        if (page < 0) {
            throw AppointmentException.invalidField("page", "Page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw AppointmentException.invalidField("size", "Size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Page<Appointment> result = appointments.findAll(specification(filter),
                PageRequest.of(page, size, Sort.by("createdAt", "id")));
        Map<UUID, String> names = serviceNames(result.getContent().stream().map(a -> a.getService().getId()).toList());
        Map<UUID, String> preferred = doctorNames(result.getContent().stream()
                .map(Appointment::getPreferredDoctorId).toList());
        List<AppointmentListItem> items = result.getContent().stream()
                .map(a -> listItem(a, names.getOrDefault(a.getService().getId(), ""),
                        preferred.get(a.getPreferredDoctorId())))
                .toList();
        return new PageResponse<>(items,
                new PageInfo(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    public VersionedDetail detail(UUID id) {
        Appointment a = appointments.findWithDoctor(id).orElseThrow(AppointmentException::appointmentNotFound);
        String serviceName = serviceNames(List.of(a.getService().getId())).getOrDefault(a.getService().getId(), "");
        String preferredName = a.getPreferredDoctorId() == null ? null
                : doctorNames(List.of(a.getPreferredDoctorId())).get(a.getPreferredDoctorId());
        AppointmentListItem item = listItem(a, serviceName, preferredName);
        AppointmentDetail detail = new AppointmentDetail(
                item.id(), item.reference(), item.status(), item.patientName(), item.patientPhone(),
                item.serviceName(), item.preferredDate(), item.preferredPeriod(), item.doctorName(),
                item.startAt(), item.createdAt(),
                a.getService().getId(), a.getPatientEmail(), a.getLanguageCode(), a.getNotes(),
                a.getDurationMinutes(), a.getBufferMinutes(),
                a.getDoctor() == null ? null : a.getDoctor().getId(),
                clinicTime(a.getEndAt()), clinicTime(a.getUpdatedAt()), requestedTime(a),
                a.getPreferredDoctorId(), preferredName);
        return new VersionedDetail(detail, a.getVersion());
    }

    /** Pending requests, and today's confirmed and later appointments by status. */
    public DashboardSummary summary() {
        Map<AppointmentStatus, Long> today = new EnumMap<>(AppointmentStatus.class);
        for (Object[] row : appointments.countByStatusOn(today())) {
            today.put((AppointmentStatus) row[0], (Long) row[1]);
        }
        return new DashboardSummary(appointments.countByStatus(AppointmentStatus.PENDING), today);
    }

    /** AP-16. */
    public List<DoctorResponse> activeDoctors() {
        return doctors.findByStatusOrderBySortOrderAscFullNameAsc(DoctorStatus.ACTIVE).stream()
                .map(d -> new DoctorResponse(d.getId(), d.getFullName()))
                .toList();
    }

    /**
     * AP-11: each period on {@code date} and whether the service still fits. A hint only; the booking
     * request checks capacity again under the lock. Outside the booking window nothing is available.
     */
    public List<BookingPeriodAvailability> periodAvailability(LocalDate date, UUID serviceId) {
        ClinicService service = Optional.ofNullable(serviceId)
                .flatMap(services::findById)
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                .orElseThrow(AppointmentException::serviceNotFound);
        LocalDate today = today();
        boolean inWindow = !date.isBefore(today) && !date.isAfter(today.plusDays(settings.windowDays()));
        int needed = service.getDurationMinutes() + service.getBufferMinutes();
        List<BookingPeriodAvailability> result = new ArrayList<>();
        for (BookingPeriod period : periods.findAllByOrderBySortOrderAscStartTimeAsc()) {
            boolean available = inWindow
                    && appointments.minutesUsed(date, period.getCode()) + needed <= period.getCapacityMinutes();
            result.add(new BookingPeriodAvailability(period.getCode(), period.getStartTime().toString(),
                    period.getEndTime().toString(), available));
        }
        return result;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ClinicTime.ZONE));
    }

    /**
     * {@code date} and {@code from}/{@code to} match the confirmed date, or the preferred date until
     * there is one. {@code q} matches the reference, the patient's name, or the digits of the phone.
     */
    static Specification<Appointment> specification(ListFilter filter) {
        if (filter.date() != null && (filter.from() != null || filter.to() != null)) {
            throw AppointmentException.invalidField("date", "Send either date or from and to, not both.");
        }
        if ((filter.from() == null) != (filter.to() == null)) {
            throw AppointmentException.invalidField(filter.from() == null ? "from" : "to", "Send both from and to.");
        }
        if (filter.from() != null) {
            if (filter.to().isBefore(filter.from())) {
                throw AppointmentException.invalidField("to", "The range must end on or after its start.");
            }
            if (ChronoUnit.DAYS.between(filter.from(), filter.to()) + 1 > MAX_RANGE_DAYS) {
                throw AppointmentException.invalidField("to", "The range can be at most " + MAX_RANGE_DAYS + " days.");
            }
        }
        String q = filter.q() == null ? "" : filter.q().strip();
        if (q.length() > 100) {
            throw AppointmentException.invalidField("q", "Search text can be up to 100 characters.");
        }

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            Expression<LocalDate> day = cb.coalesce(root.<LocalDate>get("confirmedDate"), root.<LocalDate>get("preferredDate"));
            if (filter.date() != null) {
                predicates.add(cb.equal(day, filter.date()));
            }
            if (filter.from() != null) {
                predicates.add(cb.between(day, filter.from(), filter.to()));
            }
            if (!q.isEmpty()) {
                String text = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> matches = new ArrayList<>();
                matches.add(cb.like(cb.lower(root.get("reference")), text, '\\'));
                matches.add(cb.like(cb.lower(root.get("patientName")), text, '\\'));
                String digits = q.replaceAll("\\D", "");
                if (digits.length() >= 3) {
                    // A local number (012...) matches the stored +85512...: drop the leading 0.
                    String core = digits.startsWith("0") ? digits.substring(1) : digits;
                    matches.add(cb.like(root.get("patientPhone"), "%" + core + "%"));
                }
                predicates.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private AppointmentListItem listItem(Appointment a, String serviceName, String preferredDoctorName) {
        return new AppointmentListItem(
                a.getId(), a.getReference(), a.getStatus(), a.getPatientName(), a.getPatientPhone(), serviceName,
                a.getPreferredDate(), a.getPreferredPeriod(),
                a.getDoctor() == null ? null : a.getDoctor().getFullName(),
                clinicTime(a.getStartAt()), clinicTime(a.getCreatedAt()), requestedTime(a), preferredDoctorName);
    }

    /** PD-07: current full names of preferred doctors, whatever their status or visibility. */
    private Map<UUID, String> doctorNames(Collection<UUID> doctorIds) {
        Set<UUID> ids = doctorIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return doctors.findAllById(ids).stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getFullName));
    }

    private Map<UUID, String> serviceNames(Collection<UUID> serviceIds) {
        Set<UUID> ids = Set.copyOf(serviceIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return serviceText.findByServiceIds(ids).stream()
                .filter(t -> STAFF_LANGUAGE.equals(t.getId().getLanguageCode()))
                .collect(Collectors.toMap(t -> t.getId().getOwnerId(), ClinicServiceTranslation::getName, (a, b) -> a));
    }

    private static String requestedTime(Appointment a) {
        return a.getPreferredStartTime() == null ? null : a.getPreferredStartTime().toString();
    }

    static OffsetDateTime clinicTime(Instant instant) {
        return instant == null ? null : instant.atZone(ClinicTime.ZONE).toOffsetDateTime();
    }
}
