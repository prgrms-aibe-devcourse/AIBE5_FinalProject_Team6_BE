package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlResponse;
import com.fandrops.user.application.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
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
     * 응답의 presignedUrl로 클라이언트가 직접 S3에 PUT 요청하고,
     * imageUrl을 배너 생성 API의 imageUrl 필드에 사용한다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UploadPresignedUrlResponse>> generatePresignedUrl(
            @Valid @RequestBody UploadPresignedUrlRequest request) {
        UploadPresignedUrlResponse response = UploadPresignedUrlResponse.from(
                uploadService.requestPresignedUrl(request.contentType())
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId()));
    }
}
