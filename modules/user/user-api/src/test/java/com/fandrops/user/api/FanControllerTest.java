package com.fandrops.user.api;

import com.fandrops.user.api.dto.UpdateFanRequest;
import com.fandrops.user.application.dto.FanResult;
import com.fandrops.user.application.exception.FanNotFoundException;
import com.fandrops.user.application.exception.InvalidCredentialsException;
import com.fandrops.user.application.service.FanService;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FanControllerTest {

    @Mock FanService fanService;
    @Mock Environment environment;
    @Mock Authentication authentication;

    FanController controller;

    private static final Long FAN_ID = 42L;

    @BeforeEach
    void setUp() {
        controller = new FanController(fanService, environment);
    }

    private void givenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(FAN_ID);
    }

    private FanResult dummyFanResult() {
        return new FanResult(FAN_ID, "fan@example.com", "테스트팬", true, LocalDateTime.of(2024, 1, 1, 0, 0));
    }

    // ── GET /fans/me ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("내 정보 조회 → 200 OK")
    void getMyInfo_success_returns200() {
        givenAuthenticated();
        when(fanService.getMyInfo(FAN_ID)).thenReturn(dummyFanResult());

        ResponseEntity<?> response = controller.getMyInfo(authentication, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("비인증 내 정보 조회 → InvalidCredentialsException 전파")
    void getMyInfo_unauthenticated_throwsInvalidCredentialsException() {
        assertThrows(InvalidCredentialsException.class,
                () -> controller.getMyInfo(null, null));
    }

    @Test
    @DisplayName("존재하지 않는 팬 조회 → FanNotFoundException 전파")
    void getMyInfo_fanNotFound_throwsFanNotFoundException() {
        givenAuthenticated();
        when(fanService.getMyInfo(FAN_ID)).thenThrow(new FanNotFoundException("존재하지 않는 팬입니다."));

        assertThrows(FanNotFoundException.class,
                () -> controller.getMyInfo(authentication, null));
    }

    // ── PATCH /fans/me ────────────────────────────────────────────────────────

    @Test
    @DisplayName("내 정보 수정 → 200 OK")
    void updateMyInfo_success_returns200() {
        givenAuthenticated();
        when(fanService.updateMyInfo(any())).thenReturn(dummyFanResult());

        ResponseEntity<?> response = controller.updateMyInfo(
                authentication, null, new UpdateFanRequest("변경닉네임", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("비인증 내 정보 수정 → InvalidCredentialsException 전파")
    void updateMyInfo_unauthenticated_throwsInvalidCredentialsException() {
        assertThrows(InvalidCredentialsException.class,
                () -> controller.updateMyInfo(null, null, new UpdateFanRequest("닉", null)));
    }

    @Test
    @DisplayName("존재하지 않는 팬 수정 → FanNotFoundException 전파")
    void updateMyInfo_fanNotFound_throwsFanNotFoundException() {
        givenAuthenticated();
        when(fanService.updateMyInfo(any())).thenThrow(new FanNotFoundException("존재하지 않는 팬입니다."));

        assertThrows(FanNotFoundException.class,
                () -> controller.updateMyInfo(authentication, null, new UpdateFanRequest("닉", null)));
    }

    @Test
    @DisplayName("닉네임만 수정 → 200 OK")
    void updateMyInfo_nicknameOnly_returns200() {
        givenAuthenticated();
        when(fanService.updateMyInfo(any())).thenReturn(dummyFanResult());

        ResponseEntity<?> response = controller.updateMyInfo(
                authentication, null, new UpdateFanRequest("새닉네임", null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("알림 수신 여부만 수정 → 200 OK")
    void updateMyInfo_allowNotificationOnly_returns200() {
        givenAuthenticated();
        when(fanService.updateMyInfo(any())).thenReturn(dummyFanResult());

        ResponseEntity<?> response = controller.updateMyInfo(
                authentication, null, new UpdateFanRequest(null, false));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
