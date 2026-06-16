package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.domain.ArtistMember;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistMemberService {

    private final ArtistMemberRepository artistMemberRepository;
    private final AgencyAccountRepository agencyAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public ArtistMemberService(
            ArtistMemberRepository artistMemberRepository,
            AgencyAccountRepository agencyAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.artistMemberRepository = artistMemberRepository;
        this.agencyAccountRepository = agencyAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Agency loginId와의 cross-table 중복 체크 후 ArtistMember 생성
    // Agency hit 시 early return하는 AuthService.login() 구조상,
    // 같은 loginId가 두 테이블에 존재하면 ArtistMember 영구 로그인 불가 (#267)
    @Transactional
    public ArtistMember createArtistMember(CreateArtistMemberCommand command) {
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

        return artistMemberRepository.save(member);
    }
}
