package com.fandrops.user.domain;

public enum AgencyApplicationStatus {
    PENDING, APPROVED, REJECTED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}