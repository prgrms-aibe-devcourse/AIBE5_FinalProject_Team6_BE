package com.fandrops.user.api;

import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyUploadControllerTest {

    @Mock UploadService uploadService;
    @Mock Environment environment;
    @Mock Authentication authentication;
    @Mock HttpServletRequest httpRequest;

    AgencyUploadController controller;

    private static final Long AGENCY_ID = 10L;

    @BeforeEach
    void setUp() {
        controller = new AgencyUploadController(uploadService, environment);
    }

    private void givenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(AGENCY_ID);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    @Test
    @DisplayName("Presigned URL 발급 성공 → 200 OK + presignedUrl·imageUrl 반환")
    void generatePresignedUrl_success_returns200WithUrls() {
        givenAuthenticated();
        PresignedUploadResult result = new PresignedUploadResult(
                "https://s3.amazonaws.com/presigned?sig=abc",
                "https://bucket.s3.amazonaws.com/uploads/banners/uuid.jpg",
                Instant.now().plusSeconds(600));
        when(uploadService.requestPresignedUrlForAgency(eq("image/jpeg"), eq(1024L),
                eq(AGENCY_ID), eq("10.0.0.1"), anyString())).thenReturn(result);

        ResponseEntity<?> response = controller.generatePresignedUrl(
                new UploadPresignedUrlRequest("image/jpeg", 1024L),
                authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(uploadService).requestPresignedUrlForAgency(eq("image/jpeg"), eq(1024L),
                eq(AGENCY_ID), eq("10.0.0.1"), anyString());
    }

    @Test
    @DisplayName("허용되지 않은 contentType → InvalidContentTypeException 그대로 전파")
    void generatePresignedUrl_invalidContentType_propagatesException() {
        givenAuthenticated();
        when(uploadService.requestPresignedUrlForAgency(eq("image/gif"), anyLong(),
                any(), any(), any())).thenThrow(new InvalidContentTypeException("image/gif"));

        assertThrows(InvalidContentTypeException.class,
                () -> controller.generatePresignedUrl(
                        new UploadPresignedUrlRequest("image/gif", 1024L),
                        authentication, httpRequest));
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더가 있으면 첫 번째 IP를 clientIp로 사용")
    void generatePresignedUrl_xForwardedFor_usesFirstIp() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(AGENCY_ID);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        PresignedUploadResult result = new PresignedUploadResult(
                "https://presigned", "https://image", Instant.now().plusSeconds(600));
        when(uploadService.requestPresignedUrlForAgency(any(), anyLong(), any(), eq("1.2.3.4"), any()))
                .thenReturn(result);

        controller.generatePresignedUrl(
                new UploadPresignedUrlRequest("image/jpeg", 512L),
                authentication, httpRequest);

        verify(uploadService).requestPresignedUrlForAgency(any(), anyLong(), any(), eq("1.2.3.4"), any());
    }
}
