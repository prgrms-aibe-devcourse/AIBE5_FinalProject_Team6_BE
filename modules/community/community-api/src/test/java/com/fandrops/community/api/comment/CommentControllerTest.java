package com.fandrops.community.api.comment;

import com.fandrops.community.application.comment.CommentListResult;
import com.fandrops.community.application.comment.CommentResult;
import com.fandrops.community.application.comment.CommentService;
import com.fandrops.community.application.comment.CommentWithRepliesResult;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock CommentService commentService;
    @Mock Environment environment;

    CommentController controller;

    @BeforeEach
    void setUp() {
        controller = new CommentController(commentService, environment);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("POST /api/v1/feeds/{feedId}/comments")
    class CreateCommentTest {

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 201, commentId 반환")
        void localProfile_fanHeader_returns201() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            CommentResult stub = new CommentResult(42L, 1L, 55L, null, null, "좋아요", OffsetDateTime.now(ZoneOffset.UTC));
            when(commentService.createComment(any())).thenReturn(stub);

            ResponseEntity<?> response = controller.createComment(
                    1L, 10L,
                    new CommentCreateRequest("좋아요", null),
                    null, 55L, null);

            assertEquals(201, response.getStatusCode().value());
            verify(commentService).createComment(any());
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → 201, commentId 반환")
        void fanJwt_nonLocal_returns201() {
            Authentication auth = mockAuth("77", "FAN");
            CommentResult stub = new CommentResult(43L, 1L, 77L, null, null, "응원해요", OffsetDateTime.now(ZoneOffset.UTC));
            when(commentService.createComment(any())).thenReturn(stub);

            ResponseEntity<?> response = controller.createComment(
                    1L, 10L,
                    new CommentCreateRequest("응원해요", null),
                    auth, null, null);

            assertEquals(201, response.getStatusCode().value());
            verify(commentService).createComment(any());
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException, service 미호출")
        void noAuth_nonLocal_throwsUnauthorized() {
            assertThrows(UnauthorizedException.class,
                    () -> controller.createComment(
                            1L, 10L,
                            new CommentCreateRequest("내용", null),
                            null, null, null));
            verify(commentService, never()).createComment(any());
        }

        @Test
        @DisplayName("존재하지 않는 feedId → FeedNotFoundException 전파")
        void invalidFeedId_propagatesFeedNotFound() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(commentService.createComment(any()))
                    .thenThrow(new FeedNotFoundException("피드를 찾을 수 없습니다."));

            assertThrows(FeedNotFoundException.class,
                    () -> controller.createComment(
                            999L, 10L,
                            new CommentCreateRequest("내용", null),
                            null, 55L, null));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/feeds/{feedId}/comments")
    class GetCommentsTest {

        @Test
        @DisplayName("공개 조회 → 200, 커서 페이징 결과 반환")
        void public_returnsCommentList() {
            CommentListResult stub = new CommentListResult(List.of(), null, false);
            when(commentService.getComments(eq(1L), isNull(), eq(20))).thenReturn(stub);

            ResponseEntity<?> response = controller.getComments(1L, null, 20);

            assertEquals(200, response.getStatusCode().value());
            verify(commentService).getComments(eq(1L), isNull(), eq(20));
        }

        @Test
        @DisplayName("커서 + size 지정 → service에 그대로 전달")
        void withCursorAndSize_passesToService() {
            CommentWithRepliesResult item = new CommentWithRepliesResult(
                    new CommentResult(1L, 2L, 3L, null, null, "댓글", OffsetDateTime.now(ZoneOffset.UTC)),
                    List.of());
            CommentListResult stub = new CommentListResult(List.of(item), "cursor-next", true);
            when(commentService.getComments(eq(2L), eq("abc"), eq(10))).thenReturn(stub);

            ResponseEntity<?> response = controller.getComments(2L, "abc", 10);

            assertEquals(200, response.getStatusCode().value());
            verify(commentService).getComments(eq(2L), eq("abc"), eq(10));
        }

        @Test
        @DisplayName("존재하지 않는 feedId → FeedNotFoundException 전파")
        void invalidFeedId_propagates() {
            when(commentService.getComments(eq(999L), isNull(), eq(20)))
                    .thenThrow(new FeedNotFoundException("피드를 찾을 수 없습니다."));

            assertThrows(FeedNotFoundException.class,
                    () -> controller.getComments(999L, null, 20));
        }
    }

    private static Authentication mockAuth(String name, String authority) {
        return new Authentication() {
            @Override public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority(authority));
            }
            @Override public Object getCredentials() { return null; }
            @Override public Object getDetails() { return null; }
            @Override public Object getPrincipal() { return name; }
            @Override public boolean isAuthenticated() { return true; }
            @Override public void setAuthenticated(boolean b) {}
            @Override public String getName() { return name; }
        };
    }
}