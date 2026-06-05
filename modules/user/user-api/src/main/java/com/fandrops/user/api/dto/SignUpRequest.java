package com.fandrops.user.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Size(max = 50) String nickname,
        @AssertTrue(message = "약관에 동의해야 합니다.") boolean termsAgreed
) {}
