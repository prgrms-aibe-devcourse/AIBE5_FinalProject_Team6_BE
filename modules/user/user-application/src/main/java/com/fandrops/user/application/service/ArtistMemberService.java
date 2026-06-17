package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.exception.ArtistMemberNotFoundException;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.domain.AuditLog;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class ArtistMemberService {

    private final ArtistMemberRepository artistMemberRepository;
    private final AgencyAccountRepository agencyAccountRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final AuditLogPort auditLogPort;
    private final PasswordEncoder passwordEncoder;

    public ArtistMemberService(
            ArtistMemberRepository artistMemberRepository,
            AgencyAccountRepository agencyAccountRepository,
            ArtistProfileRepository artistProfileRepository,
            AuditLogPort auditLogPort,
            PasswordEncoder passwordEncoder) {
        this.artistMemberRepository = artistMemberRepository;
        this.agencyAccountRepository = agencyAccountRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.auditLogPort = auditLogPort;
        this.passwordEncoder = passwordEncoder;
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

        String afterJson = "{\"loginId\":" + escapeJson(saved.getLoginId())
                + ",\"artistId\":" + saved.getArtistId() + "}";

        auditLogPort.save(AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("AGENCY")
                .actorId(actorId)
                .action("ARTIST_MEMBER_CREATE")
                .resourceType("ARTIST_MEMBER")
                .resourceId(saved.getId())
                .traceId(traceId)
                .afterJson(afterJson)
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
                .afterJson("{\"memberName\":" + escapeJson(memberName) + "}")
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

    private static String escapeJson(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
