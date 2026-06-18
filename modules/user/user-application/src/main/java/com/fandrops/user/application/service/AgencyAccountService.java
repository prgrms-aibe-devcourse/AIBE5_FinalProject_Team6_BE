package com.fandrops.user.application.service;

import com.fandrops.user.application.event.AgencyTempPasswordResetEmailEvent;
import com.fandrops.user.application.exception.AgencyAccountNotFoundException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AuditLog;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AgencyAccountService {

    private final AgencyAccountRepository agencyAccountRepository;
    private final AuditLogPort auditLogPort;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AgencyAccountService(
            AgencyAccountRepository agencyAccountRepository,
            AuditLogPort auditLogPort,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.agencyAccountRepository = agencyAccountRepository;
        this.auditLogPort = auditLogPort;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void resetTempPassword(Long agencyAccountId, Long adminId, String clientIp, String traceId) {
        AgencyAccount account = agencyAccountRepository.findById(agencyAccountId)
                .orElseThrow(() -> new AgencyAccountNotFoundException(
                        "존재하지 않는 Agency 계정입니다. id=" + agencyAccountId));

        String tempPassword = generateTempPassword();
        AgencyAccount updated = account.withPasswordHash(passwordEncoder.encode(tempPassword));
        agencyAccountRepository.save(updated);

        eventPublisher.publishEvent(
                new AgencyTempPasswordResetEmailEvent(account.getContactEmail(), account.getLoginId(), tempPassword));

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("ADMIN")
                .actorId(adminId)
                .action("AGENCY_TEMP_PASSWORD_RESET")
                .resourceType("AGENCY_ACCOUNT")
                .resourceId(agencyAccountId)
                .traceId(traceId)
                .clientIp(clientIp)
                .build());
    }

    private String generateTempPassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
