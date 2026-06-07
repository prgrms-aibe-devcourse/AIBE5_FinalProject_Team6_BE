package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgencyAccountJpaRepository extends JpaRepository<AgencyAccountJpaEntity, Long> {
    Optional<AgencyAccountJpaEntity> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}