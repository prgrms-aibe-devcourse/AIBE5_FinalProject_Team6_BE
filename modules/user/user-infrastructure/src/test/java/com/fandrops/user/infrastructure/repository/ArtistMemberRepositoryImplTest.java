package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.UserRole;
import com.fandrops.user.infrastructure.persistence.ArtistMemberJpaEntity;
import com.fandrops.user.infrastructure.persistence.ArtistMemberJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistMemberRepositoryImplTest {

    @Mock private ArtistMemberJpaRepository jpaRepository;

    private ArtistMemberRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new ArtistMemberRepositoryImpl(jpaRepository);
    }

    private ArtistMember buildDomain(Long id) {
        return ArtistMember.builder()
                .id(id).artistId(1L).loginId("hani")
                .passwordHash("hash").memberName("하니")
                .role(UserRole.ARTIST).profileImageUrl("https://cdn.fandrops.com/hani.jpg")
                .build();
    }

    @Test
    @DisplayName("save — domain을 JPA entity로 변환 후 저장하고 다시 domain 반환")
    void save_persistsAndReturnsDomain() {
        ArtistMember domain = buildDomain(null);
        ArtistMemberJpaEntity savedEntity = ArtistMemberJpaEntity.from(
                ArtistMember.builder().id(10L).artistId(1L).loginId("hani")
                        .passwordHash("hash").memberName("하니")
                        .role(UserRole.ARTIST).profileImageUrl("https://cdn.fandrops.com/hani.jpg")
                        .build());
        when(jpaRepository.save(any(ArtistMemberJpaEntity.class))).thenReturn(savedEntity);

        ArtistMember result = repository.save(domain);

        assertEquals(10L, result.getId());
        assertEquals("hani", result.getLoginId());
        assertEquals(UserRole.ARTIST, result.getRole());
        verify(jpaRepository).save(any(ArtistMemberJpaEntity.class));
    }

    @Test
    @DisplayName("findByLoginId — 존재하는 loginId → domain 반환")
    void findByLoginId_found_returnsDomain() {
        ArtistMemberJpaEntity entity = ArtistMemberJpaEntity.from(buildDomain(5L));
        when(jpaRepository.findByLoginId("hani")).thenReturn(Optional.of(entity));

        Optional<ArtistMember> result = repository.findByLoginId("hani");

        assertTrue(result.isPresent());
        assertEquals("hani", result.get().getLoginId());
        assertEquals("하니", result.get().getMemberName());
        assertEquals(UserRole.ARTIST, result.get().getRole());
    }

    @Test
    @DisplayName("findByLoginId — 존재하지 않는 loginId → empty")
    void findByLoginId_notFound_returnsEmpty() {
        when(jpaRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

        assertTrue(repository.findByLoginId("unknown").isEmpty());
    }

    @Test
    @DisplayName("findByLoginId — profileImageUrl이 null이어도 domain 정상 변환")
    void findByLoginId_nullProfileImageUrl_returnsDomainWithNullUrl() {
        ArtistMember domainNoImg = ArtistMember.builder()
                .id(7L).artistId(2L).loginId("minji")
                .passwordHash("hash2").memberName("민지")
                .role(UserRole.ARTIST).build();
        ArtistMemberJpaEntity entity = ArtistMemberJpaEntity.from(domainNoImg);
        when(jpaRepository.findByLoginId("minji")).thenReturn(Optional.of(entity));

        Optional<ArtistMember> result = repository.findByLoginId("minji");

        assertTrue(result.isPresent());
        assertNull(result.get().getProfileImageUrl());
    }
}
