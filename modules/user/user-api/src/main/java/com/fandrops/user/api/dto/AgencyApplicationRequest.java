package com.fandrops.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgencyApplicationRequest(
        @NotBlank String companyName,
        String businessRegistrationNumber,
        @NotBlank String representativeName,
        @NotBlank @Email String contactEmail,
        @NotBlank String contactPhone,
        @NotBlank @Size(max = 2000) String introduction,
        @NotBlank String targetArtistName
) {}