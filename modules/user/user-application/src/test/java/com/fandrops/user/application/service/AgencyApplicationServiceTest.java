package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.AgencyApplicationResult;
import com.fandrops.user.application.dto.CreateAgencyApplicationCommand;
import com.fandrops.user.application.exception.AgencyApplicationNotFoundException;
import com.fandrops.user.application.exception.DuplicateAgencyAccountException;
import com.fandrops.user.application.exception.DuplicateApplicationException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.AgencyApplicationRepository;
import com.fandrops.user.application.port.EmailNotificationPort;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AgencyAccountStatus;
import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationAlreadyReviewedException;
import com.fandrops.user.domain.AgencyApplicationStatus;
import com.fandrops.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyApplicationServiceTest {

    @Mock AgencyApplicationRepository agencyApplicationRepository;
    @Mock AgencyAccountRepository agencyAccountRepository;
    @Mock EmailNotificationPort emailNotificationPort;
    @Mock PasswordEncoder passwordEncoder;

    AgencyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AgencyApplicationService(
                agencyApplicationRepository, agencyAccountRepository,
                emailNotificationPort, passwordEncoder);
    }

    // ── submitApplication ────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 신청서 제출 시 PENDING 상태로 저장된다")
    void submitApplication_success() {
        CreateAgencyApplicationCommand command = validCommand();
        AgencyApplication saved = buildPendingApplication(1L);
        when(agencyApplicationRepository.existsPendingByBusinessRegistrationNumber("123-45-67890"))
                .thenReturn(false);
        when(agencyApplicationRepository.save(any())).thenReturn(saved);

        AgencyApplicationResult result = service.submitApplication(command);

        assertEquals(AgencyApplicationStatus.PENDING, result.status());
        assertEquals(1L, result.id());
        verify(agencyApplicationRepository).save(any());
    }

    @Test
    @DisplayName("동일 사업자등록번호로 PENDING 신청 존재 시 DuplicateApplicationException")
    void submitApplication_duplicateRegistrationNumber_throws() {
        CreateAgencyApplicationCommand command = validCommand();
        when(agencyApplicationRepository.existsPendingByBusinessRegistrationNumber("123-45-67890"))
                .thenReturn(true);

        assertThrows(DuplicateApplicationException.class, () -> service.submitApplication(command));
        verify(agencyApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("사업자등록번호 null 이면 중복 체크를 건너뛴다 (1인 크리에이터 허용)")
    void submitApplication_nullRegistrationNumber_skipsCheck() {
        CreateAgencyApplicationCommand command = new CreateAgencyApplicationCommand(
                "홍길동", null, "홍길동", "hong@test.com",
                "010-1234-5678", "소개글", "홍아티스트");
        AgencyApplication saved = AgencyApplication.builder()
                .companyName("홍길동").businessRegistrationNumber(null)
                .representativeName("홍길동").contactEmail("hong@test.com")
                .contactPhone("010-1234-5678").introduction("소개글")
                .targetArtistName("홍아티스트").build();
        when(agencyApplicationRepository.save(any())).thenReturn(saved);

        assertDoesNotThrow(() -> service.submitApplication(command));
        verify(agencyApplicationRepository, never())
                .existsPendingByBusinessRegistrationNumber(any());
    }

    // ── getApplications ──────────────────────────────────────────────────────

    @Test
    @DisplayName("status=null 이면 전체 목록을 반환한다")
    void getApplications_nullStatus_returnsAll() {
        when(agencyApplicationRepository.findAll())
                .thenReturn(List.of(buildPendingApplication(1L), buildPendingApplication(2L)));

        List<AgencyApplicationResult> results = service.getApplications(null);

        assertEquals(2, results.size());
        verify(agencyApplicationRepository).findAll();
        verify(agencyApplicationRepository, never()).findAllByStatus(any());
    }

    @Test
    @DisplayName("status=PENDING 이면 findAllByStatus(PENDING)을 호출한다")
    void getApplications_pendingStatus_callsFindAllByStatus() {
        when(agencyApplicationRepository.findAllByStatus(AgencyApplicationStatus.PENDING))
                .thenReturn(List.of(buildPendingApplication(1L)));

        List<AgencyApplicationResult> results = service.getApplications(AgencyApplicationStatus.PENDING);

        assertEquals(1, results.size());
        verify(agencyApplicationRepository).findAllByStatus(AgencyApplicationStatus.PENDING);
        verify(agencyApplicationRepository, never()).findAll();
    }

    // ── approveApplication ───────────────────────────────────────────────────

    @Test
    @DisplayName("정상 승인 시 APPROVED 저장 + AgencyAccount 생성 + 이메일 발송")
    void approveApplication_success() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveApplication(1L);

        verify(agencyApplicationRepository).save(any());
        verify(agencyAccountRepository).save(any());
        verify(emailNotificationPort).sendApplicationApprovedEmail(
                eq("contact@hybe.com"), eq("contact@hybe.com"), anyString());
    }

    @Test
    @DisplayName("존재하지 않는 신청서 ID로 승인 시 AgencyApplicationNotFoundException")
    void approveApplication_notFound_throws() {
        when(agencyApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AgencyApplicationNotFoundException.class,
                () -> service.approveApplication(99L));
        verify(agencyAccountRepository, never()).save(any());
        verify(emailNotificationPort, never()).sendApplicationApprovedEmail(any(), any(), any());
    }

    @Test
    @DisplayName("이미 계정이 존재하는 이메일로 승인 시 DuplicateAgencyAccountException (중복 체크가 approve 전에 실행됨)")
    void approveApplication_duplicateAccount_throws() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(true);

        assertThrows(DuplicateAgencyAccountException.class,
                () -> service.approveApplication(1L));

        // 중복 체크 후 approve()가 호출되지 않았으므로 save도 없어야 함
        verify(agencyApplicationRepository, never()).save(any());
        verify(emailNotificationPort, never()).sendApplicationApprovedEmail(any(), any(), any());
    }

    @Test
    @DisplayName("이미 APPROVED된 신청서를 다시 승인하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void approveApplication_alreadyApproved_throws() {
        AgencyApplication approved = buildApprovedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(approved));
        when(agencyAccountRepository.existsByLoginId(anyString())).thenReturn(false);

        assertThrows(AgencyApplicationAlreadyReviewedException.class, () -> service.approveApplication(1L));
    }

    @Test
    @DisplayName("이미 REJECTED된 신청서를 승인하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void approveApplication_alreadyRejected_throws() {
        AgencyApplication rejected = buildRejectedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(rejected));
        when(agencyAccountRepository.existsByLoginId(anyString())).thenReturn(false);

        assertThrows(AgencyApplicationAlreadyReviewedException.class, () -> service.approveApplication(1L));
        verify(agencyAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인 시 AgencyAccount 는 status=ACTIVE, role=AGENCY 로 저장된다")
    void approveApplication_success_accountHasCorrectStatusAndRole() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveApplication(1L);

        ArgumentCaptor<AgencyAccount> captor = ArgumentCaptor.forClass(AgencyAccount.class);
        verify(agencyAccountRepository).save(captor.capture());
        AgencyAccount saved = captor.getValue();
        assertEquals(AgencyAccountStatus.ACTIVE, saved.getStatus());
        assertEquals(UserRole.AGENCY, saved.getRole());
    }

    @Test
    @DisplayName("이메일 발송 실패 시 예외가 전파된다 — DB 롤백 의도 확인")
    void approveApplication_emailFails_exceptionPropagates() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("메일 서버 연결 실패"))
                .when(emailNotificationPort).sendApplicationApprovedEmail(any(), any(), any());

        assertThrows(RuntimeException.class, () -> service.approveApplication(1L));
    }

    // ── rejectApplication ────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 반려 시 REJECTED 저장 + 이메일 발송")
    void rejectApplication_success() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyApplicationRepository.save(any())).thenReturn(application);

        service.rejectApplication(1L, "서류 미비");

        verify(agencyApplicationRepository).save(any());
        verify(emailNotificationPort).sendApplicationRejectedEmail("contact@hybe.com", "서류 미비");
    }

    @Test
    @DisplayName("존재하지 않는 신청서 ID로 반려 시 AgencyApplicationNotFoundException")
    void rejectApplication_notFound_throws() {
        when(agencyApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AgencyApplicationNotFoundException.class,
                () -> service.rejectApplication(99L, "서류 미비"));
        verify(emailNotificationPort, never()).sendApplicationRejectedEmail(any(), any());
    }

    @Test
    @DisplayName("이미 APPROVED된 신청서를 반려하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void rejectApplication_alreadyApproved_throws() {
        AgencyApplication approved = buildApprovedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(approved));

        assertThrows(AgencyApplicationAlreadyReviewedException.class,
                () -> service.rejectApplication(1L, "추가 사유"));
        verify(agencyApplicationRepository, never()).save(any());
        verify(emailNotificationPort, never()).sendApplicationRejectedEmail(any(), any());
    }

    @Test
    @DisplayName("반려 이메일 발송 실패 시 예외가 전파된다 — DB 롤백 의도 확인")
    void rejectApplication_emailFails_exceptionPropagates() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        doThrow(new RuntimeException("메일 서버 연결 실패"))
                .when(emailNotificationPort).sendApplicationRejectedEmail(any(), any());

        assertThrows(RuntimeException.class, () -> service.rejectApplication(1L, "서류 미비"));
    }

    @Test
    @DisplayName("이미 REJECTED된 신청서를 다시 반려하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void rejectApplication_alreadyRejected_throws() {
        AgencyApplication rejected = buildRejectedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(rejected));

        assertThrows(AgencyApplicationAlreadyReviewedException.class,
                () -> service.rejectApplication(1L, "추가 사유"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CreateAgencyApplicationCommand validCommand() {
        return new CreateAgencyApplicationCommand(
                "HYBE", "123-45-67890", "방시혁",
                "contact@hybe.com", "02-1234-5678", "글로벌 K-Pop 기획사", "BTS");
    }

    private AgencyApplication buildPendingApplication(Long id) {
        return AgencyApplication.reconstitute(
                id, "HYBE", "123-45-67890", "방시혁",
                "contact@hybe.com", "02-1234-5678", "글로벌 K-Pop 기획사", "BTS",
                AgencyApplicationStatus.PENDING, null,
                LocalDateTime.of(2025, 1, 1, 0, 0), null);
    }

    private AgencyApplication buildApprovedApplication(Long id) {
        return AgencyApplication.reconstitute(
                id, "HYBE", "123-45-67890", "방시혁",
                "contact@hybe.com", "02-1234-5678", "글로벌 K-Pop 기획사", "BTS",
                AgencyApplicationStatus.APPROVED, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 5, 0, 0));
    }

    private AgencyApplication buildRejectedApplication(Long id) {
        return AgencyApplication.reconstitute(
                id, "HYBE", "123-45-67890", "방시혁",
                "contact@hybe.com", "02-1234-5678", "글로벌 K-Pop 기획사", "BTS",
                AgencyApplicationStatus.REJECTED, "서류 미비",
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 5, 0, 0));
    }
}
