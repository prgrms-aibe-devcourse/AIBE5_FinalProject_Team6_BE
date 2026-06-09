package com.fandrops.community.application.mypage;

import com.fandrops.community.domain.feed.Comment;
import com.fandrops.community.domain.feed.FeedLike;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import com.fandrops.community.domain.feed.repository.FeedLikeRepository;
import com.fandrops.community.domain.follow.UserFollow;
import com.fandrops.community.domain.follow.repository.UserFollowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class FanActivityService {

    private final CommentRepository commentRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final UserFollowRepository userFollowRepository;

    public FanActivityService(CommentRepository commentRepository,
                               FeedLikeRepository feedLikeRepository,
                               UserFollowRepository userFollowRepository) {
        this.commentRepository = commentRepository;
        this.feedLikeRepository = feedLikeRepository;
        this.userFollowRepository = userFollowRepository;
    }

    public ActivityListResult getActivities(Long fanId, String cursor, int size) {
        Long commentCursor = null;
        Long likeCursor = null;
        if (cursor != null) {
            Long[] decoded = decodeCursor(cursor);
            commentCursor = decoded[0];
            likeCursor = decoded[1];
        }

        List<Comment> comments = commentRepository.findByFanId(fanId, commentCursor, size + 1);
        List<FeedLike> likes = feedLikeRepository.findByFanId(fanId, likeCursor, size + 1);

        List<ActivityItem> allItems = new ArrayList<>(comments.size() + likes.size());
        for (Comment c : comments) {
            allItems.add(new ActivityItem(ActivityType.COMMENT, c.getId(), c.getFeedId(),
                    c.getArtistId(), c.getContent(), c.getCreatedAt().atOffset(ZoneOffset.UTC)));
        }
        for (FeedLike l : likes) {
            allItems.add(new ActivityItem(ActivityType.FEED_LIKE, l.getId(), l.getFeedId(),
                    l.getArtistId(), null, l.getCreatedAt().atOffset(ZoneOffset.UTC)));
        }

        allItems.sort(Comparator.comparing(ActivityItem::createdAt).reversed());

        boolean hasMore = allItems.size() > size;
        List<ActivityItem> page = allItems.subList(0, Math.min(size, allItems.size()));

        Long nextCommentCursor = lastIdInPage(page, ActivityType.COMMENT, commentCursor);
        Long nextLikeCursor = lastIdInPage(page, ActivityType.FEED_LIKE, likeCursor);

        String nextCursor = hasMore ? encodeCursor(nextCommentCursor, nextLikeCursor) : null;
        return new ActivityListResult(List.copyOf(page), nextCursor, hasMore);
    }

    public JoinedArtistListResult getJoinedArtists(Long fanId, String cursor, int size) {
        Long cursorId = null;
        if (cursor != null) {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
            }
        }

        List<UserFollow> follows = userFollowRepository.findByFanId(fanId, cursorId, size + 1);
        boolean hasMore = follows.size() > size;
        List<UserFollow> page = hasMore ? follows.subList(0, size) : follows;

        List<JoinedArtistResult> items = page.stream()
                .map(f -> new JoinedArtistResult(f.getArtistId(),
                        f.getFollowedAt().atOffset(ZoneOffset.UTC)))
                .toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new JoinedArtistListResult(items, nextCursor, hasMore);
    }

    // page를 DESC(newest first)로 순회, 해당 type의 마지막(= 가장 오래된) id 반환
    private static Long lastIdInPage(List<ActivityItem> page, ActivityType type, Long inputCursor) {
        for (int i = page.size() - 1; i >= 0; i--) {
            if (page.get(i).type() == type) {
                return page.get(i).id();
            }
        }
        return inputCursor;
    }

    private static String encodeCursor(Long commentCursor, Long likeCursor) {
        String json = "{\"c\":" + (commentCursor != null ? commentCursor : "null")
                + ",\"l\":" + (likeCursor != null ? likeCursor : "null") + "}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Long[] decodeCursor(String cursor) {
        try {
            String json = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            return new Long[]{extractLong(json, "c"), extractLong(json, "l")};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
        }
    }

    private static Long extractLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
        int start = idx + pattern.length();
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        if (end < 0) throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
        String val = json.substring(start, end).trim();
        return "null".equals(val) ? null : Long.parseLong(val);
    }
}