package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.community.application.port.ArtistSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ArtistProfileAdapter implements ArtistProfilePort {

    private static final Logger log = LoggerFactory.getLogger(ArtistProfileAdapter.class);

    private final JdbcTemplate jdbc;

    public ArtistProfileAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(Long artistId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM artist_profile WHERE id = ?",
                Integer.class, artistId);
        return count != null && count > 0;
    }

    @Override
    public void incrementFanCount(Long artistId) {
        int rows = jdbc.update("UPDATE artist_profile SET fan_count = fan_count + 1 WHERE id = ?", artistId);
        if (rows == 0) {
            log.warn("[FAN_COUNT] artist_profile not found artistId={}", artistId);
        }
    }

    @Override
    public void decrementFanCount(Long artistId) {
        int rows = jdbc.update(
                "UPDATE artist_profile SET fan_count = GREATEST(fan_count - 1, 0) WHERE id = ?",
                artistId);
        if (rows == 0) {
            log.warn("[FAN_COUNT] artist_profile not found artistId={}", artistId);
        }
    }

    @Override
    public void activate(Long artistId) {
        // no-op: active 컬럼 없음 — 향후 artist_profile.is_active 컬럼 추가 시 구현
    }

    @Override
    public Optional<ArtistSummary> findById(Long artistId) {
        List<ArtistSummary> results = jdbc.query(
                "SELECT id, name, profile_image_url FROM artist_profile WHERE id = ?",
                (rs, rowNum) -> new ArtistSummary(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("profile_image_url")),
                artistId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Map<Long, ArtistSummary> findAllByIds(Collection<Long> artistIds) {
        if (artistIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = artistIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT id, name, profile_image_url FROM artist_profile WHERE id IN (" + placeholders + ")";
        List<ArtistSummary> summaries = jdbc.query(
                sql,
                (rs, rowNum) -> new ArtistSummary(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("profile_image_url")),
                artistIds.toArray());
        return summaries.stream().collect(Collectors.toMap(ArtistSummary::artistId, s -> s));
    }
}
