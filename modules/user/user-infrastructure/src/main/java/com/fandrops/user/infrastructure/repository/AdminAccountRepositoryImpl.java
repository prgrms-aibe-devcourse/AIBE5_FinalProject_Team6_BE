package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.AdminAccountRepository;
import com.fandrops.user.domain.AdminAccount;
import com.fandrops.user.infrastructure.persistence.AdminAccountJpaEntity;
import com.fandrops.user.infrastructure.persistence.AdminAccountJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdminAccountRepositoryImpl implements AdminAccountRepository {

    private final AdminAccountJpaRepository jpaRepository;

    public AdminAccountRepositoryImpl(AdminAccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<AdminAccount> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId).map(AdminAccountJpaEntity::toDomain);
    }

    @Override
    public AdminAccount save(AdminAccount adminAccount) {
        return jpaRepository.save(AdminAccountJpaEntity.from(adminAccount)).toDomain();
    }
}
