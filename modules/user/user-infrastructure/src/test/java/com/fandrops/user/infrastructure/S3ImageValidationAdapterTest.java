package com.fandrops.user.infrastructure;

import com.fandrops.user.application.exception.S3OperationException;
import com.fandrops.user.infrastructure.config.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ImageValidationAdapterTest {

    @Mock S3Client s3Client;

    S3Properties properties;
    S3ImageValidationAdapter adapter;

    private static final String BUCKET = "fandrops-test";
    private static final String REGION = "ap-northeast-2";
    private static final String BASE_URL =
            "https://fandrops-test.s3.ap-northeast-2.amazonaws.com/";
    private static final String OWNED_URL = BASE_URL + "uploads/banners/image.jpg";

    @BeforeEach
    void setUp() {
        properties = new S3Properties();
        properties.setBucket(BUCKET);
        properties.setRegion(REGION);
        adapter = new S3ImageValidationAdapter(s3Client, properties);
    }

    // ── isOwnedUrl ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isOwnedUrl — null이면 false")
    void isOwnedUrl_null_returnsFalse() {
        assertFalse(adapter.isOwnedUrl(null));
    }

    @Test
    @DisplayName("isOwnedUrl — 외부 도메인 URL이면 false")
    void isOwnedUrl_externalDomain_returnsFalse() {
        assertFalse(adapter.isOwnedUrl("https://example.com/image.jpg"));
    }

    @Test
    @DisplayName("isOwnedUrl — 버킷 baseUrl로 시작하면 true")
    void isOwnedUrl_ownedUrl_returnsTrue() {
        assertTrue(adapter.isOwnedUrl(OWNED_URL));
    }

    @Test
    @DisplayName("isOwnedUrl — CDN baseUrl로 시작하면 true")
    void isOwnedUrl_cdnUrl_returnsTrue() {
        properties.setCdnBaseUrl("d3lxwf37p438iz.cloudfront.net");
        String cdnUrl = "https://d3lxwf37p438iz.cloudfront.net/uploads/profiles/image.jpg";
        assertTrue(adapter.isOwnedUrl(cdnUrl));
    }

    @Test
    @DisplayName("isOwnedUrl — cdnBaseUrl에 https:// 포함된 경우에도 true")
    void isOwnedUrl_cdnUrlWithHttpsPrefix_returnsTrue() {
        properties.setCdnBaseUrl("https://d3lxwf37p438iz.cloudfront.net");
        String cdnUrl = "https://d3lxwf37p438iz.cloudfront.net/uploads/profiles/image.jpg";
        assertTrue(adapter.isOwnedUrl(cdnUrl));
    }

    @Test
    @DisplayName("isOwnedUrl — CDN 미설정 상태에서 CloudFront URL이면 false")
    void isOwnedUrl_cdnNotConfigured_cloudfrontUrlReturnsFalse() {
        // cdnBaseUrl 설정 없음 (setUp 기본값)
        String cdnUrl = "https://d3lxwf37p438iz.cloudfront.net/uploads/profiles/image.jpg";
        assertFalse(adapter.isOwnedUrl(cdnUrl));
    }

    @Test
    @DisplayName("imageExists — CDN URL이면 object key 추출 후 HeadObject")
    void imageExists_cdnUrl_headObjectsWithExtractedKey() {
        properties.setCdnBaseUrl("d3lxwf37p438iz.cloudfront.net");
        String cdnUrl = "https://d3lxwf37p438iz.cloudfront.net/uploads/profiles/image.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertTrue(adapter.imageExists(cdnUrl));

        verify(s3Client).headObject(HeadObjectRequest.builder()
                .bucket(BUCKET)
                .key("uploads/profiles/image.jpg")
                .build());
    }

    @Test
    @DisplayName("imageExists — CDN URL이지만 NoSuchKeyException이면 false")
    void imageExists_cdnUrl_noSuchKey_returnsFalse() {
        properties.setCdnBaseUrl("d3lxwf37p438iz.cloudfront.net");
        String cdnUrl = "https://d3lxwf37p438iz.cloudfront.net/uploads/profiles/image.jpg";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertFalse(adapter.imageExists(cdnUrl));
    }

    // ── imageExists ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("imageExists — 외부 도메인 URL이면 S3 호출 없이 false 반환")
    void imageExists_externalDomain_returnsFalseWithoutS3Call() {
        boolean result = adapter.imageExists("https://example.com/image.jpg");

        assertFalse(result);
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("imageExists — HeadObject 성공이면 true")
    void imageExists_headObjectSucceeds_returnsTrue() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertTrue(adapter.imageExists(OWNED_URL));
    }

    @Test
    @DisplayName("imageExists — NoSuchKeyException이면 false")
    void imageExists_noSuchKey_returnsFalse() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertFalse(adapter.imageExists(OWNED_URL));
    }

    @Test
    @DisplayName("imageExists — S3Exception이면 S3OperationException 전파")
    void imageExists_s3Exception_throwsS3OperationException() {
        S3Exception s3Exception = mock(S3Exception.class);
        when(s3Exception.statusCode()).thenReturn(403);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception);

        assertThrows(S3OperationException.class, () -> adapter.imageExists(OWNED_URL));
    }

    @Test
    @DisplayName("imageExists — SdkClientException(네트워크 오류)이면 S3OperationException 전파")
    void imageExists_sdkClientException_throwsS3OperationException() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("Connection refused").build());

        assertThrows(S3OperationException.class, () -> adapter.imageExists(OWNED_URL));
    }
}