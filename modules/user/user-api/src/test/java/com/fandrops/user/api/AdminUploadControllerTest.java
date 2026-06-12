package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlResponse;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUploadControllerTest {

    @Mock UploadService uploadService;
    @Mock Environment environment;

    AdminUploadController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUploadController(uploadService, environment);
    }

    @Test
    @DisplayName("유효한 contentType이면 200과 presignedUrl/imageUrl을 반환한다")
    void generatePresignedUrl_validType_returns200() {
        PresignedUploadResult result = new PresignedUploadResult(
                "https://s3.amazonaws.com/presigned?X-Amz-Signature=abc",
                "https://s3.amazonaws.com/uploads/banners/uuid.jpg"
        );
        when(uploadService.requestPresignedUrl("image/jpeg")).thenReturn(result);

        ResponseEntity<ApiResponse<UploadPresignedUrlResponse>> response =
                controller.generatePresignedUrl(new UploadPresignedUrlRequest("image/jpeg"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UploadPresignedUrlResponse body = response.getBody().data();
        assertEquals(result.presignedUrl(), body.presignedUrl());
        assertEquals(result.imageUrl(), body.imageUrl());
    }

    @Test
    @DisplayName("허용되지 않은 contentType이면 InvalidContentTypeException을 던진다")
    void generatePresignedUrl_invalidType_throwsException() {
        when(uploadService.requestPresignedUrl("image/gif"))
                .thenThrow(new InvalidContentTypeException("image/gif"));

        assertThrows(InvalidContentTypeException.class,
                () -> controller.generatePresignedUrl(new UploadPresignedUrlRequest("image/gif")));
    }
}
