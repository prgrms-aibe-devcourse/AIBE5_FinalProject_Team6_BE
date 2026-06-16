package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.AuditLog;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
        artistProfileRepository.findById(command.artistId())
                .orElseThrow(() -> new ArtistNotFoundException(
                        "존재하지 않는 아티스트 그룹입니다. artistId=" + command.artistId()));

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
                .afterJson("{\"loginId\":\"" + saved.getLoginId() + "\",\"artistId\":" + saved.getArtistId() + "}")
                .clientIp(clientIp)
                .build());

        return saved;
    }
}
