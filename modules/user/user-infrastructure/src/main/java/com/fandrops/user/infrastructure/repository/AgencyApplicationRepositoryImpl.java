package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.AgencyApplicationRepository;
import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationStatus;
import com.fandrops.user.infrastructure.persistence.AgencyApplicationJpaEntity;
import com.fandrops.user.infrastructure.persistence.AgencyApplicationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AgencyApplicationRepositoryImpl implements AgencyApplicationRepository {

    private final AgencyApplicationJpaRepository jpaRepository;

    public AgencyApplicationRepositoryImpl(AgencyApplicationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AgencyApplication save(AgencyApplication application) {
        return jpaRepository.save(AgencyApplicationJpaEntity.from(application)).toDomain();
    }

    @Override
    public Optional<AgencyApplication> findById(Long id) {
        return jpaRepository.findById(id).map(AgencyApplicationJpaEntity::toDomain);
    }

    @Override
    public boolean existsPendingByBusinessRegistrationNumber(String businessRegistrationNumber) {
        return jpaRepository.existsByBusinessRegistrationNumberAndStatus(
                businessRegistrationNumber, AgencyApplicationStatus.PENDING);
    }

    @Override
    public List<AgencyApplication> findAll() {
        return jpaRepository.findAll().stream()
                .map(AgencyApplicationJpaEntity::toDomain)
                .toList();
    }
}
