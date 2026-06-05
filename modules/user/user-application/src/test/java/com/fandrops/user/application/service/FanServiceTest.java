package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.FanResult;
import com.fandrops.user.application.dto.UpdateFanCommand;
import com.fandrops.user.application.exception.FanNotFoundException;
import com.fandrops.user.application.port.UserRepository;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FanServiceTest {

    @Mock UserRepository userRepository;

    FanService fanService;

    @BeforeEach
    void setUp() {
        fanService = new FanService(userRepository);
    }

    // ── getMyInfo ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 fanId 조회 시 FanNotFoundException")
    void getMyInfo_notFound_throwsFanNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(FanNotFoundException.class, () -> fanService.getMyInfo(99L));
    }

    @Test
    @DisplayName("정상 조회 시 FanResult 반환")
    void getMyInfo_success_returnsFanResult() {
        Fan fan = Fan.builder().id(7L).email("a@b.com").nickname("nick").authProvider(AuthProvider.LOCAL).passwordHash("h").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(fan));

        FanResult result = fanService.getMyInfo(7L);
        assertEquals("a@b.com", result.email());
        assertEquals("nick", result.nickname());
    }

    // ── updateMyInfo ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("nickname만 변경 시 allowNotification은 그대로 유지")
    void updateMyInfo_onlyNickname_allowNotificationUnchanged() {
        Fan fan = Fan.builder().id(8L).email("a@b.com").nickname("old").authProvider(AuthProvider.LOCAL).passwordHash("h").build();
        when(userRepository.findById(8L)).thenReturn(Optional.of(fan));
        when(userRepository.save(any(Fan.class))).thenReturn(fan);

        fanService.updateMyInfo(new UpdateFanCommand(8L, "new", null));

        verify(userRepository).save(argThat(f -> "new".equals(f.getNickname())));
    }

    @Test
    @DisplayName("존재하지 않는 fanId 수정 시 FanNotFoundException")
    void updateMyInfo_notFound_throwsFanNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(FanNotFoundException.class,
                () -> fanService.updateMyInfo(new UpdateFanCommand(99L, "nick", null)));
    }
}