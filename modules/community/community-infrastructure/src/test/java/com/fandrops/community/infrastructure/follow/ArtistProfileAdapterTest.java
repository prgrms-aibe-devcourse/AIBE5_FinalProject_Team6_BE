package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.application.port.ArtistSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistProfileAdapterTest {

    @Mock JdbcTemplate jdbc;

    ArtistProfileAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ArtistProfileAdapter(jdbc);
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("exists: DB에 행 있음 → true 반환")
    void exists_rowFound_returnsTrue() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);

        assertTrue(adapter.exists(1L));
    }

    @Test
    @DisplayName("exists: DB에 행 없음 → false 반환")
    void exists_rowNotFound_returnsFalse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(99L))).thenReturn(0);

        assertFalse(adapter.exists(99L));
    }

    @Test
    @DisplayName("exists: queryForObject null 반환 → false 반환")
    void exists_queryReturnsNull_returnsFalse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(null);

        assertFalse(adapter.exists(1L));
    }

    // ── incrementFanCount ─────────────────────────────────────────────────────

    @Test
    @DisplayName("incrementFanCount: UPDATE 쿼리에 fan_count + 1 포함")
    void incrementFanCount_executesCorrectSql() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbc.update(sqlCaptor.capture(), eq(5L))).thenReturn(1);

        adapter.incrementFanCount(5L);

        assertTrue(sqlCaptor.getValue().contains("fan_count + 1"));
    }

    // ── decrementFanCount ─────────────────────────────────────────────────────

    @Test
    @DisplayName("decrementFanCount: UPDATE 쿼리에 GREATEST 포함 (음수 방지)")
    void decrementFanCount_executesCorrectSqlWithGreatest() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbc.update(sqlCaptor.capture(), eq(5L))).thenReturn(1);

        adapter.decrementFanCount(5L);

        assertTrue(sqlCaptor.getValue().toUpperCase().contains("GREATEST"));
    }

    @Test
    @DisplayName("incrementFanCount: 0 rows affected → 예외 없이 warn 로그만 (정합성 감지)")
    void incrementFanCount_zeroRowsAffected_doesNotThrow() {
        when(jdbc.update(anyString(), eq(999L))).thenReturn(0);

        assertDoesNotThrow(() -> adapter.incrementFanCount(999L));
    }

    @Test
    @DisplayName("decrementFanCount: 0 rows affected → 예외 없이 warn 로그만 (정합성 감지)")
    void decrementFanCount_zeroRowsAffected_doesNotThrow() {
        when(jdbc.update(anyString(), eq(999L))).thenReturn(0);

        assertDoesNotThrow(() -> adapter.decrementFanCount(999L));
    }

    // ── activate ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("activate: no-op → jdbc 호출 없음")
    void activate_noOp_doesNotCallJdbc() {
        adapter.activate(1L);

        verifyNoInteractions(jdbc);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById: 행 있음 → Optional.of(ArtistSummary) 반환")
    @SuppressWarnings("unchecked")
    void findById_rowFound_returnsOptionalWithSummary() {
        ArtistSummary summary = new ArtistSummary(1L, "NewJeans", "https://img.url");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(summary));

        Optional<ArtistSummary> result = adapter.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().artistId());
        assertEquals("NewJeans", result.get().name());
    }

    @Test
    @DisplayName("findById: 행 없음 → Optional.empty() 반환")
    @SuppressWarnings("unchecked")
    void findById_rowNotFound_returnsEmpty() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(99L)))
                .thenReturn(List.of());

        Optional<ArtistSummary> result = adapter.findById(99L);

        assertTrue(result.isEmpty());
    }

    // ── findAllByIds ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAllByIds: 빈 컬렉션 → 빈 맵 반환 (jdbc 호출 없음)")
    void findAllByIds_emptyCollection_returnsEmptyMap() {
        Map<Long, ArtistSummary> result = adapter.findAllByIds(List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("findAllByIds: 여러 id → 결과 맵 반환")
    @SuppressWarnings("unchecked")
    void findAllByIds_multipleIds_returnsPopulatedMap() {
        ArtistSummary s1 = new ArtistSummary(1L, "NewJeans", "https://img1.url");
        ArtistSummary s2 = new ArtistSummary(2L, "aespa", "https://img2.url");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(s1, s2));

        Map<Long, ArtistSummary> result = adapter.findAllByIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("NewJeans", result.get(1L).name());
        assertEquals("aespa", result.get(2L).name());
    }
}