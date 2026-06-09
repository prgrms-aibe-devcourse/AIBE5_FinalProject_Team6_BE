package com.fandrops.user.application.port;

import com.fandrops.user.domain.AdminAccount;

import java.util.Optional;

public interface AdminAccountRepository {
    Optional<AdminAccount> findByLoginId(String loginId);
    AdminAccount save(AdminAccount adminAccount);
}