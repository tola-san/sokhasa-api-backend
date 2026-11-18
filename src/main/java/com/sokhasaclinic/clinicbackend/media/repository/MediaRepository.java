package com.sokhasaclinic.clinicbackend.media.repository;

import com.sokhasaclinic.clinicbackend.media.model.Media;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaRepository extends JpaRepository<Media, UUID> {
}
