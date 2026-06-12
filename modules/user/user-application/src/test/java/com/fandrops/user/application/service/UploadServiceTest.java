package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock S3PresignedUrlPort s3PresignedUrlPort;
    @InjectMocks UploadService uploadService;

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    @DisplayName("허용된 contentType이면 S3 포트를 호출하고 결과를 반환한다")
    void requestPresignedUrl_allowedType_callsPort(String contentType) {
        PresignedUploadResult expected = new PresignedUploadResult("https://presigned", "https://image");
        when(s3PresignedUrlPort.generate(contentType)).thenReturn(expected);

        PresignedUploadResult result = uploadService.requestPresignedUrl(contentType);

        verify(s3PresignedUrlPort).generate(contentType);
        assertEquals(expected.presignedUrl(), result.presignedUrl());
        assertEquals(expected.imageUrl(), result.imageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain", ""})
    @DisplayName("허용되지 않은 contentType이면 InvalidContentTypeException을 던진다")
    void requestPresignedUrl_disallowedType_throwsException(String contentType) {
        assertThrows(InvalidContentTypeException.class,
                () -> uploadService.requestPresignedUrl(contentType));
    }

    @Test
    @DisplayName("포트가 반환한 presignedUrl과 imageUrl이 그대로 전달된다")
    void requestPresignedUrl_returnsPortResult() {
        PresignedUploadResult expected = new PresignedUploadResult(
                "https://s3.amazonaws.com/presigned?X-Amz-Signature=abc",
                "https://s3.amazonaws.com/uploads/banners/uuid.jpg"
        );
        when(s3PresignedUrlPort.generate("image/jpeg")).thenReturn(expected);

        PresignedUploadResult result = uploadService.requestPresignedUrl("image/jpeg");

        assertEquals(expected, result);
    }
}
