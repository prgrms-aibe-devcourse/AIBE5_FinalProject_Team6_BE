package com.fandrops.user.api;

import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.service.UploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUploadControllerTest {

    @Mock UploadService uploadService;
    @Mock Environment environment;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminUploadController controller = new AdminUploadController(uploadService, environment);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new UserExceptionHandler())
                .build();
    }

    // standaloneSetup은 SecurityContextPersistenceFilter가 없으므로 authentication() post-processor가
    // SecurityContext를 활성화하지 못한다. request.setUserPrincipal()을 직접 설정해야
    // Spring MVC의 ServletRequestMethodArgumentResolver가 Authentication을 올바르게 주입한다.
    private RequestPostProcessor asAdmin() {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            return request;
        };
    }

    @Test
    @DisplayName("유효한 요청이면 200과 presignedUrl/imageUrl/expiresAt을 반환한다")
    void generatePresignedUrl_validRequest_returns200() throws Exception {
        PresignedUploadResult result = new PresignedUploadResult(
                "https://s3.amazonaws.com/presigned?X-Amz-Signature=abc",
                "https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/banners/uuid.jpg",
                Instant.now().plusSeconds(600));
        when(uploadService.requestPresignedUrl(eq("image/jpeg"), eq(1024L), any(), any(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/admin/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":1024}")
                        .with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrl").value(result.presignedUrl()))
                .andExpect(jsonPath("$.data.imageUrl").value(result.imageUrl()))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("contentType이 빈 문자열이면 400을 반환한다")
    void generatePresignedUrl_blankContentType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"\",\"contentLength\":1024}")
                        .with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("contentLength가 없으면 400을 반환한다")
    void generatePresignedUrl_nullContentLength_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\"}")
                        .with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("contentLength가 5MB를 초과하면 400을 반환한다")
    void generatePresignedUrl_contentLengthExceeds5MB_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"contentLength\":5242881}")
                        .with(asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("허용되지 않은 contentType이면 INVALID_CONTENT_TYPE 에러코드로 400을 반환한다")
    void generatePresignedUrl_invalidContentType_returns400WithErrorCode() throws Exception {
        when(uploadService.requestPresignedUrl(eq("image/gif"), eq(1024L), any(), any(), any()))
                .thenThrow(new InvalidContentTypeException("image/gif"));

        mockMvc.perform(post("/api/v1/admin/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/gif\",\"contentLength\":1024}")
                        .with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CONTENT_TYPE"));
    }
}
