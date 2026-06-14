package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlResponse;
import com.fandrops.user.application.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/uploads")
public class AdminUploadController extends UserControllerSupport {

    private final UploadService uploadService;

    public AdminUploadController(UploadService uploadService, Environment environment) {
        super(environment);
        this.uploadService = uploadService;
    }

    /**
     * S3 Presigned PUT URL 발급.
     * ⚠️ presignedUrl로 S3 PUT이 완료된 이후에만 imageUrl을 배너 등록 API에 사용할 것.
     * PUT 미완료 시 배너 등록(POST /admin/main-banners) 호출 시 400 반환.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<UploadPresignedUrlResponse>> generatePresignedUrl(
            @Valid @RequestBody UploadPresignedUrlRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        UploadPresignedUrlResponse response = UploadPresignedUrlResponse.from(
                uploadService.requestPresignedUrl(
                        request.contentType(),
                        request.contentLength(),
                        resolveAdminId(authentication),
                        resolveClientIp(httpRequest),
                        traceId()
                )
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId()));
    }
}