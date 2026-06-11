package com.fandrops.user.api;

import com.fandrops.user.api.dto.AgencyApplicationRequest;
import com.fandrops.user.api.dto.ReviewAgencyApplicationRequest;
import com.fandrops.user.application.dto.AgencyApplicationResult;
import com.fandrops.user.application.dto.CreateAgencyApplicationCommand;
import com.fandrops.user.application.exception.AgencyApplicationNotFoundException;
import com.fandrops.user.application.service.AgencyApplicationService;
import com.fandrops.user.domain.AgencyApplicationStatus;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyApplicationControllerTest {

    @Mock AgencyApplicationService agencyApplicationService;
    @Mock Environment environment;
    @Mock Authentication authentication;
    @Mock HttpServletRequest httpRequest;

    AgencyApplicationController controller;

    AgencyApplicationResult stub;

    @BeforeEach
    void setUp() {
        controller = new AgencyApplicationController(agencyApplicationService, environment);
        stub = new AgencyApplicationResult(
                1L, "(주)팬드롭스", null, "홍길동", "agency@test.com",
                "010-1234-5678", "K-Pop 아티스트 입점 신청", "아티스트명",
                AgencyApplicationStatus.PENDING, null, null, null
        );
    }

    private void givenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    // ── apply ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("입점 신청 제출 → 201 Created")
    void apply_success_returns201() {
        AgencyApplicationRequest request = new AgencyApplicationRequest(
                "(주)팬드롭스", null, "홍길동", "agency@test.com",
                "010-1234-5678", "K-Pop 아티스트 입점 신청", "아티스트명"
        );
        when(agencyApplicationService.submitApplication(any(CreateAgencyApplicationCommand.class)))
                .thenReturn(stub);

        ResponseEntity<?> response = controller.apply(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(agencyApplicationService).submitApplication(any(CreateAgencyApplicationCommand.class));
    }

    // ── getApplications ────────────────────────────────────────────────────

    @Test
    @DisplayName("신청 목록 조회 (필터 없음) → 200 OK, 전체 반환")
    void getApplications_noFilter_returns200() {
        when(agencyApplicationService.getApplications(isNull())).thenReturn(List.of(stub));

        ResponseEntity<?> response = controller.getApplications(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(agencyApplicationService).getApplications(null);
    }

    @Test
    @DisplayName("신청 목록 조회 (status=PENDING) → 200 OK, 필터된 목록 반환")
    void getApplications_withPendingFilter_returns200() {
        when(agencyApplicationService.getApplications(AgencyApplicationStatus.PENDING))
                .thenReturn(List.of(stub));

        ResponseEntity<?> response = controller.getApplications("PENDING");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(agencyApplicationService).getApplications(AgencyApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("신청 목록 조회 — 잘못된 status 값 → IllegalArgumentException 전파")
    void getApplications_invalidStatus_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.getApplications("INVALID_STATUS"));
    }

    // ── review ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("입점 승인 → 204 No Content")
    void review_approve_returns204() {
        givenAuthenticated();
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("APPROVED", null);

        ResponseEntity<?> response = controller.review(1L, request, authentication, httpRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(agencyApplicationService).approveApplication(eq(1L), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("입점 반려 (사유 있음) → 204 No Content")
    void review_reject_returns204() {
        givenAuthenticated();
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("REJECTED", "서류 미비");

        ResponseEntity<?> response = controller.review(1L, request, authentication, httpRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(agencyApplicationService).rejectApplication(eq(1L), eq("서류 미비"), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("입점 반려 — rejectReason 없음 → IllegalArgumentException")
    void review_rejectWithoutReason_throwsIllegalArgumentException() {
        givenAuthenticated();
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("REJECTED", null);

        assertThrows(IllegalArgumentException.class,
                () -> controller.review(1L, request, authentication, httpRequest));
    }

    @Test
    @DisplayName("입점 반려 — rejectReason 공백 → IllegalArgumentException")
    void review_rejectWithBlankReason_throwsIllegalArgumentException() {
        givenAuthenticated();
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("REJECTED", "   ");

        assertThrows(IllegalArgumentException.class,
                () -> controller.review(1L, request, authentication, httpRequest));
    }

    @Test
    @DisplayName("심사 처리 — 알 수 없는 status 값 → IllegalArgumentException")
    void review_invalidStatus_throwsIllegalArgumentException() {
        givenAuthenticated();
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("UNKNOWN", null);

        assertThrows(IllegalArgumentException.class,
                () -> controller.review(1L, request, authentication, httpRequest));
    }

    @Test
    @DisplayName("입점 승인 — 존재하지 않는 신청서 → AgencyApplicationNotFoundException 전파")
    void review_approveNotFound_propagatesException() {
        givenAuthenticated();
        doThrow(new AgencyApplicationNotFoundException("신청서를 찾을 수 없습니다."))
                .when(agencyApplicationService).approveApplication(eq(99L), anyLong(), anyString(), anyString());

        assertThrows(AgencyApplicationNotFoundException.class,
                () -> controller.review(99L, new ReviewAgencyApplicationRequest("APPROVED", null),
                        authentication, httpRequest));
    }

    @Test
    @DisplayName("입점 승인 — X-Forwarded-For 헤더 존재 시 첫 번째 IP를 사용한다")
    void review_approve_xForwardedForPresent_usesFirstIp() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("APPROVED", null);

        controller.review(1L, request, authentication, httpRequest);

        verify(agencyApplicationService).approveApplication(eq(1L), anyLong(), eq("203.0.113.10"), anyString());
    }

    @Test
    @DisplayName("입점 승인 — X-Forwarded-For 없으면 getRemoteAddr() fallback")
    void review_approve_noXForwardedFor_fallsBackToRemoteAddr() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        ReviewAgencyApplicationRequest request = new ReviewAgencyApplicationRequest("APPROVED", null);

        controller.review(1L, request, authentication, httpRequest);

        verify(agencyApplicationService).approveApplication(eq(1L), anyLong(), eq("10.0.0.5"), anyString());
    }
}
