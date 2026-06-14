package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock S3PresignedUrlPort s3PresignedUrlPort;
    @Mock AuditLogPort auditLogPort;
    @InjectMocks UploadService uploadService;

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    @DisplayName("허용된 contentType이면 S3 포트를 호출하고 결과를 반환한다")
    void requestPresignedUrl_allowedType_callsPort(String contentType) {
        PresignedUploadResult expected = new PresignedUploadResult(
                "https://presigned", "https://image", Instant.now().plusSeconds(600));
        when(s3PresignedUrlPort.generate(contentType, 1024L)).thenReturn(expected);

        PresignedUploadResult result = uploadService.requestPresignedUrl(
                contentType, 1024L, 1L, "127.0.0.1", "trace-id");

        verify(s3PresignedUrlPort).generate(contentType, 1024L);
        assertEquals(expected.presignedUrl(), result.presignedUrl());
        assertEquals(expected.imageUrl(), result.imageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain", ""})
    @DisplayName("허용되지 않은 contentType이면 InvalidContentTypeException을 던진다")
    void requestPresignedUrl_disallowedType_throwsException(String contentType) {
        assertThrows(InvalidContentTypeException.class,
                () -> uploadService.requestPresignedUrl(contentType, 1024L, 1L, "127.0.0.1", "trace-id"));
    }

    @Test
    @DisplayName("포트가 반환한 presignedUrl과 imageUrl이 그대로 전달된다")
    void requestPresignedUrl_returnsPortResult() {
        PresignedUploadResult expected = new PresignedUploadResult(
                "https://s3.amazonaws.com/presigned?X-Amz-Signature=abc",
                "https://s3.amazonaws.com/uploads/banners/uuid.jpg",
                Instant.now().plusSeconds(600));
        when(s3PresignedUrlPort.generate("image/jpeg", 1024L)).thenReturn(expected);

        PresignedUploadResult result = uploadService.requestPresignedUrl(
                "image/jpeg", 1024L, 1L, "127.0.0.1", "trace-id");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("허용된 contentType이면 audit 로그를 저장한다")
    void requestPresignedUrl_allowedType_savesAuditLog() {
        PresignedUploadResult result = new PresignedUploadResult(
                "https://presigned", "https://image", Instant.now().plusSeconds(600));
        when(s3PresignedUrlPort.generate("image/jpeg", 1024L)).thenReturn(result);

        uploadService.requestPresignedUrl("image/jpeg", 1024L, 1L, "127.0.0.1", "trace-id");

        verify(auditLogPort).save(any());
    }
}
