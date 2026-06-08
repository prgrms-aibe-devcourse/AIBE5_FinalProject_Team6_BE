package com.fandrops.user.domain;

import java.util.Objects;

public class AdminAccount {

    private final Long id;
    private final String loginId;
    private final String passwordHash;

    public AdminAccount(Long id, String loginId, String passwordHash) {
        Objects.requireNonNull(loginId, "loginId는 필수입니다");
        Objects.requireNonNull(passwordHash, "passwordHash는 필수입니다");
        this.id = id;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
}
