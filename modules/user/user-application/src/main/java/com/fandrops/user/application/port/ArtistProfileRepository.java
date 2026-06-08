package com.fandrops.user.application.port;

import com.fandrops.user.domain.ArtistProfile;

public interface ArtistProfileRepository {
    ArtistProfile save(ArtistProfile profile);
}
