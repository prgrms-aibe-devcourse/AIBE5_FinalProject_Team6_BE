package com.fandrops.user.application.dto;

public record CreateAgencyApplicationCommand(
        String companyName,
        String businessRegistrationNumber,
        String representativeName,
        String contactEmail,
        String contactPhone,
        String introduction,
        String targetArtistName
) {}
