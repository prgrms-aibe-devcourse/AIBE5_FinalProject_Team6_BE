package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.domain.ArtistProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistProfileServiceTest {

    @Mock ArtistProfileRepository artistProfileRepository;

    ArtistProfileService artistProfileService;

    @BeforeEach
    void setUp() {
        artistProfileService = new ArtistProfileService(artistProfileRepository);
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

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ArtistProfile dummyProfile(Long id, long fanCount) {
        return ArtistProfile.builder()
                .id(id).agencyId(10L).name("BTS")
                .fanCount(fanCount)
                .joinedAt(LocalDateTime.of(2013, 6, 13, 0, 0))
                .build();
    }
}
