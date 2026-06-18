package com.fandrops.community.application.comment;

import com.fandrops.community.application.event.NewCommentEvent;
import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.application.port.OutboxEvent;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import com.fandrops.community.domain.feed.Comment;
import org.springframework.context.ApplicationEventPublisher;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final ArtistFeedRepository feedRepository;
    private final FanMembershipPort fanMembershipPort;
    private final OutboxEventPort outboxEventPort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public CommentService(CommentRepository commentRepository,
                          ArtistFeedRepository feedRepository,
                          FanMembershipPort fanMembershipPort,
                          OutboxEventPort outboxEventPort,
                          ApplicationEventPublisher applicationEventPublisher,
                          Clock clock) {
        this.commentRepository = commentRepository;
        this.feedRepository = feedRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.outboxEventPort = outboxEventPort;
        this.applicationEventPublisher = applicationEventPublisher;
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
        applicationEventPublisher.publishEvent(
                new NewCommentEvent(saved.getId(), saved.getFeedId(), saved.getParentId(), saved.getArtistId()));

        return toResult(saved);
    }

    @Transactional(readOnly = true)
    public CommentListResult getComments(Long feedId, String cursor, int size) {
        feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException("피드를 찾을 수 없습니다."));
        Long cursorId = null;
        if (cursor != null) {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다: " + cursor);
            }
        }
        List<Comment> topLevel = commentRepository.findTopLevelByFeedId(feedId, cursorId, size + 1);
        boolean hasMore = topLevel.size() > size;
        List<Comment> page = hasMore ? topLevel.subList(0, size) : topLevel;

        List<Long> parentIds = page.stream().map(Comment::getId).toList();
        Map<Long, List<CommentResult>> repliesByParentId = commentRepository.findRepliesByParentIds(parentIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Comment::getParentId,
                        Collectors.mapping(this::toResult, Collectors.toList())));

        List<CommentWithRepliesResult> items = page.stream()
                .map(c -> new CommentWithRepliesResult(
                        toResult(c),
                        repliesByParentId.getOrDefault(c.getId(), List.of())))
                .toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CommentListResult(items, nextCursor, hasMore);
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