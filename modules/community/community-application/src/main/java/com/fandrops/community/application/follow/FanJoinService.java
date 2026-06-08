package com.fandrops.community.application.follow;

import com.fandrops.community.application.exception.AlreadyJoinedException;
import com.fandrops.community.application.exception.ArtistNotFoundException;
import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.follow.UserFollow;
import com.fandrops.community.domain.follow.repository.UserFollowRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;

@Service
public class FanJoinService {

    private final UserFollowRepository userFollowRepository;
    private final FanMembershipPort fanMembershipPort;
    private final ArtistProfilePort artistProfilePort;
    private final Clock clock;

    public FanJoinService(UserFollowRepository userFollowRepository,
                          FanMembershipPort fanMembershipPort,
                          ArtistProfilePort artistProfilePort,
                          Clock clock) {
        this.userFollowRepository = userFollowRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.artistProfilePort = artistProfilePort;
        this.clock = clock;
    }

    @Transactional
    public FanJoinResult join(Long artistId, Long fanId) {
        if (!artistProfilePort.exists(artistId)) {
            throw new ArtistNotFoundException("존재하지 않는 아티스트입니다.");
        }
        if (fanMembershipPort.isFanOf(fanId, artistId)) {
            throw new AlreadyJoinedException("이미 팬 가입한 아티스트입니다.");
        }
        try {
            UserFollow saved = userFollowRepository.save(UserFollow.create(fanId, artistId, clock));
            artistProfilePort.incrementFanCount(artistId);
            return new FanJoinResult(saved.getArtistId(), saved.getFanId(),
                    saved.getFollowedAt().atOffset(ZoneOffset.UTC));
        } catch (DataIntegrityViolationException e) {
            // isFanOf 체크와 INSERT 사이 동시 요청에 의한 UK(uq_user_follow) 충돌
            throw new AlreadyJoinedException("이미 팬 가입한 아티스트입니다.");
        }
    }

    @Transactional
    public void leave(Long artistId, Long fanId) {
        int deleted = userFollowRepository.deleteByFanIdAndArtistId(fanId, artistId);
        if (deleted > 0) {
            artistProfilePort.decrementFanCount(artistId);
        }
    }
}