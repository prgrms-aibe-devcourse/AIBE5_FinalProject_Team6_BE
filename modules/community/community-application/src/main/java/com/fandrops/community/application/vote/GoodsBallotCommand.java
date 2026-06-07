package com.fandrops.community.application.vote;

public record GoodsBallotCommand(
        Long voteId,
        Long optionId,
        Long fanId
) {}
