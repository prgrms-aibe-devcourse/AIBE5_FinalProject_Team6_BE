package com.fandrops.user.application.service;

import com.fandrops.user.application.event.AgencyTempPasswordResetEmailEvent;
import com.fandrops.user.application.exception.AgencyAccountNotFoundException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AgencyAccountStatus;
import com.fandrops.user.domain.AuditLog;
import com.fandrops.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyAccountServiceTest {

    @Mock AgencyAccountRepository agencyAccountRepository;
    @Mock AuditLogPort auditLogPort;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;

    AgencyAccountService service;

    private static final Long ADMIN_ID  = 1L;
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String TRACE_ID  = "test-trace";

    @BeforeEach
    void setUp() {
        service = new AgencyAccountService(
                agencyAccountRepository, auditLogPort, passwordEncoder, eventPublisher);
    }

    @Test
    @DisplayName("존재하지 않는 계정 ID — AgencyAccountNotFoundException")
    void resetTempPassword_accountNotFound_throwsAgencyAccountNotFoundException() {
        when(agencyAccountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AgencyAccountNotFoundException.class,
                () -> service.resetTempPassword(99L, ADMIN_ID, CLIENT_IP, TRACE_ID));

        verify(agencyAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(auditLogPort, never()).save(any());
    }

    @Test
    @DisplayName("정상 재발급 — 새 passwordHash 저장 + 이메일 이벤트 발행 + 감사 로그 기록")
    void resetTempPassword_success_savesNewHashAndPublishesEvent() {
        AgencyAccount existing = buildAccount(10L);
        when(agencyAccountRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPw");
        when(agencyAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetTempPassword(10L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<AgencyAccount> accountCaptor = ArgumentCaptor.forClass(AgencyAccount.class);
        verify(agencyAccountRepository).save(accountCaptor.capture());
        assertEquals("newHashedPw", accountCaptor.getValue().getPasswordHash());
        assertEquals(10L, accountCaptor.getValue().getId());

        verify(eventPublisher).publishEvent(any(AgencyTempPasswordResetEmailEvent.class));
        verify(auditLogPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("이메일 이벤트에 contactEmail·loginId·tempPassword가 포함된다")
    void resetTempPassword_success_emailEventContainsCorrectFields() {
        AgencyAccount existing = buildAccount(10L);
        when(agencyAccountRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPw");
        when(agencyAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetTempPassword(10L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<AgencyTempPasswordResetEmailEvent> captor =
                ArgumentCaptor.forClass(AgencyTempPasswordResetEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AgencyTempPasswordResetEmailEvent event = captor.getValue();
        assertEquals("contact@hybe.com", event.email());
        assertEquals("contact@hybe.com", event.loginId());
        assertNotNull(event.tempPassword());
        assertFalse(event.tempPassword().isBlank());
    }

    @Test
    @DisplayName("감사 로그에 AGENCY_TEMP_PASSWORD_RESET 액션과 adminId·resourceId가 기록된다")
    void resetTempPassword_success_auditLogHasCorrectFields() {
        AgencyAccount existing = buildAccount(10L);
        when(agencyAccountRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPw");
        when(agencyAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetTempPassword(10L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPort).save(logCaptor.capture());
        AuditLog log = logCaptor.getValue();
        assertEquals("AGENCY_TEMP_PASSWORD_RESET", log.getAction());
        assertEquals("ADMIN", log.getActorType());
        assertEquals(ADMIN_ID, log.getActorId());
        assertEquals(10L, log.getResourceId());
        assertEquals("AGENCY_ACCOUNT", log.getResourceType());
    }

    private AgencyAccount buildAccount(Long id) {
        return AgencyAccount.builder()
                .id(id)
                .loginId("contact@hybe.com")
                .passwordHash("oldHashedPw")
                .companyName("HYBE")
                .contactEmail("contact@hybe.com")
                .status(AgencyAccountStatus.ACTIVE)
                .role(UserRole.AGENCY)
                .build();
    }
}
