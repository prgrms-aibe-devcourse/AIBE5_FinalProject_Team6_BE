package com.fandrops.user.application.port;

import com.fandrops.user.domain.ArtistProfile;

import java.util.List;
import java.util.Optional;

public interface ArtistProfileRepository {
    ArtistProfile save(ArtistProfile profile);
    Optional<ArtistProfile> findById(Long artistId);
    List<ArtistProfile> findAllOrderByFanCountDesc(Long cursorId, int size);
}
