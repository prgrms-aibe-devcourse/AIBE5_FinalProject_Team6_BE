package com.fandrops.user.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.user.application.constant.AllowedImageContentType;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.exception.InvalidImageUrlException;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.application.port.S3ImageValidationPort;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.domain.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ArtistProfileService {

    private static final Logger log = LoggerFactory.getLogger(ArtistProfileService.class);

    private final ArtistProfileRepository artistProfileRepository;
    private final AuditLogPort auditLogPort;
    private final S3PresignedUrlPort s3PresignedUrlPort;
    private final S3ImageValidationPort s3ImageValidationPort;
    private final ObjectMapper objectMapper;

    public ArtistProfileService(
            ArtistProfileRepository artistProfileRepository,
            AuditLogPort auditLogPort,
            S3PresignedUrlPort s3PresignedUrlPort,
            S3ImageValidationPort s3ImageValidationPort,
            ObjectMapper objectMapper) {
        this.artistProfileRepository = artistProfileRepository;
        this.auditLogPort = auditLogPort;
        this.s3PresignedUrlPort = s3PresignedUrlPort;
        this.s3ImageValidationPort = s3ImageValidationPort;
        this.objectMapper = objectMapper;
    }

    public ArtistProfile getArtistProfile(Long artistId) {
        return artistProfileRepository.findById(artistId)
                .orElseThrow(() -> new ArtistNotFoundException("아티스트를 찾을 수 없습니다."));
    }

    public ArtistProfileListResult listArtistProfiles(String cursor, int size) {
        Long cursorId = null;
        if (cursor != null) {
            try {
                cursorId = Long.valueOf(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다: " + cursor);
            }
        }
        List<ArtistProfile> raw = artistProfileRepository.findAllOrderByFanCountDesc(cursorId, size + 1);
        boolean hasMore = raw.size() > size;
        List<ArtistProfile> page = hasMore ? raw.subList(0, size) : raw;
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new ArtistProfileListResult(page, nextCursor, hasMore);
    }

    public ArtistProfileListResult listByAgencyId(Long agencyId, String cursor, int size) {
        Long cursorId = null;
        if (cursor != null) {
            try {
                cursorId = Long.valueOf(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다: " + cursor);
            }
        }
        List<ArtistProfile> raw = artistProfileRepository.findByAgencyId(agencyId, cursorId, size + 1);
        boolean hasMore = raw.size() > size;
        List<ArtistProfile> page = hasMore ? raw.subList(0, size) : raw;
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new ArtistProfileListResult(page, nextCursor, hasMore);
    }

    @Transactional
    public ArtistProfile updateArtistProfile(
            Long artistId, Long agencyId,
            String bio, String profileImageUrl,
            String instagramUrl, String youtubeUrl, String twitterUrl, String officialUrl,
            String clientIp, String traceId) {
        ArtistProfile profile = findWithOwnership(artistId, agencyId);
        profile.updateProfile(profileImageUrl, profile.getCoverImageUrl(), bio,
                officialUrl, youtubeUrl, instagramUrl, twitterUrl);
        ArtistProfile saved = artistProfileRepository.save(profile);

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(agencyId)
                .action("ARTIST_PROFILE_UPDATE")
                .resourceType("ARTIST_PROFILE")
                .resourceId(artistId)
                .traceId(traceId)
                .afterJson(toJson(Map.of("bio", String.valueOf(bio), "artistId", artistId)))
                .clientIp(clientIp)
                .build());

        return saved;
    }

    public PresignedUploadResult generateProfileImagePresignedUrl(
            Long artistId, Long agencyId, String contentType, long contentLength,
            String clientIp, String traceId) {
        if (!AllowedImageContentType.isAllowed(contentType)) {
            throw new InvalidContentTypeException(contentType);
        }
        findWithOwnership(artistId, agencyId);
        PresignedUploadResult result = s3PresignedUrlPort.generateForProfileImage(contentType, contentLength);
        try {
            auditLogPort.save(AuditLog.builder()
                    .occurredAt(Instant.now())
                    .actorType("AGENCY")
                    .actorId(agencyId)
                    .action("ARTIST_PROFILE_IMAGE_PRESIGNED_URL_ISSUED")
                    .resourceType("ARTIST_PROFILE")
                    .resourceId(artistId)
                    .traceId(traceId)
                    .afterJson(toJson(Map.of("contentType", contentType, "contentLength", contentLength)))
                    .clientIp(clientIp)
                    .build());
        } catch (Exception e) {
            log.error("[AUDIT_FAIL] presigned URL 발급 로그 저장 실패 traceId={} agencyId={}", traceId, agencyId, e);
        }
        return result;
    }

    @Transactional
    public ArtistProfile confirmProfileImageUrl(
            Long artistId, Long agencyId, String imageUrl, String clientIp, String traceId) {
        if (!s3ImageValidationPort.isOwnedUrl(imageUrl)) {
            throw new InvalidImageUrlException(imageUrl);
        }
        ArtistProfile profile = findWithOwnership(artistId, agencyId);
        profile.updateProfileImageUrl(imageUrl);
        ArtistProfile saved = artistProfileRepository.save(profile);

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(agencyId)
                .action("ARTIST_PROFILE_IMAGE_URL_CONFIRMED")
                .resourceType("ARTIST_PROFILE")
                .resourceId(artistId)
                .traceId(traceId)
                .afterJson(toJson(Map.of("profileImageUrl", imageUrl)))
                .clientIp(clientIp)
                .build());

        return saved;
    }

    private ArtistProfile findWithOwnership(Long artistId, Long agencyId) {
        ArtistProfile profile = artistProfileRepository.findById(artistId)
                .orElseThrow(() -> new ArtistNotFoundException("아티스트를 찾을 수 없습니다. artistId=" + artistId));
        if (!profile.getAgencyId().equals(agencyId)) {
            throw new ArtistNotFoundException("아티스트를 찾을 수 없습니다. artistId=" + artistId);
        }
        return profile;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("afterJson 직렬화 실패", e);
        }
    }
}
