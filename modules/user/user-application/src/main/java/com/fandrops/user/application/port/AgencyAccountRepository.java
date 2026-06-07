package com.fandrops.user.application.port;

import com.fandrops.user.domain.AgencyAccount;

import java.util.Optional;

public interface AgencyAccountRepository {
    AgencyAccount save(AgencyAccount account);
    Optional<AgencyAccount> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}