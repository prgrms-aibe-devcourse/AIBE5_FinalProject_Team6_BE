package com.fandrops.community.application.comment;

import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.application.port.OutboxEvent;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import com.fandrops.community.domain.feed.Comment;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final ArtistFeedRepository feedRepository;
    private final FanMembershipPort fanMembershipPort;
    private final OutboxEventPort outboxEventPort;
    private final Clock clock;

    public CommentService(CommentRepository commentRepository,
                          ArtistFeedRepository feedRepository,
                          FanMembershipPort fanMembershipPort,
                          OutboxEventPort outboxEventPort,
                          Clock clock) {
        this.commentRepository = commentRepository;
        this.feedRepository = feedRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.outboxEventPort = outboxEventPort;
        this.clock = clock;
    }

    public CommentResult createComment(CommentCreateCommand command) {
        feedRepository.findById(command.feedId())
                .orElseThrow(() -> new FeedNotFoundException("피드를 찾을 수 없습니다."));

        if (command.fanId() != null && !fanMembershipPort.isFanOf(command.fanId(), command.artistId())) {
            throw new NotFanMemberException("팬 가입 후 댓글을 작성할 수 있습니다.");
        }

        // parentId 검증: 존재 여부 + 동일 feedId
        if (command.parentId() != null) {
            Comment parent = commentRepository.findById(command.parentId())
                    .orElseThrow(() -> new CommentNotFoundException("부모 댓글을 찾을 수 없습니다."));
            if (!parent.getFeedId().equals(command.feedId())) {
                throw new IllegalArgumentException("답글의 feedId가 부모 댓글의 feedId와 다릅니다.");
            }
        }

        Comment comment = command.fanId() != null
                ? Comment.createByFan(command.feedId(), command.artistId(),
                        command.fanId(), command.parentId(), command.content(), clock)
                : Comment.createByArtistMember(command.feedId(), command.artistId(),
                        command.artistMemberId(), command.parentId(), command.content(), clock);

        Comment saved = commentRepository.save(comment);
        feedRepository.incrementCommentCount(command.feedId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", saved.getId());
        payload.put("feedId", saved.getFeedId());
        payload.put("artistId", saved.getArtistId());
        payload.put("parentId", saved.getParentId());
        payload.put("fanId", saved.getFanId());
        payload.put("artistMemberId", saved.getArtistMemberId());
        outboxEventPort.publish(new OutboxEvent(OutboxEventType.NEW_COMMENT, saved.getId(), payload));

        return toResult(saved);
    }

    private CommentResult toResult(Comment comment) {
        return new CommentResult(
                comment.getId(),
                comment.getFeedId(),
                comment.getFanId(),
                comment.getArtistMemberId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }
}