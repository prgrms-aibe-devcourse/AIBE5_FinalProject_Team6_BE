package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.dto.ArtistSummary;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaEntity;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistProfileRepositoryImplTest {

    @Mock private ArtistProfileJpaRepository jpaRepository;

    private ArtistProfileRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ArtistProfileRepositoryImpl(jpaRepository);
    }

    private ArtistProfileJpaEntity buildEntity(Long id, long fanCount) {
        ArtistProfile domain = ArtistProfile.reconstitute(
                id, 1L, "아티스트" + id, fanCount, LocalDateTime.now(),
                null, null, null, null, null, null);
        return ArtistProfileJpaEntity.from(domain);
    }

    @Test
    @DisplayName("findById — 존재하는 id → domain 반환")
    void findById_found_returnsDomain() {
        ArtistProfileJpaEntity entity = buildEntity(10L, 100L);
        when(jpaRepository.findById(10L)).thenReturn(Optional.of(entity));

        Optional<ArtistProfile> result = repository.findById(10L);

        assertTrue(result.isPresent());
        assertEquals(10L, result.get().getId());
    }

    @Test
    @DisplayName("findById — 존재하지 않는 id → empty")
    void findById_notFound_returnsEmpty() {
        when(jpaRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(repository.findById(99L).isEmpty());
    }

    @Test
    @DisplayName("findAllOrderByFanCountDesc — cursorId null → 첫 페이지 쿼리 호출")
    void findAllOrderByFanCountDesc_noCursor_callsFirstPageQuery() {
        when(jpaRepository.findAllByOrderByFanCountDescIdAsc(any()))
                .thenReturn(List.of(buildEntity(1L, 50L), buildEntity(2L, 30L)));

        List<ArtistProfile> result = repository.findAllOrderByFanCountDesc(null, 10);

        assertEquals(2, result.size());
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(jpaRepository).findAllByOrderByFanCountDescIdAsc(captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
        verify(jpaRepository, never()).findAfterCursor(any(), any());
    }

    @Test
    @DisplayName("findAllOrderByFanCountDesc — cursorId 있음 → 커서 쿼리 호출")
    void findAllOrderByFanCountDesc_withCursor_callsCursorQuery() {
        when(jpaRepository.findAfterCursor(eq(5L), any()))
                .thenReturn(List.of(buildEntity(6L, 20L)));

        List<ArtistProfile> result = repository.findAllOrderByFanCountDesc(5L, 10);

        assertEquals(1, result.size());
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(jpaRepository).findAfterCursor(eq(5L), captor.capture());
        assertEquals(10, captor.getValue().getPageSize());
        verify(jpaRepository, never()).findAllByOrderByFanCountDescIdAsc(any());
    }

    @Test
    @DisplayName("findAllByIds — 매칭되는 ID 있으면 ArtistSummary 리스트 반환")
    void findAllByIds_withMatchingIds_returnsArtistSummaryList() {
        when(jpaRepository.findAllById(Set.of(1L, 2L)))
                .thenReturn(List.of(buildEntity(1L, 10L), buildEntity(2L, 20L)));

        List<ArtistSummary> result = repository.findAllByIds(Set.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).artistId());
        assertEquals("아티스트1", result.get(0).artistName());
        assertNull(result.get(0).profileImageUrl());
    }

    @Test
    @DisplayName("findAllByIds — 빈 Set이면 빈 리스트 반환")
    void findAllByIds_withEmptySet_returnsEmptyList() {
        when(jpaRepository.findAllById(Set.of())).thenReturn(List.of());

        List<ArtistSummary> result = repository.findAllByIds(Set.of());

        assertTrue(result.isEmpty());
    }
}
