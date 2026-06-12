package com.fandrops.community.api;

public record Principal(Long fanId, Long artistMemberId) {

    public static Principal ofFan(Long fanId) {
        return new Principal(fanId, null);
    }

    public static Principal ofArtistMember(Long artistMemberId) {
        return new Principal(null, artistMemberId);
    }
}