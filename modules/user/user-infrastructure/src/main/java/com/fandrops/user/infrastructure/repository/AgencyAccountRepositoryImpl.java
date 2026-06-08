package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.infrastructure.persistence.AgencyAccountJpaEntity;
import com.fandrops.user.infrastructure.persistence.AgencyAccountJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AgencyAccountRepositoryImpl implements AgencyAccountRepository {

    private final AgencyAccountJpaRepository jpaRepository;

    public AgencyAccountRepositoryImpl(AgencyAccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AgencyAccount save(AgencyAccount account) {
        return jpaRepository.save(AgencyAccountJpaEntity.from(account)).toDomain();
    }

    @Override
    public Optional<AgencyAccount> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId).map(AgencyAccountJpaEntity::toDomain);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }
}