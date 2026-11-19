package com.sokhasaclinic.clinicbackend.doctor.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The service–doctor links (PC-06). Information only; they never affect booking or assignment. */
@Repository
public class ServiceDoctorLinks {

    /** A linked active service in one language, in public service order (SV-05). */
    public record LinkedService(UUID doctorId, String slug, String name, String shortDescription) { }

    private final JdbcTemplate jdbc;

    public ServiceDoctorLinks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every linked service id, active or not, for the admin profile. */
    public List<UUID> serviceIds(UUID doctorId) {
        return jdbc.queryForList("""
                select sd.service_id from clinic.service_doctor sd
                join clinic.service s on s.id = sd.service_id
                join clinic.service_category c on c.id = s.category_id
                where sd.doctor_id = ?
                order by c.sort_order, s.sort_order, s.slug
                """, UUID.class, doctorId);
    }

    /** Linked public services per doctor, in the language and public order. */
    public Map<UUID, List<LinkedService>> activeServices(Collection<UUID> doctorIds, String languageCode) {
        Map<UUID, List<LinkedService>> result = new LinkedHashMap<>();
        if (doctorIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select sd.doctor_id, s.slug, st.name, st.short_description
                from clinic.service_doctor sd
                join clinic.service s on s.id = sd.service_id and s.status = 'ACTIVE'
                join clinic.service_category c on c.id = s.category_id
                join clinic.service_translation st on st.service_id = s.id and st.language_code = ?
                where sd.doctor_id = any (?)
                order by c.sort_order, s.sort_order, s.slug
                """,
                rs -> {
                    UUID doctorId = rs.getObject("doctor_id", UUID.class);
                    result.computeIfAbsent(doctorId, ignored -> new ArrayList<>()).add(new LinkedService(doctorId,
                            rs.getString("slug"), rs.getString("name"), rs.getString("short_description")));
                },
                languageCode, doctorIds.toArray(UUID[]::new));
        return result;
    }

    public List<UUID> doctorIds(UUID serviceId) {
        return jdbc.queryForList("select doctor_id from clinic.service_doctor where service_id = ? order by doctor_id",
                UUID.class, serviceId);
    }

    /** Replaces every link of the service. Callers hold the service's row lock. */
    public void replace(UUID serviceId, Collection<UUID> doctorIds) {
        jdbc.update("delete from clinic.service_doctor where service_id = ?", serviceId);
        for (UUID doctorId : doctorIds) {
            jdbc.update("insert into clinic.service_doctor (service_id, doctor_id) values (?, ?)", serviceId, doctorId);
        }
    }
}
