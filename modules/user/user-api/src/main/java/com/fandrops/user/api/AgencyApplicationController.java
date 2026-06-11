package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.AgencyApplicationRequest;
import com.fandrops.user.api.dto.AgencyApplicationResponse;
import com.fandrops.user.api.dto.ReviewAgencyApplicationRequest;
import com.fandrops.user.application.dto.CreateAgencyApplicationCommand;
import com.fandrops.user.application.service.AgencyApplicationService;
import com.fandrops.user.domain.AgencyApplicationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AgencyApplicationController extends UserControllerSupport {

    private final AgencyApplicationService agencyApplicationService;

    public AgencyApplicationController(AgencyApplicationService agencyApplicationService,
                                       Environment environment) {
        super(environment);
        this.agencyApplicationService = agencyApplicationService;
    }

    // F02-01: 입점 신청서 제출
    @PostMapping("/api/v1/b2b/apply")
    public ResponseEntity<ApiResponse<AgencyApplicationResponse>> apply(
            @Valid @RequestBody AgencyApplicationRequest request) {
        AgencyApplicationResponse response = AgencyApplicationResponse.from(
                agencyApplicationService.submitApplication(new CreateAgencyApplicationCommand(
                        request.companyName(),
                        request.businessRegistrationNumber(),
                        request.representativeName(),
                        request.contactEmail(),
                        request.contactPhone(),
                        request.introduction(),
                        request.targetArtistName()
                )));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, traceId()));
    }

    // F02-02: 신청 목록 조회 (Admin) — ?status=PENDING|APPROVED|REJECTED 또는 생략 시 전체
    @GetMapping("/api/v1/admin/artist-applications")
    public ResponseEntity<ApiResponse<List<AgencyApplicationResponse>>> getApplications(
            @RequestParam(required = false) String status) {
        AgencyApplicationStatus statusEnum = null;
        if (status != null) {
            try {
                statusEnum = AgencyApplicationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("유효하지 않은 status 값입니다: " + status);
            }
        }
        List<AgencyApplicationResponse> responses = agencyApplicationService.getApplications(statusEnum)
                .stream()
                .map(AgencyApplicationResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses, traceId()));
    }

    // F02-02: 심사 처리 (승인·반려)
    @PatchMapping("/api/v1/admin/artist-applications/{id}")
    public ResponseEntity<Void> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewAgencyApplicationRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long adminId = resolveAdminId(authentication);
        String clientIp = httpRequest.getRemoteAddr();
        String traceId = traceId();
        switch (request.status().toUpperCase()) {
            case "APPROVED" -> agencyApplicationService.approveApplication(id, adminId, clientIp, traceId);
            case "REJECTED" -> {
                if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                    throw new IllegalArgumentException("반려 시 rejectReason은 필수입니다.");
                }
                agencyApplicationService.rejectApplication(id, request.rejectReason(), adminId, clientIp, traceId);
            }
            default -> throw new IllegalArgumentException(
                    "유효하지 않은 status 값입니다: " + request.status());
        }
        return ResponseEntity.noContent().build();
    }
}
