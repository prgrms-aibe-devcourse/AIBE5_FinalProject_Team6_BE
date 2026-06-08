package com.fandrops.community.api.vote;

import jakarta.validation.constraints.NotNull;

public record GoodsBallotRequest(@NotNull Long optionId) {}
