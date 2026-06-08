package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminAccountJpaRepository extends JpaRepository<AdminAccountJpaEntity, Long> {
    Optional<AdminAccountJpaEntity> findByLoginId(String loginId);
}
