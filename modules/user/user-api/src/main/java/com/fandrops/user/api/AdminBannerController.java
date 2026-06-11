package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.BannerResponse;
import com.fandrops.user.api.dto.CreateBannerRequest;
import com.fandrops.user.api.dto.UpdateBannerRequest;
import com.fandrops.user.application.dto.CreateBannerCommand;
import com.fandrops.user.application.dto.UpdateBannerCommand;
import com.fandrops.user.application.service.BannerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/main-banners")
public class AdminBannerController extends UserControllerSupport {

    private final BannerService bannerService;

    public AdminBannerController(BannerService bannerService, Environment environment) {
        super(environment);
        this.bannerService = bannerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> list() {
        List<BannerResponse> items = bannerService.getAllMainBanners()
                .stream().map(BannerResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(items, traceId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BannerResponse>> create(
            @Valid @RequestBody CreateBannerRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        BannerResponse response = BannerResponse.from(bannerService.createBanner(
                new CreateBannerCommand(
                        request.title(), request.imageUrl(), request.landingUrl(),
                        request.exposureOrder(), request.startAt(), request.endAt()
                ),
                resolveAdminId(authentication),
                httpRequest.getRemoteAddr()
        ));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, traceId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBannerRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        BannerResponse response = BannerResponse.from(bannerService.updateBanner(id,
                new UpdateBannerCommand(
                        request.title(), request.imageUrl(), request.landingUrl(),
                        request.exposureOrder(), request.isActive(),
                        request.startAt(), request.endAt()
                ),
                resolveAdminId(authentication),
                httpRequest.getRemoteAddr()
        ));
        return ResponseEntity.ok(ApiResponse.ok(response, traceId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        bannerService.deleteBanner(id, resolveAdminId(authentication), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}