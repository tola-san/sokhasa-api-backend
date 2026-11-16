package com.sokhasaclinic.clinicbackend.cms.repository;

import com.sokhasaclinic.clinicbackend.cms.model.HomepageBanner;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HomepageBannerRepository extends JpaRepository<HomepageBanner, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from HomepageBanner b where b.id = 1")
    Optional<HomepageBanner> lockSingleton();

    @Query("select b from HomepageBanner b where b.id = 1")
    Optional<HomepageBanner> findSingleton();
}
