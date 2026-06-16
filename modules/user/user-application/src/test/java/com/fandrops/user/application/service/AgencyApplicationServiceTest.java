package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.AgencyApplicationResult;
import com.fandrops.user.application.dto.CreateAgencyApplicationCommand;
import com.fandrops.user.application.event.AgencyApplicationApprovedEmailEvent;
import com.fandrops.user.application.event.AgencyApplicationRejectedEmailEvent;
import com.fandrops.user.application.event.AgencyApprovedEvent;
import com.fandrops.user.application.exception.AgencyApplicationNotFoundException;
import com.fandrops.user.application.exception.DuplicateAgencyAccountException;
import com.fandrops.user.application.exception.DuplicateApplicationException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.AgencyApplicationRepository;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AgencyAccountStatus;
import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationAlreadyReviewedException;
import com.fandrops.user.domain.AgencyApplicationStatus;
import com.fandrops.user.domain.ArtistProfile;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyApplicationServiceTest {

    @Mock AgencyApplicationRepository agencyApplicationRepository;
    @Mock AgencyAccountRepository agencyAccountRepository;
    @Mock ArtistProfileRepository artistProfileRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuditLogPort auditLogPort;

    AgencyApplicationService service;

    private static final Long ADMIN_ID = 1L;
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String TRACE_ID = "test-trace-id";

    @BeforeEach
    void setUp() {
        service = new AgencyApplicationService(
                agencyApplicationRepository, agencyAccountRepository,
                artistProfileRepository, passwordEncoder,
                eventPublisher, auditLogPort);
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
    @DisplayName("사업자등록번호 빈 문자열은 null로 정규화되어 중복 체크를 건너뛴다")
    void submitApplication_blankRegistrationNumber_treatedAsNull() {
        CreateAgencyApplicationCommand command = new CreateAgencyApplicationCommand(
                "홍길동", "   ", "홍길동", "hong@test.com",
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
    @DisplayName("정상 승인 시 APPROVED 저장 + AgencyAccount 생성 + ArtistProfile 생성 + 이메일 이벤트 발행")
    void approveApplication_success() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenReturn(buildSavedAccount(100L));
        when(artistProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        verify(agencyApplicationRepository).save(any());
        verify(agencyAccountRepository).save(any());
        verify(artistProfileRepository).save(any());
        verify(eventPublisher).publishEvent(any(AgencyApplicationApprovedEmailEvent.class));
        verify(auditLogPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("존재하지 않는 신청서 ID로 승인 시 AgencyApplicationNotFoundException")
    void approveApplication_notFound_throws() {
        when(agencyApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AgencyApplicationNotFoundException.class,
                () -> service.approveApplication(99L, ADMIN_ID, CLIENT_IP, TRACE_ID));
        verify(agencyAccountRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AgencyApplicationApprovedEmailEvent.class));
    }

    @Test
    @DisplayName("이미 계정이 존재하는 이메일로 승인 시 DuplicateAgencyAccountException (중복 체크가 approve 전에 실행됨)")
    void approveApplication_duplicateAccount_throws() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(true);

        assertThrows(DuplicateAgencyAccountException.class,
                () -> service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID));

        // 중복 체크 후 approve()가 호출되지 않았으므로 save도 없어야 함
        verify(agencyApplicationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AgencyApplicationApprovedEmailEvent.class));
    }

    @Test
    @DisplayName("이미 APPROVED된 신청서를 다시 승인하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void approveApplication_alreadyApproved_throws() {
        AgencyApplication approved = buildApprovedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(approved));
        when(agencyAccountRepository.existsByLoginId(anyString())).thenReturn(false);

        assertThrows(AgencyApplicationAlreadyReviewedException.class,
                () -> service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID));
    }

    @Test
    @DisplayName("이미 REJECTED된 신청서를 승인하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void approveApplication_alreadyRejected_throws() {
        AgencyApplication rejected = buildRejectedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(rejected));
        when(agencyAccountRepository.existsByLoginId(anyString())).thenReturn(false);

        assertThrows(AgencyApplicationAlreadyReviewedException.class,
                () -> service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID));
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
        when(agencyAccountRepository.save(any())).thenReturn(buildSavedAccount(100L));
        when(artistProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<AgencyAccount> captor = ArgumentCaptor.forClass(AgencyAccount.class);
        verify(agencyAccountRepository).save(captor.capture());
        AgencyAccount saved = captor.getValue();
        assertEquals(AgencyAccountStatus.ACTIVE, saved.getStatus());
        assertEquals(UserRole.AGENCY, saved.getRole());
    }

    @Test
    @DisplayName("승인 시 ArtistProfile 은 agencyId=저장된계정ID, name=targetArtistName 으로 생성된다")
    void approveApplication_success_artistProfileHasCorrectFields() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenReturn(buildSavedAccount(100L));
        when(artistProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<ArtistProfile> captor = ArgumentCaptor.forClass(ArtistProfile.class);
        verify(artistProfileRepository).save(captor.capture());
        ArtistProfile saved = captor.getValue();
        assertEquals(100L, saved.getAgencyId());
        assertEquals("BTS", saved.getName());
        assertEquals(0L, saved.getFanCount());
        assertNotNull(saved.getJoinedAt());
    }

    @Test
    @DisplayName("승인 성공 시 AgencyApprovedEvent가 artistId·agencyId·artistName 포함하여 발행된다")
    void approveApplication_success_publishesAgencyApprovedEvent() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenReturn(buildSavedAccount(100L));
        when(artistProfileRepository.save(any())).thenReturn(buildSavedProfile(42L));

        service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<Object> allEventsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(allEventsCaptor.capture());
        AgencyApprovedEvent event = allEventsCaptor.getAllValues().stream()
                .filter(e -> e instanceof AgencyApprovedEvent)
                .map(e -> (AgencyApprovedEvent) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("AgencyApprovedEvent 미발행"));
        assertEquals(42L, event.getArtistId());
        assertEquals(100L, event.getAgencyId());
        assertEquals("BTS", event.getArtistName());
    }

    @Test
    @DisplayName("승인 이메일 이벤트에 email·loginId가 contactEmail로, tempPassword가 비어있지 않게 담긴다")
    void approveApplication_success_emailEventContainsCorrectFields() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenReturn(buildSavedAccount(100L));
        when(artistProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        AgencyApplicationApprovedEmailEvent emailEvent = captor.getAllValues().stream()
                .filter(e -> e instanceof AgencyApplicationApprovedEmailEvent)
                .map(e -> (AgencyApplicationApprovedEmailEvent) e)
                .findFirst()
                .orElseThrow(() -> new AssertionError("AgencyApplicationApprovedEmailEvent 미발행"));
        assertEquals("contact@hybe.com", emailEvent.email());
        assertEquals("contact@hybe.com", emailEvent.loginId());
        assertNotNull(emailEvent.tempPassword());
        assertFalse(emailEvent.tempPassword().isBlank());
    }

    @Test
    @DisplayName("ArtistProfile 저장 실패 시 예외가 전파된다 — DB 롤백 의도 확인")
    void approveApplication_artistProfileSaveFails_exceptionPropagates() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyAccountRepository.existsByLoginId("contact@hybe.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedTempPw");
        when(agencyApplicationRepository.save(any())).thenReturn(application);
        when(agencyAccountRepository.save(any())).thenReturn(buildSavedAccount(100L));
        doThrow(new RuntimeException("DB 저장 실패"))
                .when(artistProfileRepository).save(any());

        assertThrows(RuntimeException.class,
                () -> service.approveApplication(1L, ADMIN_ID, CLIENT_IP, TRACE_ID));
        verify(eventPublisher, never()).publishEvent(any(AgencyApprovedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(AgencyApplicationApprovedEmailEvent.class));
    }

    // ── rejectApplication ────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 반려 시 REJECTED 저장 + 이메일 이벤트 발행")
    void rejectApplication_success() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyApplicationRepository.save(any())).thenReturn(application);

        service.rejectApplication(1L, "서류 미비", ADMIN_ID, CLIENT_IP, TRACE_ID);

        verify(agencyApplicationRepository).save(any());
        verify(eventPublisher).publishEvent(any(AgencyApplicationRejectedEmailEvent.class));
        verify(auditLogPort).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("반려 이벤트에 email과 rejectReason이 정확히 담긴다")
    void rejectApplication_success_eventContainsCorrectFields() {
        AgencyApplication application = buildPendingApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(agencyApplicationRepository.save(any())).thenReturn(application);

        service.rejectApplication(1L, "서류 미비", ADMIN_ID, CLIENT_IP, TRACE_ID);

        ArgumentCaptor<AgencyApplicationRejectedEmailEvent> captor =
                ArgumentCaptor.forClass(AgencyApplicationRejectedEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("contact@hybe.com", captor.getValue().email());
        assertEquals("서류 미비", captor.getValue().rejectReason());
    }

    @Test
    @DisplayName("존재하지 않는 신청서 ID로 반려 시 AgencyApplicationNotFoundException")
    void rejectApplication_notFound_throws() {
        when(agencyApplicationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AgencyApplicationNotFoundException.class,
                () -> service.rejectApplication(99L, "서류 미비", ADMIN_ID, CLIENT_IP, TRACE_ID));
        verify(eventPublisher, never()).publishEvent(any(AgencyApplicationRejectedEmailEvent.class));
    }

    @Test
    @DisplayName("이미 APPROVED된 신청서를 반려하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void rejectApplication_alreadyApproved_throws() {
        AgencyApplication approved = buildApprovedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(approved));

        assertThrows(AgencyApplicationAlreadyReviewedException.class,
                () -> service.rejectApplication(1L, "추가 사유", ADMIN_ID, CLIENT_IP, TRACE_ID));
        verify(agencyApplicationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(AgencyApplicationRejectedEmailEvent.class));
    }

    @Test
    @DisplayName("이미 REJECTED된 신청서를 다시 반려하면 AgencyApplicationAlreadyReviewedException (AA-1)")
    void rejectApplication_alreadyRejected_throws() {
        AgencyApplication rejected = buildRejectedApplication(1L);
        when(agencyApplicationRepository.findById(1L)).thenReturn(Optional.of(rejected));

        assertThrows(AgencyApplicationAlreadyReviewedException.class,
                () -> service.rejectApplication(1L, "추가 사유", ADMIN_ID, CLIENT_IP, TRACE_ID));
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

    private ArtistProfile buildSavedProfile(Long id) {
        return ArtistProfile.builder()
                .id(id)
                .agencyId(100L)
                .name("BTS")
                .build();
    }

    private AgencyAccount buildSavedAccount(Long id) {
        return AgencyAccount.builder()
                .id(id)
                .loginId("contact@hybe.com")
                .passwordHash("hashedTempPw")
                .companyName("HYBE")
                .contactEmail("contact@hybe.com")
                .status(AgencyAccountStatus.ACTIVE)
                .role(UserRole.AGENCY)
                .build();
    }
}
