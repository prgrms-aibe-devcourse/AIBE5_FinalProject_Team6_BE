package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AgencyApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgencyApplicationJpaRepository extends JpaRepository<AgencyApplicationJpaEntity, Long> {
    boolean existsByBusinessRegistrationNumberAndStatus(String businessRegistrationNumber, AgencyApplicationStatus status);
    List<AgencyApplicationJpaEntity> findAllByStatus(AgencyApplicationStatus status);
}
