package com.fandrops.user.application.port;

import com.fandrops.user.domain.ArtistMember;

import java.util.Optional;

public interface ArtistMemberRepository {
    ArtistMember save(ArtistMember member);
    Optional<ArtistMember> findById(Long id);
    Optional<ArtistMember> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
