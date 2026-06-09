package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.BannerResponse;
import com.fandrops.user.application.service.BannerService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
public class BannerController extends UserControllerSupport {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService, Environment environment) {
        super(environment);
        this.bannerService = bannerService;
    }

    /** GET /banners/main — 활성 메인 배너 조회 (비인증 허용) */
    @GetMapping("/main")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getMainBanners() {
        List<BannerResponse> items = bannerService.getActiveMainBanners()
                .stream().map(BannerResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(items, traceId()));
    }
}