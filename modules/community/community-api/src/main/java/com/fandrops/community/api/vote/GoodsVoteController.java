package com.fandrops.community.api.vote;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.vote.GoodsBallotCommand;
import com.fandrops.community.application.vote.GoodsBallotResult;
import com.fandrops.community.application.vote.GoodsVoteCloseResult;
import com.fandrops.community.application.vote.GoodsVoteCreateCommand;
import com.fandrops.community.application.vote.GoodsVoteResult;
import com.fandrops.community.application.vote.GoodsVoteService;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class GoodsVoteController extends CommunityControllerSupport {

    private final GoodsVoteService goodsVoteService;

    public GoodsVoteController(GoodsVoteService goodsVoteService, Environment environment) {
        super(environment);
        this.goodsVoteService = goodsVoteService;
    }

    // GET /api/v1/artists/{artistId}/goods-votes — 굿즈 투표 목록 (F03-08, 공개 조회)
    // 의도적 익명 허용 — 목록 조회는 공개 (mvp-api-spec.md)
    @GetMapping("/api/v1/artists/{artistId}/goods-votes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVotes(
            @PathVariable Long artistId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("size는 1~50 사이여야 합니다.");
        }
        List<GoodsVoteResult> raw = goodsVoteService.getVotes(artistId, cursor, size);
        boolean hasMore = raw.size() > size;
        List<GoodsVoteResult> items = hasMore ? raw.subList(0, size) : raw;
        Long nextCursor = hasMore ? items.get(items.size() - 1).id() : null;

        // Map.of()는 null 값 불허 → HashMap 사용 (api-contract.md: nextCursor는 null 허용)
        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("nextCursor", nextCursor != null ? String.valueOf(nextCursor) : null);
        data.put("hasMore", hasMore);
        return ResponseEntity.ok(ApiResponse.ok(data, traceId()));
    }

    // POST /api/v1/artists/{artistId}/goods-votes — 굿즈 투표 생성 (AGENCY 운영 계정 전용)
    @PostMapping("/api/v1/artists/{artistId}/goods-votes")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createVote(
            @PathVariable Long artistId,
            @Valid @RequestBody GoodsVoteCreateRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        if (!isLocalProfile() && (authentication == null || !hasArtistOrAgencyRole(authentication))) {
            throw new ForbiddenException("AGENCY 권한이 필요합니다.");
        }
        List<GoodsVoteCreateCommand.OptionInput> options = request.options().stream()
                .map(o -> new GoodsVoteCreateCommand.OptionInput(o.label(), o.imageUrl()))
                .toList();
        Long voteId = goodsVoteService.createVote(
                new GoodsVoteCreateCommand(artistId, request.title(), request.endsAt(), options));
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("voteId", voteId), traceId()));
    }

    // PATCH /api/v1/goods-votes/{voteId}/close — 굿즈 투표 강제 종료 (AGENCY 운영 계정 전용)
    @PatchMapping("/api/v1/goods-votes/{voteId}/close")
    public ResponseEntity<ApiResponse<GoodsVoteCloseResult>> closeVote(
            @PathVariable Long voteId,
            Authentication authentication,
            @RequestHeader(value = "X-Agency-Account-Id", required = false) Long agencyAccountIdHeader) {

        if (!isLocalProfile() && (authentication == null || !hasAgencyRole(authentication))) {
            throw new ForbiddenException("AGENCY 권한이 필요합니다.");
        }
        Long agencyAccountId = resolveAgencyAccountId(authentication, agencyAccountIdHeader);
        GoodsVoteCloseResult result = goodsVoteService.closeVote(voteId, agencyAccountId);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

    // POST /api/v1/goods-votes/{id}/ballots — 투표 참여 (팬 가입자 전용, 1인 1표)
    @PostMapping("/api/v1/goods-votes/{id}/ballots")
    public ResponseEntity<ApiResponse<Map<String, Long>>> castBallot(
            @PathVariable Long id,
            @Valid @RequestBody GoodsBallotRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        GoodsBallotResult result = goodsVoteService.castBallot(
                new GoodsBallotCommand(id, request.optionId(), fanId));
        return ResponseEntity.status(201).body(ApiResponse.ok(Map.of("recordId", result.recordId()), traceId()));
    }
}
