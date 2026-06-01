package com.fandrops.community.application.port;

public interface FanMembershipPort {

    boolean isFanOf(Long fanId, Long artistId);
}