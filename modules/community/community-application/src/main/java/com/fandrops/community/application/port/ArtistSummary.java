package com.fandrops.community.application.port;

/**
 * community 모듈이 소유하는 아티스트 요약 정보.
 * user-domain.ArtistProfile 엔티티를 직접 import하지 않도록 포트 반환 타입으로 정의.
 */
public record ArtistSummary(Long artistId, String name, String profileImageUrl) {}