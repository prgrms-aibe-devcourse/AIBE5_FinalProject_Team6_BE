package com.fandrops.user.application.port;

import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationStatus;

import java.util.List;
import java.util.Optional;

public interface AgencyApplicationRepository {
    AgencyApplication save(AgencyApplication application);
    Optional<AgencyApplication> findById(Long id);
    // 중복 신청 차단: 동일 사업자등록번호로 PENDING 신청이 존재하는지 확인
    boolean existsPendingByBusinessRegistrationNumber(String businessRegistrationNumber);
    List<AgencyApplication> findAll();
    List<AgencyApplication> findAllByStatus(AgencyApplicationStatus status);
}