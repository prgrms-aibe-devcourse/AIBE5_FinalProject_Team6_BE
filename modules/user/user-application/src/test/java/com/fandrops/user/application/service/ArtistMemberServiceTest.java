package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.exception.ArtistMemberNotFoundException;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.ArtistProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistMemberServiceTest {

    @Mock ArtistMemberRepository artistMemberRepository;
    @Mock AgencyAccountRepository agencyAccountRepository;
    @Mock ArtistProfileRepository artistProfileRepository;
    @Mock AuditLogPort auditLogPort;
    @Mock PasswordEncoder passwordEncoder;

    ArtistMemberService artistMemberService;

    private static final Long ACTOR_ID   = 10L;
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String TRACE_ID  = "test-trace";

    @BeforeEach
    void setUp() {
        artistMemberService = new ArtistMemberService(
                artistMemberRepository, agencyAccountRepository,
                artistProfileRepository, auditLogPort, passwordEncoder);
    }

    // ── artistId 존재 검증 ────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 artistId — ArtistNotFoundException")
    void createArtistMember_artistNotFound_throwsArtistNotFoundException() {
        when(artistProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ArtistNotFoundException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(99L, "hani", "pass", "하니"),
                        ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(agencyAccountRepository, never()).existsByLoginId(anyString());
        verify(artistMemberRepository, never()).save(any());
    }

    // ── 소유권 검증 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("다른 Agency 소속 artistId — ArtistNotFoundException (소유권 불일치)")
    void createArtistMember_artistBelongsToOtherAgency_throwsArtistNotFoundException() {
        ArtistProfile otherAgencyProfile = ArtistProfile.builder()
                .id(1L).agencyId(99L).name("타 소속 아티스트").build(); // ACTOR_ID=10, agencyId=99 → 불일치
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(otherAgencyProfile));

        assertThrows(ArtistNotFoundException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(1L, "hani", "pass", "하니"),
                        ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(agencyAccountRepository, never()).existsByLoginId(anyString());
        verify(artistMemberRepository, never()).save(any());
    }

    // ── loginId 중복 체크 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Agency 계정과 동일한 loginId — DuplicateLoginIdException (보안 결함 #267)")
    void createArtistMember_loginIdConflictsWithAgency_throwsDuplicateLoginIdException() {
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(true);

        assertThrows(DuplicateLoginIdException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(1L, "hani", "pass", "하니"),
                        ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(agencyAccountRepository).existsByLoginId("hani");
        verify(artistMemberRepository, never()).existsByLoginId(anyString());
        verify(artistMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("ArtistMember 내 중복 loginId — DuplicateLoginIdException")
    void createArtistMember_loginIdAlreadyUsedByArtistMember_throwsDuplicateLoginIdException() {
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(false);
        when(artistMemberRepository.existsByLoginId("hani")).thenReturn(true);

        assertThrows(DuplicateLoginIdException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(1L, "hani", "pass", "하니"),
                        ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(agencyAccountRepository).existsByLoginId("hani");
        verify(artistMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("TOCTOU: save() 시 DataIntegrityViolationException → DuplicateLoginIdException (409)")
    void createArtistMember_concurrentDuplicate_translatesToDuplicateLoginIdException() {
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(false);
        when(artistMemberRepository.existsByLoginId("hani")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(artistMemberRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateLoginIdException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(1L, "hani", "pass", "하니"),
                        ACTOR_ID, CLIENT_IP, TRACE_ID));
    }

    // ── 정상 생성 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginId 중복 없음 — ArtistMember 정상 저장 및 감사 로그 기록")
    void createArtistMember_uniqueLoginId_savesAndWritesAuditLog() {
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(false);
        when(artistMemberRepository.existsByLoginId("hani")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        ArtistMember saved = ArtistMember.builder()
                .id(1L).artistId(1L).loginId("hani").passwordHash("hashed").memberName("하니").build();
        when(artistMemberRepository.save(any(ArtistMember.class))).thenReturn(saved);

        ArtistMember result = artistMemberService.createArtistMember(
                new CreateArtistMemberCommand(1L, "hani", "pass", "하니"),
                ACTOR_ID, CLIENT_IP, TRACE_ID);

        assertEquals("hani", result.getLoginId());
        verify(artistMemberRepository).save(any(ArtistMember.class));
        verify(auditLogPort).save(any());
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 멤버 조회 — ArtistMember 반환")
    void getArtistMember_found_returnsMember() {
        when(artistMemberRepository.findById(1L)).thenReturn(Optional.of(dummyMember()));
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));

        ArtistMember result = artistMemberService.getArtistMember(1L, ACTOR_ID, CLIENT_IP, TRACE_ID);

        assertEquals("hani", result.getLoginId());
    }

    @Test
    @DisplayName("존재하지 않는 멤버 조회 — ArtistMemberNotFoundException")
    void getArtistMember_notFound_throwsArtistMemberNotFoundException() {
        when(artistMemberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ArtistMemberNotFoundException.class,
                () -> artistMemberService.getArtistMember(99L, ACTOR_ID, CLIENT_IP, TRACE_ID));
    }

    @Test
    @DisplayName("타 Agency 소속 멤버 조회 — ArtistMemberNotFoundException (소유권 불일치)")
    void getArtistMember_wrongAgency_throwsArtistMemberNotFoundException() {
        ArtistMember otherMember = ArtistMember.builder()
                .id(1L).artistId(2L).loginId("hani").passwordHash("hash").memberName("하니").build();
        ArtistProfile otherProfile = ArtistProfile.builder()
                .id(2L).agencyId(99L).name("타 소속").build();
        when(artistMemberRepository.findById(1L)).thenReturn(Optional.of(otherMember));
        when(artistProfileRepository.findById(2L)).thenReturn(Optional.of(otherProfile));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> artistMemberService.getArtistMember(1L, ACTOR_ID, CLIENT_IP, TRACE_ID));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버 이름 수정 성공 — 변경된 이름으로 저장")
    void updateArtistMemberName_success_savesUpdatedName() {
        ArtistMember existing = dummyMember();
        ArtistMember renamed = existing.withMemberName("다니");
        when(artistMemberRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));
        when(artistMemberRepository.save(any(ArtistMember.class))).thenReturn(renamed);

        ArtistMember result = artistMemberService.updateArtistMemberName(
                1L, "다니", ACTOR_ID, CLIENT_IP, TRACE_ID);

        assertEquals("다니", result.getMemberName());
        verify(artistMemberRepository).save(any(ArtistMember.class));
        verify(auditLogPort).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 멤버 이름 수정 — ArtistMemberNotFoundException")
    void updateArtistMemberName_notFound_throwsArtistMemberNotFoundException() {
        when(artistMemberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ArtistMemberNotFoundException.class,
                () -> artistMemberService.updateArtistMemberName(
                        99L, "다니", ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(artistMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("타 Agency 소속 멤버 이름 수정 — ArtistMemberNotFoundException (소유권 불일치)")
    void updateArtistMemberName_wrongAgency_throwsArtistMemberNotFoundException() {
        ArtistMember otherMember = ArtistMember.builder()
                .id(1L).artistId(2L).loginId("hani").passwordHash("hash").memberName("하니").build();
        ArtistProfile otherProfile = ArtistProfile.builder()
                .id(2L).agencyId(99L).name("타 소속").build();
        when(artistMemberRepository.findById(1L)).thenReturn(Optional.of(otherMember));
        when(artistProfileRepository.findById(2L)).thenReturn(Optional.of(otherProfile));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> artistMemberService.updateArtistMemberName(
                        1L, "다니", ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(artistMemberRepository, never()).save(any());
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버 소프트 삭제 성공 — deletedAt 세팅 후 저장")
    void deleteArtistMember_success_savesWithDeletedAt() {
        when(artistMemberRepository.findById(1L)).thenReturn(Optional.of(dummyMember()));
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile()));
        when(artistMemberRepository.save(any(ArtistMember.class))).thenAnswer(inv -> inv.getArgument(0));

        artistMemberService.deleteArtistMember(1L, ACTOR_ID, CLIENT_IP, TRACE_ID);

        verify(artistMemberRepository).save(argThat(m -> m.getDeletedAt() != null));
        verify(auditLogPort).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 멤버 삭제 — ArtistMemberNotFoundException")
    void deleteArtistMember_notFound_throwsArtistMemberNotFoundException() {
        when(artistMemberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ArtistMemberNotFoundException.class,
                () -> artistMemberService.deleteArtistMember(99L, ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(artistMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("타 Agency 소속 멤버 삭제 — ArtistMemberNotFoundException (소유권 불일치)")
    void deleteArtistMember_wrongAgency_throwsArtistMemberNotFoundException() {
        ArtistMember otherMember = ArtistMember.builder()
                .id(1L).artistId(2L).loginId("hani").passwordHash("hash").memberName("하니").build();
        ArtistProfile otherProfile = ArtistProfile.builder()
                .id(2L).agencyId(99L).name("타 소속").build();
        when(artistMemberRepository.findById(1L)).thenReturn(Optional.of(otherMember));
        when(artistProfileRepository.findById(2L)).thenReturn(Optional.of(otherProfile));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> artistMemberService.deleteArtistMember(1L, ACTOR_ID, CLIENT_IP, TRACE_ID));

        verify(artistMemberRepository, never()).save(any());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ArtistProfile dummyProfile() {
        return ArtistProfile.builder()
                .id(1L).agencyId(10L).name("테스트 아티스트").build();
    }

    private ArtistMember dummyMember() {
        return ArtistMember.builder()
                .id(1L).artistId(1L).loginId("hani").passwordHash("hash").memberName("하니").build();
    }
}