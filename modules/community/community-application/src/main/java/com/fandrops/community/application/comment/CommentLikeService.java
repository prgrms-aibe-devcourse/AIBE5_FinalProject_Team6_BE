package com.fandrops.community.application.comment;

import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.feed.CommentLike;
import com.fandrops.community.domain.feed.repository.CommentLikeRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class CommentLikeService {

    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final FanMembershipPort fanMembershipPort;
    private final Clock clock;

    public CommentLikeService(CommentLikeRepository commentLikeRepository,
                               CommentRepository commentRepository,
                               FanMembershipPort fanMembershipPort,
                               Clock clock) {
        this.commentLikeRepository = commentLikeRepository;
        this.commentRepository = commentRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.clock = clock;
    }

    public void likeComment(Long commentId, Long fanId) {
        var comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("댓글을 찾을 수 없습니다."));

        if (!fanMembershipPort.isFanOf(fanId, comment.getArtistId())) {
            throw new NotFanMemberException("팬 가입 후 좋아요를 누를 수 있습니다.");
        }
        if (commentLikeRepository.existsByCommentIdAndFanId(commentId, fanId)) {
            throw new AlreadyLikedException("이미 좋아요를 눌렀습니다.");
        }
        commentLikeRepository.save(CommentLike.create(commentId, fanId, clock));
    }

    public void unlikeComment(Long commentId, Long fanId) {
        CommentLike like = commentLikeRepository.findByCommentIdAndFanId(commentId, fanId)
                .orElseThrow(() -> new LikeNotFoundException("좋아요 기록을 찾을 수 없습니다."));
        commentLikeRepository.delete(like);
    }
}