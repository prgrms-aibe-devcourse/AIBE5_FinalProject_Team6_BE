package com.fandrops.user.infrastructure;

import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.infrastructure.config.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3PresignedUrlAdapterTest {

    // RETURNS_DEEP_STUBS: presigner.presignPutObject(...).url().toString() 처럼
    // 연쇄 호출(메서드 체인)을 한 번에 모킹하기 위해 사용
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    S3Presigner presigner;

    S3Properties properties;
    S3PresignedUrlAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new S3Properties();
        properties.setBucket("test-bucket");
        properties.setRegion("ap-northeast-2");
        properties.setPresignedUrlExpiryMinutes(10);
        properties.setUploadPrefix("uploads/banners");
        adapter = new S3PresignedUrlAdapter(presigner, properties);
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, .jpg",
            "image/png,  .png",
            "image/webp, .webp"
    })
    @DisplayName("contentType에 따라 objectKey 확장자와 imageUrl이 올바르게 생성된다")
    void generate_correctExtensionAndImageUrl(String contentType, String expectedExt) {
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)).url().toString()).thenReturn("https://presigned");

        PresignedUploadResult result = adapter.generate(contentType);

        // imageUrl이 올바른 S3 도메인 + 경로 + 확장자를 가지는지 검증
        assertTrue(result.imageUrl().startsWith(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/uploads/banners/"),
                "imageUrl은 버킷/리전/prefix로 시작해야 한다");
        assertTrue(result.imageUrl().endsWith(expectedExt.trim()),
                "imageUrl은 올바른 확장자로 끝나야 한다");
    }

    @Test
    @DisplayName("objectKey가 IAM Role 권한 경로(uploads/)로 시작하는지 검증")
    void generate_objectKeyStartsWithUploadsPrefix() {
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)).url().toString()).thenReturn("https://presigned");

        PresignedUploadResult result = adapter.generate("image/jpeg");

        // IAM Role이 uploads/* 경로에만 PutObject 권한을 가지므로,
        // 이 prefix를 벗어나면 403 Forbidden 발생 — 반드시 확인
        String path = result.imageUrl()
                .replace("https://test-bucket.s3.ap-northeast-2.amazonaws.com/", "");
        assertTrue(path.startsWith("uploads/"), "objectKey는 반드시 uploads/ 로 시작해야 한다");
    }

    @Test
    @DisplayName("presignedUrl은 S3Presigner가 반환한 값을 그대로 전달한다")
    void generate_presignedUrlFromPresigner() {
        String expectedPresignedUrl = "https://test-bucket.s3.amazonaws.com/uploads/banners/uuid.jpg?X-Amz-Signature=abc";
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class)).url().toString()).thenReturn(expectedPresignedUrl);

        PresignedUploadResult result = adapter.generate("image/png");

        assertEquals(expectedPresignedUrl, result.presignedUrl());
    }
}
