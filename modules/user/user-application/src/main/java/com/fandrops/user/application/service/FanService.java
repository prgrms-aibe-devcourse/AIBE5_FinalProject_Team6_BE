package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.FanResult;
import com.fandrops.user.application.dto.UpdateFanCommand;
import com.fandrops.user.application.exception.FanNotFoundException;
import com.fandrops.user.application.port.UserRepository;
import com.fandrops.user.domain.Fan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FanService {

    private final UserRepository userRepository;

    public FanService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public FanResult getMyInfo(Long fanId) {
        Fan fan = userRepository.findById(fanId)
                .orElseThrow(() -> new FanNotFoundException("존재하지 않는 팬입니다."));
        return FanResult.from(fan);
    }

    @Transactional
    public FanResult updateMyInfo(UpdateFanCommand command) {
        Fan fan = userRepository.findById(command.fanId())
                .orElseThrow(() -> new FanNotFoundException("존재하지 않는 팬입니다."));

        if (command.nickname() != null) {
            fan.updateNickname(command.nickname());
        }
        if (command.allowNotification() != null) {
            fan.updateNotificationConsent(command.allowNotification());
        }

        return FanResult.from(userRepository.save(fan));
    }
}
