package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.AgencyApplicationRepository;
import com.fandrops.user.domain.AgencyApplication;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AgencyApplicationRepositoryImpl implements AgencyApplicationRepository {

    @Override
    public AgencyApplication save(AgencyApplication application) {
        throw new UnsupportedOperationException("미구현");
    }

    @Override
    public Optional<AgencyApplication> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public boolean existsPendingByBusinessRegistrationNumber(String businessRegistrationNumber) {
        return false;
    }

    @Override
    public List<AgencyApplication> findAll() {
        return List.of();
    }
}
