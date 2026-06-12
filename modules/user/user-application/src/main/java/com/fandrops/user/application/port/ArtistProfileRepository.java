package com.fandrops.user.application.port;

import com.fandrops.user.application.dto.ArtistSummary;
import com.fandrops.user.domain.ArtistProfile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ArtistProfileRepository {
    ArtistProfile save(ArtistProfile profile);
    Optional<ArtistProfile> findById(Long artistId);
    List<ArtistProfile> findAllOrderByFanCountDesc(Long cursorId, int size);
    List<ArtistSummary> findAllByIds(Set<Long> artistIds);
}
