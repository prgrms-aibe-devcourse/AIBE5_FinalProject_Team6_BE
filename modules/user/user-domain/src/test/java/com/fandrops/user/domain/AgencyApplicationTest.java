package com.fandrops.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AgencyApplicationTest {

    private AgencyApplication newPendingApplication() {
        return AgencyApplication.builder()
                .companyName("HYBE")
                .businessRegistrationNumber("123-45-67890")
                .representativeName("방시혁")
                .contactEmail("contact@hybe.com")
                .contactPhone("02-1234-5678")
                .introduction("글로벌 K-Pop 기획사")
                .targetArtistName("BTS")
                .build();
    }

    @Test
    @DisplayName("PENDING 상태에서 승인하면 APPROVED로 전이된다")
    void approve_success() {
        AgencyApplication app = newPendingApplication();

        app.approve(LocalDateTime.now());

        assertEquals(AgencyApplicationStatus.APPROVED, app.getStatus());
        assertNotNull(app.getReviewedAt());
    }

    @Test
    @DisplayName("PENDING 상태에서 반려하면 REJECTED로 전이되고 rejectReason이 저장된다")
    void reject_success() {
        AgencyApplication app = newPendingApplication();

        app.reject("서류 미비", LocalDateTime.now());

        assertEquals(AgencyApplicationStatus.REJECTED, app.getStatus());
        assertEquals("서류 미비", app.getRejectReason());
        assertNotNull(app.getReviewedAt());
    }

    @Test
    @DisplayName("반려 사유 없이 reject 호출하면 예외가 발생한다 (AA-2)")
    void reject_without_reason_throws() {
        AgencyApplication app = newPendingApplication();

        assertThrows(IllegalArgumentException.class,
                () -> app.reject(null, LocalDateTime.now()));

        assertThrows(IllegalArgumentException.class,
                () -> app.reject("  ", LocalDateTime.now()));
    }

    @Test
    @DisplayName("APPROVED 상태에서 다시 승인하면 예외가 발생한다 (AA-1)")
    void approve_after_approved_throws() {
        AgencyApplication app = newPendingApplication();
        app.approve(LocalDateTime.now());

        assertThrows(IllegalStateException.class,
                () -> app.approve(LocalDateTime.now()));
    }

    @Test
    @DisplayName("REJECTED 상태에서 승인하면 예외가 발생한다 (AA-1)")
    void approve_after_rejected_throws() {
        AgencyApplication app = newPendingApplication();
        app.reject("서류 미비", LocalDateTime.now());

        assertThrows(IllegalStateException.class,
                () -> app.approve(LocalDateTime.now()));
    }

    @Test
    @DisplayName("신규 생성된 신청서의 초기 상태는 PENDING이다")
    void initial_status_is_pending() {
        AgencyApplication app = newPendingApplication();

        assertEquals(AgencyApplicationStatus.PENDING, app.getStatus());
        assertNull(app.getRejectReason());
        assertNull(app.getReviewedAt());
    }

    @Test
    @DisplayName("REJECTED 상태에서 다시 반려하면 예외가 발생한다 (AA-1)")
    void reject_after_rejected_throws() {
        AgencyApplication app = newPendingApplication();
        app.reject("서류 미비", LocalDateTime.now(ZoneOffset.UTC));

        assertThrows(IllegalStateException.class,
                () -> app.reject("추가 사유", LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("reconstitute로 APPROVED 상태를 그대로 복원할 수 있다")
    void reconstitute_approved() {
        LocalDateTime appliedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime reviewedAt = LocalDateTime.of(2025, 1, 5, 0, 0);

        AgencyApplication app = AgencyApplication.reconstitute(
                1L, "HYBE", "123-45-67890", "방시혁",
                "contact@hybe.com", "02-1234-5678", "글로벌 K-Pop 기획사", "BTS",
                AgencyApplicationStatus.APPROVED, null, appliedAt, reviewedAt
        );

        assertEquals(AgencyApplicationStatus.APPROVED, app.getStatus());
        assertEquals(1L, app.getId());
        assertEquals(reviewedAt, app.getReviewedAt());
    }

    @Test
    @DisplayName("reconstitute로 REJECTED 상태와 반려 사유를 그대로 복원할 수 있다")
    void reconstitute_rejected() {
        AgencyApplication app = AgencyApplication.reconstitute(
                2L, "SM", "111-11-11111", "이수만",
                "contact@sm.com", "02-0000-0000", "SM 엔터", "aespa",
                AgencyApplicationStatus.REJECTED, "서류 미비",
                LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC)
        );

        assertEquals(AgencyApplicationStatus.REJECTED, app.getStatus());
        assertEquals("서류 미비", app.getRejectReason());
    }

    @Test
    @DisplayName("필수 필드 없이 build하면 즉시 예외가 발생한다 (Fail-Fast)")
    void build_without_required_field_throws() {
        assertThrows(NullPointerException.class, () ->
                AgencyApplication.builder()
                        .representativeName("방시혁")
                        .build()
        );
    }
}