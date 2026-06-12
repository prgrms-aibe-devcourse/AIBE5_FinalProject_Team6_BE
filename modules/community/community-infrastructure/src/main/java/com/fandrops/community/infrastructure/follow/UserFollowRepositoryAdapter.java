package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.domain.follow.UserFollow;
import com.fandrops.community.domain.follow.repository.UserFollowRepository;
import com.fandrops.community.infrastructure.follow.jpa.UserFollowJpaEntity;
import com.fandrops.community.infrastructure.follow.jpa.UserFollowJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserFollowRepositoryAdapter implements UserFollowRepository {

    private final UserFollowJpaRepository jpaRepository;

    public UserFollowRepositoryAdapter(UserFollowJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserFollow save(UserFollow follow) {
        UserFollowJpaEntity entity = new UserFollowJpaEntity(
                follow.getId(), follow.getFanId(), follow.getArtistId(), follow.getFollowedAt());
        UserFollowJpaEntity saved = jpaRepository.save(entity);
        return UserFollow.reconstruct(saved.getId(), saved.getFanId(), saved.getArtistId(), saved.getFollowedAt());
    }

    @Override
    public int deleteByFanIdAndArtistId(Long fanId, Long artistId) {
        return jpaRepository.deleteByFanIdAndArtistId(fanId, artistId);
    }

    @Override
    public List<UserFollow> findByFanId(Long fanId, Long cursorId, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<UserFollowJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByFanIdOrderByIdDesc(fanId, page)
                : jpaRepository.findByFanIdAndIdLessThanOrderByIdDesc(fanId, cursorId, page);
        return entities.stream()
                .map(e -> UserFollow.reconstruct(e.getId(), e.getFanId(), e.getArtistId(), e.getFollowedAt()))
                .toList();
    }
}