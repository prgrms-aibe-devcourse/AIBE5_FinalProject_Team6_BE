package com.fandrops.order.api;

import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.api.dto.CreateStoreBannerRequest;
import com.fandrops.order.api.dto.UpdateStoreBannerRequest;
import com.fandrops.order.application.StoreBannerService;
import com.fandrops.order.application.dto.CreateStoreBannerCommand;
import com.fandrops.order.application.dto.StoreBannerResponse;
import com.fandrops.order.application.dto.UpdateStoreBannerCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreBannerController {

    private final StoreBannerService storeBannerService;

    public StoreBannerController(StoreBannerService storeBannerService) {
        this.storeBannerService = storeBannerService;
    }

    @GetMapping("/api/v1/store-banners")
    public ResponseEntity<ApiResponse<List<StoreBannerResponse>>> getActiveStoreBanners() {
        return ResponseEntity.ok(ApiResponse.ok(
                storeBannerService.getActiveStoreBanners(), traceId()));
    }

    @PostMapping("/api/v1/admin/store-banners")
    public ResponseEntity<ApiResponse<Map<String, Long>>> createStoreBanner(
            @Valid @RequestBody CreateStoreBannerRequest request) {
        Long bannerId = storeBannerService.createStoreBanner(new CreateStoreBannerCommand(
                request.getTitle(), request.getImageUrl(), request.getLandingUrl(),
                request.getExposureOrder(), request.getStartAt(), request.getEndAt(),
                request.getProductId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("bannerId", bannerId), traceId()));
    }

    @PatchMapping("/api/v1/admin/store-banners/{id}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> updateStoreBanner(
            @PathVariable("id") Long bannerId,
            @Valid @RequestBody UpdateStoreBannerRequest request) {
        storeBannerService.updateStoreBanner(new UpdateStoreBannerCommand(
                bannerId, request.getTitle(), request.getImageUrl(), request.getLandingUrl(),
                request.getExposureOrder(), request.getStartAt(), request.getEndAt(),
                request.getProductId()));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("bannerId", bannerId), traceId()));
    }

    @DeleteMapping("/api/v1/admin/store-banners/{id}")
    public ResponseEntity<Void> deleteStoreBanner(@PathVariable("id") Long bannerId) {
        storeBannerService.deleteStoreBanner(bannerId);
        return ResponseEntity.noContent().build();
    }

    private String traceId() {
        return MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
    }
}
