package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.domain.ArtistMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistMemberServiceTest {

    @Mock ArtistMemberRepository artistMemberRepository;
    @Mock AgencyAccountRepository agencyAccountRepository;
    @Mock PasswordEncoder passwordEncoder;

    ArtistMemberService artistMemberService;

    @BeforeEach
    void setUp() {
        artistMemberService = new ArtistMemberService(
                artistMemberRepository, agencyAccountRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Agency 계정과 동일한 loginId — DuplicateLoginIdException (보안 결함 #267)")
    void createArtistMember_loginIdConflictsWithAgency_throwsDuplicateLoginIdException() {
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(true);

        assertThrows(DuplicateLoginIdException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(1L, "hani", "pass", "하니")));

        verify(agencyAccountRepository).existsByLoginId("hani");
        verify(artistMemberRepository, never()).existsByLoginId(anyString());
        verify(artistMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("ArtistMember 내 중복 loginId — DuplicateLoginIdException")
    void createArtistMember_loginIdAlreadyUsedByArtistMember_throwsDuplicateLoginIdException() {
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(false);
        when(artistMemberRepository.existsByLoginId("hani")).thenReturn(true);

        assertThrows(DuplicateLoginIdException.class,
                () -> artistMemberService.createArtistMember(
                        new CreateArtistMemberCommand(1L, "hani", "pass", "하니")));

        verify(artistMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("loginId 중복 없음 — ArtistMember 정상 저장")
    void createArtistMember_uniqueLoginId_savesAndReturns() {
        when(agencyAccountRepository.existsByLoginId("hani")).thenReturn(false);
        when(artistMemberRepository.existsByLoginId("hani")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        ArtistMember saved = ArtistMember.builder()
                .id(1L).artistId(1L).loginId("hani").passwordHash("hashed").memberName("하니").build();
        when(artistMemberRepository.save(any(ArtistMember.class))).thenReturn(saved);

        ArtistMember result = artistMemberService.createArtistMember(
                new CreateArtistMemberCommand(1L, "hani", "pass", "하니"));

        assertEquals("hani", result.getLoginId());
        verify(artistMemberRepository).save(any(ArtistMember.class));
    }
}
