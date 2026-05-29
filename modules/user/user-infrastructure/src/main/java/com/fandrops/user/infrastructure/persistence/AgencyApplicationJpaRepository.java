package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AgencyApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyApplicationJpaRepository extends JpaRepository<AgencyApplicationJpaEntity, Long> {
    boolean existsByBusinessRegistrationNumberAndStatus(String businessRegistrationNumber, AgencyApplicationStatus status);
}
