package com.fandrops.user.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.user.application.constant.AllowedImageContentType;
import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.ArtistMemberNotFoundException;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.exception.InvalidImageUrlException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.application.port.S3ImageValidationPort;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.domain.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ArtistMemberService {

    private static final Logger log = LoggerFactory.getLogger(ArtistMemberService.class);

    private final ArtistMemberRepository artistMemberRepository;
    private final AgencyAccountRepository agencyAccountRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final AuditLogPort auditLogPort;
    private final PasswordEncoder passwordEncoder;
    private final S3PresignedUrlPort s3PresignedUrlPort;
    private final S3ImageValidationPort s3ImageValidationPort;
    private final ObjectMapper objectMapper;

    public ArtistMemberService(
            ArtistMemberRepository artistMemberRepository,
            AgencyAccountRepository agencyAccountRepository,
            ArtistProfileRepository artistProfileRepository,
            AuditLogPort auditLogPort,
            PasswordEncoder passwordEncoder,
            S3PresignedUrlPort s3PresignedUrlPort,
            S3ImageValidationPort s3ImageValidationPort,
            ObjectMapper objectMapper) {
        this.artistMemberRepository = artistMemberRepository;
        this.agencyAccountRepository = agencyAccountRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.auditLogPort = auditLogPort;
        this.passwordEncoder = passwordEncoder;
        this.s3PresignedUrlPort = s3PresignedUrlPort;
        this.s3ImageValidationPort = s3ImageValidationPort;
        this.objectMapper = objectMapper;
    }

    // Agency loginId와의 cross-table 중복 체크 후 ArtistMember 생성
    // Agency hit 시 early return하는 AuthService.login() 구조상,
    // 같은 loginId가 두 테이블에 존재하면 ArtistMember 영구 로그인 불가 (#267)
    @Transactional
    public ArtistMember createArtistMember(CreateArtistMemberCommand command,
                                            Long actorId, String clientIp, String traceId) {
        ArtistProfile profile = artistProfileRepository.findById(command.artistId())
                .orElseThrow(() -> new ArtistNotFoundException(
                        "존재하지 않는 아티스트 그룹입니다. artistId=" + command.artistId()));
        // 소유권 검증: 요청한 Agency가 이 아티스트의 소속사인지 확인
        // 다른 Agency 소속 아티스트에 멤버를 생성하지 못하도록 막음.
        // 존재하지 않는 것과 동일한 메시지로 응답해 ID 노출 방지
        if (!profile.getAgencyId().equals(actorId)) {
            throw new ArtistNotFoundException(
                    "존재하지 않는 아티스트 그룹입니다. artistId=" + command.artistId());
        }

        if (agencyAccountRepository.existsByLoginId(command.loginId())) {
            throw new DuplicateLoginIdException("이미 Agency 계정에서 사용 중인 loginId입니다.");
        }
        if (artistMemberRepository.existsByLoginId(command.loginId())) {
            throw new DuplicateLoginIdException("이미 사용 중인 loginId입니다.");
        }

        ArtistMember member = ArtistMember.builder()
                .artistId(command.artistId())
                .loginId(command.loginId())
                .passwordHash(passwordEncoder.encode(command.rawPassword()))
                .memberName(command.memberName())
                .build();

        ArtistMember saved;
        try {
            saved = artistMemberRepository.save(member);
        } catch (DataIntegrityViolationException e) {
            // TOCTOU: 동시 요청이 existsBy 체크를 동시에 통과한 경우 DB UNIQUE 제약이 방어
            throw new DuplicateLoginIdException("이미 사용 중인 loginId입니다.");
        }

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(actorId)
                .action("ARTIST_MEMBER_CREATE")
                .resourceType("ARTIST_MEMBER")
                .resourceId(saved.getId())
                .traceId(traceId)
                .afterJson(toJson(Map.of("loginId", saved.getLoginId(), "artistId", saved.getArtistId())))
                .clientIp(clientIp)
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public ArtistMember getArtistMember(Long memberId, Long actorId, String clientIp, String traceId) {
        return findMemberWithOwnership(memberId, actorId);
    }

    @Transactional
    public ArtistMember updateArtistMemberName(Long memberId, String memberName,
                                               Long actorId, String clientIp, String traceId) {
        ArtistMember member = findMemberWithOwnership(memberId, actorId);
        ArtistMember updated = artistMemberRepository.save(member.withMemberName(memberName));

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(actorId)
                .action("ARTIST_MEMBER_UPDATE")
                .resourceType("ARTIST_MEMBER")
                .resourceId(memberId)
                .traceId(traceId)
                .afterJson(toJson(Map.of("memberName", memberName)))
                .clientIp(clientIp)
                .build());

        return updated;
    }

    @Transactional
    public void deleteArtistMember(Long memberId, Long actorId, String clientIp, String traceId) {
        ArtistMember member = findMemberWithOwnership(memberId, actorId);
        artistMemberRepository.save(member.withDeletedAt(LocalDateTime.now()));

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(actorId)
                .action("ARTIST_MEMBER_DELETE")
                .resourceType("ARTIST_MEMBER")
                .resourceId(memberId)
                .traceId(traceId)
                .clientIp(clientIp)
                .build());
    }

    public PresignedUploadResult generateProfileImagePresignedUrl(
            Long memberId, Long agencyId, String contentType, long contentLength,
            String clientIp, String traceId) {
        if (!AllowedImageContentType.isAllowed(contentType)) {
            throw new InvalidContentTypeException(contentType);
        }
        findMemberWithOwnership(memberId, agencyId);
        PresignedUploadResult result = s3PresignedUrlPort.generate(contentType, contentLength);
        try {
            auditLogPort.save(AuditLog.builder()
                    .occurredAt(Instant.now())
                    .actorType("AGENCY")
                    .actorId(agencyId)
                    .action("ARTIST_MEMBER_PROFILE_IMAGE_PRESIGNED_URL_ISSUED")
                    .resourceType("ARTIST_MEMBER")
                    .resourceId(memberId)
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
    public ArtistMember updateProfileImageUrl(Long memberId, String imageUrl,
                                              Long agencyId, String clientIp, String traceId) {
        if (!s3ImageValidationPort.isOwnedUrl(imageUrl)) {
            throw new InvalidImageUrlException(imageUrl);
        }
        ArtistMember member = findMemberWithOwnership(memberId, agencyId);
        ArtistMember updated = artistMemberRepository.save(member.withProfileImageUrl(imageUrl));

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(agencyId)
                .action("ARTIST_MEMBER_PROFILE_IMAGE_URL_CONFIRMED")
                .resourceType("ARTIST_MEMBER")
                .resourceId(memberId)
                .traceId(traceId)
                .afterJson(toJson(Map.of("profileImageUrl", imageUrl)))
                .clientIp(clientIp)
                .build());

        return updated;
    }

    private ArtistMember findMemberWithOwnership(Long memberId, Long actorId) {
        ArtistMember member = artistMemberRepository.findById(memberId)
                .orElseThrow(() -> new ArtistMemberNotFoundException(
                        "존재하지 않는 아티스트 멤버입니다. memberId=" + memberId));
        ArtistProfile profile = artistProfileRepository.findById(member.getArtistId())
                .orElseThrow(() -> new ArtistMemberNotFoundException(
                        "존재하지 않는 아티스트 멤버입니다. memberId=" + memberId));
        if (!profile.getAgencyId().equals(actorId)) {
            throw new ArtistMemberNotFoundException(
                    "존재하지 않는 아티스트 멤버입니다. memberId=" + memberId);
        }
        return member;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("afterJson 직렬화 실패", e);
        }
    }
}
