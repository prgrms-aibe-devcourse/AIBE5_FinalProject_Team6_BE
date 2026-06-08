package com.fandrops.user.infrastructure.config;

import com.fandrops.user.application.port.AdminAccountRepository;
import com.fandrops.user.domain.AdminAccount;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 기본 admin 계정이 없으면 생성한다.
 * mastercode: loginId=admin / password=admin
 * 프로덕션에서는 별도 비밀번호 변경 절차 필요.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String MASTER_LOGIN_ID = "admin";
    private static final String MASTER_PASSWORD = "admin";

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(AdminAccountRepository adminAccountRepository,
                                   PasswordEncoder passwordEncoder) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminAccountRepository.findByLoginId(MASTER_LOGIN_ID).isEmpty()) {
            adminAccountRepository.save(
                    new AdminAccount(null, MASTER_LOGIN_ID, passwordEncoder.encode(MASTER_PASSWORD))
            );
        }
    }
}
