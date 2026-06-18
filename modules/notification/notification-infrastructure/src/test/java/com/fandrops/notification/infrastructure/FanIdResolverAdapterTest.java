package com.fandrops.notification.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FanIdResolverAdapterTest {

    @Mock JdbcTemplate jdbcTemplate;

    FanIdResolverAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FanIdResolverAdapter(jdbcTemplate);
    }

    // ── findFanIdByOrderId ────────────────────────────────────────────────────

    @Test
    @DisplayName("findFanIdByOrderId — 주문 존재 시 Optional<fanId> 반환")
    void findFanIdByOrderId_exists_returnsOptionalWithFanId() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(42L));

        Optional<Long> result = adapter.findFanIdByOrderId(1L);

        assertTrue(result.isPresent());
        assertEquals(42L, result.get());
    }

    @Test
    @DisplayName("findFanIdByOrderId — 주문 없으면 Optional.empty()")
    void findFanIdByOrderId_notFound_returnsEmpty() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(99L)))
                .thenReturn(List.of());

        assertTrue(adapter.findFanIdByOrderId(99L).isEmpty());
    }

    // ── findFollowerFanIdsByArtistId ──────────────────────────────────────────

    @Test
    @DisplayName("findFollowerFanIdsByArtistId — 팔로워 목록 반환")
    void findFollowerFanIdsByArtistId_returnsAllFanIds() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L)))
                .thenReturn(List.of(10L, 20L, 30L));

        List<Long> result = adapter.findFollowerFanIdsByArtistId(5L);

        assertEquals(3, result.size());
        assertTrue(result.containsAll(List.of(10L, 20L, 30L)));
    }

    @Test
    @DisplayName("findFollowerFanIdsByArtistId — 팔로워 없으면 빈 리스트")
    void findFollowerFanIdsByArtistId_noFollowers_returnsEmptyList() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(7L)))
                .thenReturn(List.of());

        assertTrue(adapter.findFollowerFanIdsByArtistId(7L).isEmpty());
    }

    // ── findFanIdByCommentId ──────────────────────────────────────────────────

    @Test
    @DisplayName("findFanIdByCommentId — 댓글 존재 시 Optional<fanId> 반환")
    void findFanIdByCommentId_exists_returnsOptionalWithFanId() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(3L)))
                .thenReturn(List.of(55L));

        Optional<Long> result = adapter.findFanIdByCommentId(3L);

        assertTrue(result.isPresent());
        assertEquals(55L, result.get());
    }

    @Test
    @DisplayName("findFanIdByCommentId — 댓글 없으면 Optional.empty()")
    void findFanIdByCommentId_notFound_returnsEmpty() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(999L)))
                .thenReturn(List.of());

        assertTrue(adapter.findFanIdByCommentId(999L).isEmpty());
    }
}
