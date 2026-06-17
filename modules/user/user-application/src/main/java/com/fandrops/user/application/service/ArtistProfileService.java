package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.domain.ArtistProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ArtistProfileService {

    private final ArtistProfileRepository artistProfileRepository;

    public ArtistProfileService(ArtistProfileRepository artistProfileRepository) {
        this.artistProfileRepository = artistProfileRepository;
    }

    public ArtistProfile getArtistProfile(Long artistId) {
        return artistProfileRepository.findById(artistId)
                .orElseThrow(() -> new ArtistNotFoundException("아티스트를 찾을 수 없습니다."));
    }

    public ArtistProfileListResult listArtistProfiles(String cursor, int size) {
        Long cursorId = cursor != null ? Long.valueOf(cursor) : null;
        List<ArtistProfile> raw = artistProfileRepository.findAllOrderByFanCountDesc(cursorId, size + 1);
        boolean hasMore = raw.size() > size;
        List<ArtistProfile> page = hasMore ? raw.subList(0, size) : raw;
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new ArtistProfileListResult(page, nextCursor, hasMore);
    }
}
