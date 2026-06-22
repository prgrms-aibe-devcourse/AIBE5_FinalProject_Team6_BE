package com.fandrops.user.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.exception.InvalidImageUrlException;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.application.port.S3ImageValidationPort;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.domain.ArtistProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistProfileServiceTest {

    @Mock ArtistProfileRepository artistProfileRepository;
    @Mock AuditLogPort auditLogPort;
    @Mock S3PresignedUrlPort s3PresignedUrlPort;
    @Mock S3ImageValidationPort s3ImageValidationPort;

    ObjectMapper objectMapper = new ObjectMapper();

    ArtistProfileService artistProfileService;

    @BeforeEach
    void setUp() {
        artistProfileService = new ArtistProfileService(
                artistProfileRepository, auditLogPort, s3PresignedUrlPort, s3ImageValidationPort, objectMapper);
    }

    // ── 단건 조회 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하는 artistId — 프로필 반환")
    void getArtistProfile_found_returnsProfile() {
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(dummyProfile(1L, 1000L)));

        ArtistProfile result = artistProfileService.getArtistProfile(1L);

        assertEquals(1L, result.getId());
        assertEquals("BTS", result.getName());
    }

    @Test
    @DisplayName("존재하지 않는 artistId — ArtistNotFoundException")
    void getArtistProfile_notFound_throwsArtistNotFoundException() {
        when(artistProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ArtistNotFoundException.class,
                () -> artistProfileService.getArtistProfile(99L));
    }

    // ── 목록 조회 (커서 페이지네이션) ─────────────────────────────────────────

    @Test
    @DisplayName("cursor=null, size=2, 데이터 2개 — hasMore=false, nextCursor=null")
    void listArtistProfiles_firstPageNoMore_returnsAll() {
        when(artistProfileRepository.findAllOrderByFanCountDesc(null, 3))
                .thenReturn(List.of(dummyProfile(1L, 500L), dummyProfile(2L, 300L)));

        ArtistProfileListResult result = artistProfileService.listArtistProfiles(null, 2);

        assertEquals(2, result.items().size());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    @DisplayName("cursor=null, size=2, 데이터 3개(size+1) — hasMore=true, nextCursor=마지막 id")
    void listArtistProfiles_firstPageHasMore_returnsPageAndCursor() {
        when(artistProfileRepository.findAllOrderByFanCountDesc(null, 3))
                .thenReturn(List.of(dummyProfile(1L, 500L), dummyProfile(2L, 300L), dummyProfile(3L, 100L)));

        ArtistProfileListResult result = artistProfileService.listArtistProfiles(null, 2);

        assertEquals(2, result.items().size());
        assertTrue(result.hasMore());
        assertEquals("2", result.nextCursor());
    }

    @Test
    @DisplayName("cursor=\"2\", 다음 페이지 조회 — cursorId=2L 로 repository 호출")
    void listArtistProfiles_withCursor_callsRepositoryWithParsedCursorId() {
        when(artistProfileRepository.findAllOrderByFanCountDesc(2L, 3))
                .thenReturn(List.of(dummyProfile(3L, 100L)));

        ArtistProfileListResult result = artistProfileService.listArtistProfiles("2", 2);

        verify(artistProfileRepository).findAllOrderByFanCountDesc(2L, 3);
        assertEquals(1, result.items().size());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    @DisplayName("결과 0개 — 빈 목록 반환, hasMore=false")
    void listArtistProfiles_emptyResult_returnsEmptyList() {
        when(artistProfileRepository.findAllOrderByFanCountDesc(null, 21))
                .thenReturn(List.of());

        ArtistProfileListResult result = artistProfileService.listArtistProfiles(null, 20);

        assertTrue(result.items().isEmpty());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    @DisplayName("정확히 size개 반환 — hasMore=false (size+1 초과 아님)")
    void listArtistProfiles_exactlySize_hasMoreFalse() {
        List<ArtistProfile> profiles = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            profiles.add(dummyProfile(i, 1000L - i * 10));
        }
        when(artistProfileRepository.findAllOrderByFanCountDesc(null, 21))
                .thenReturn(profiles);

        ArtistProfileListResult result = artistProfileService.listArtistProfiles(null, 20);

        assertEquals(20, result.items().size());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    @DisplayName("cursor가 숫자가 아닐 때 — IllegalArgumentException (400으로 처리됨)")
    void listArtistProfiles_invalidCursor_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> artistProfileService.listArtistProfiles("invalid", 20));
    }

    @Test
    @DisplayName("size=1 최솟값 경계 — size+1=2 요청, hasMore=true 이면 nextCursor 정상 반환")
    void listArtistProfiles_sizeOne_hasMoreTrue() {
        when(artistProfileRepository.findAllOrderByFanCountDesc(null, 2))
                .thenReturn(List.of(dummyProfile(1L, 500L), dummyProfile(2L, 300L)));

        ArtistProfileListResult result = artistProfileService.listArtistProfiles(null, 1);

        assertEquals(1, result.items().size());
        assertTrue(result.hasMore());
        assertEquals("1", result.nextCursor());
        verify(artistProfileRepository).findAllOrderByFanCountDesc(null, 2);
    }

    @Test
    @DisplayName("size=100 최댓값 경계 — repository에 101개 요청")
    void listArtistProfiles_maxSize_requestsSizePlusOne() {
        when(artistProfileRepository.findAllOrderByFanCountDesc(null, 101))
                .thenReturn(List.of());

        artistProfileService.listArtistProfiles(null, 100);

        verify(artistProfileRepository).findAllOrderByFanCountDesc(null, 101);
    }

    // ── Agency 소속 필터 목록 (listByAgencyId) ────────────────────────────────

    @Test
    @DisplayName("agencyId=10, cursor=null, 데이터 2개 — 소속 아티스트만 반환")
    void listByAgencyId_firstPage_returnsAgencyArtistsOnly() {
        when(artistProfileRepository.findByAgencyId(10L, null, 3))
                .thenReturn(List.of(dummyProfile(1L, 500L), dummyProfile(2L, 300L)));

        ArtistProfileListResult result = artistProfileService.listByAgencyId(10L, null, 2);

        assertEquals(2, result.items().size());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
        verify(artistProfileRepository).findByAgencyId(10L, null, 3);
    }

    @Test
    @DisplayName("agencyId=10, size=1, 데이터 2건(size+1) — hasMore=true, nextCursor 반환")
    void listByAgencyId_hasMore_returnsCursor() {
        when(artistProfileRepository.findByAgencyId(10L, null, 2))
                .thenReturn(List.of(dummyProfile(1L, 500L), dummyProfile(2L, 300L)));

        ArtistProfileListResult result = artistProfileService.listByAgencyId(10L, null, 1);

        assertEquals(1, result.items().size());
        assertTrue(result.hasMore());
        assertEquals("1", result.nextCursor());
    }

    @Test
    @DisplayName("agencyId=10, cursor=\"1\" — cursorId=1L로 repository 호출")
    void listByAgencyId_withCursor_callsRepositoryWithParsedCursorId() {
        when(artistProfileRepository.findByAgencyId(10L, 1L, 3))
                .thenReturn(List.of(dummyProfile(2L, 300L)));

        ArtistProfileListResult result = artistProfileService.listByAgencyId(10L, "1", 2);

        verify(artistProfileRepository).findByAgencyId(10L, 1L, 3);
        assertEquals(1, result.items().size());
        assertFalse(result.hasMore());
    }

    @Test
    @DisplayName("agencyId=10, 소속 아티스트 없음 — 빈 목록 반환")
    void listByAgencyId_noArtists_returnsEmpty() {
        when(artistProfileRepository.findByAgencyId(10L, null, 21))
                .thenReturn(List.of());

        ArtistProfileListResult result = artistProfileService.listByAgencyId(10L, null, 20);

        assertTrue(result.items().isEmpty());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    @DisplayName("agencyId=10, cursor가 숫자 아닐 때 — IllegalArgumentException")
    void listByAgencyId_invalidCursor_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> artistProfileService.listByAgencyId(10L, "invalid", 20));
    }

    @Test
    @DisplayName("size=1 최솟값 경계 — size+1=2 요청, hasMore=true 이면 nextCursor 정상 반환")
    void listByAgencyId_sizeOne_hasMoreTrue() {
        when(artistProfileRepository.findByAgencyId(10L, null, 2))
                .thenReturn(List.of(dummyProfile(1L, 500L), dummyProfile(2L, 300L)));

        ArtistProfileListResult result = artistProfileService.listByAgencyId(10L, null, 1);

        assertEquals(1, result.items().size());
        assertTrue(result.hasMore());
        assertEquals("1", result.nextCursor());
        verify(artistProfileRepository).findByAgencyId(10L, null, 2);
    }

    @Test
    @DisplayName("size=100 최댓값 경계 — repository에 101개 요청")
    void listByAgencyId_maxSize_requestsSizePlusOne() {
        when(artistProfileRepository.findByAgencyId(10L, null, 101)).thenReturn(List.of());

        artistProfileService.listByAgencyId(10L, null, 100);

        verify(artistProfileRepository).findByAgencyId(10L, null, 101);
    }

    // ── updateArtistProfile ───────────────────────────────────────────────────

    @Test
    @DisplayName("소유권 일치 — 프로필 수정 후 저장된 프로필 반환")
    void updateArtistProfile_ownerMatches_savesAndReturns() {
        ArtistProfile profile = dummyProfile(1L, 500L);
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(artistProfileRepository.save(any())).thenReturn(profile);

        ArtistProfile result = artistProfileService.updateArtistProfile(
                1L, 10L, "새 bio", null, null, null, null, null, "127.0.0.1", "trace");

        verify(artistProfileRepository).save(profile);
        assertNotNull(result);
    }

    @Test
    @DisplayName("소유권 불일치 — ArtistNotFoundException")
    void updateArtistProfile_agencyMismatch_throwsArtistNotFoundException() {
        ArtistProfile profile = dummyProfile(1L, 500L); // agencyId=10
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        assertThrows(ArtistNotFoundException.class,
                () -> artistProfileService.updateArtistProfile(
                        1L, 99L, "bio", null, null, null, null, null, "127.0.0.1", "trace"));
    }

    @Test
    @DisplayName("존재하지 않는 아티스트 — ArtistNotFoundException")
    void updateArtistProfile_notFound_throwsArtistNotFoundException() {
        when(artistProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ArtistNotFoundException.class,
                () -> artistProfileService.updateArtistProfile(
                        99L, 10L, "bio", null, null, null, null, null, "127.0.0.1", "trace"));
    }

    // ── generateProfileImagePresignedUrl ──────────────────────────────────────

    @Test
    @DisplayName("허용된 contentType — presigned URL 반환")
    void generateProfileImagePresignedUrl_allowed_returnsResult() {
        ArtistProfile profile = dummyProfile(1L, 500L);
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        PresignedUploadResult stubResult = new PresignedUploadResult(
                "https://s3.example.com/presigned", "https://s3.example.com/img.jpg",
                Instant.now().plusSeconds(300));
        when(s3PresignedUrlPort.generateForProfileImage("image/jpeg", 1000L)).thenReturn(stubResult);

        PresignedUploadResult result = artistProfileService.generateProfileImagePresignedUrl(
                1L, 10L, "image/jpeg", 1000L, "127.0.0.1", "trace");

        assertEquals("https://s3.example.com/presigned", result.presignedUrl());
    }

    @Test
    @DisplayName("허용되지 않는 contentType — InvalidContentTypeException")
    void generateProfileImagePresignedUrl_invalidContentType_throws() {
        assertThrows(InvalidContentTypeException.class,
                () -> artistProfileService.generateProfileImagePresignedUrl(
                        1L, 10L, "application/pdf", 1000L, "127.0.0.1", "trace"));
    }

    // ── confirmProfileImageUrl ────────────────────────────────────────────────

    @Test
    @DisplayName("S3 소유 URL — 프로필 이미지 URL 확정 후 저장")
    void confirmProfileImageUrl_ownedUrl_savesImageUrl() {
        ArtistProfile profile = dummyProfile(1L, 500L);
        when(artistProfileRepository.findById(1L)).thenReturn(Optional.of(profile));
        when(s3ImageValidationPort.isOwnedUrl("https://cdn.fandrops.com/img.jpg")).thenReturn(true);
        when(artistProfileRepository.save(any())).thenReturn(profile);

        ArtistProfile result = artistProfileService.confirmProfileImageUrl(
                1L, 10L, "https://cdn.fandrops.com/img.jpg", "127.0.0.1", "trace");

        verify(artistProfileRepository).save(profile);
        assertNotNull(result);
    }

    @Test
    @DisplayName("외부 도메인 URL — InvalidImageUrlException")
    void confirmProfileImageUrl_externalUrl_throwsInvalidImageUrlException() {
        when(s3ImageValidationPort.isOwnedUrl("https://attacker.com/img.jpg")).thenReturn(false);

        assertThrows(InvalidImageUrlException.class,
                () -> artistProfileService.confirmProfileImageUrl(
                        1L, 10L, "https://attacker.com/img.jpg", "127.0.0.1", "trace"));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ArtistProfile dummyProfile(Long id, long fanCount) {
        return ArtistProfile.builder()
                .id(id).agencyId(10L).name("BTS")
                .fanCount(fanCount)
                .joinedAt(LocalDateTime.of(2013, 6, 13, 0, 0))
                .build();
    }
}
