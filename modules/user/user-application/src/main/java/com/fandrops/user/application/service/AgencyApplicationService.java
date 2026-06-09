package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.AgencyApplicationResult;
import com.fandrops.user.application.dto.CreateAgencyApplicationCommand;
import com.fandrops.user.application.exception.AgencyApplicationNotFoundException;
import com.fandrops.user.application.exception.DuplicateAgencyAccountException;
import com.fandrops.user.application.exception.DuplicateApplicationException;
import com.fandrops.user.application.port.AgencyAccountRepository;
import com.fandrops.user.application.port.AgencyApplicationRepository;
import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.application.port.EmailNotificationPort;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AgencyAccountStatus;
import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationStatus;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.domain.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AgencyApplicationService {

    private final AgencyApplicationRepository agencyApplicationRepository;
    private final AgencyAccountRepository agencyAccountRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final EmailNotificationPort emailNotificationPort;
    private final PasswordEncoder passwordEncoder;

    public AgencyApplicationService(
            AgencyApplicationRepository agencyApplicationRepository,
            AgencyAccountRepository agencyAccountRepository,
            ArtistProfileRepository artistProfileRepository,
            EmailNotificationPort emailNotificationPort,
            PasswordEncoder passwordEncoder) {
        this.agencyApplicationRepository = agencyApplicationRepository;
        this.agencyAccountRepository = agencyAccountRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.emailNotificationPort = emailNotificationPort;
        this.passwordEncoder = passwordEncoder;
    }

    // F02-01: 입점 신청서 제출
    @Transactional
    public AgencyApplicationResult submitApplication(CreateAgencyApplicationCommand command) {
        // 빈 문자열은 null과 동일하게 처리 (1인 크리에이터 — 사업자등록번호 없음)
        String brn = (command.businessRegistrationNumber() != null && !command.businessRegistrationNumber().isBlank())
                ? command.businessRegistrationNumber() : null;

        if (brn != null && agencyApplicationRepository.existsPendingByBusinessRegistrationNumber(brn)) {
            throw new DuplicateApplicationException("이미 심사 중인 신청서가 있습니다.");
        }

        AgencyApplication application = AgencyApplication.builder()
                .companyName(command.companyName())
                .businessRegistrationNumber(brn)
                .representativeName(command.representativeName())
                .contactEmail(command.contactEmail())
                .contactPhone(command.contactPhone())
                .introduction(command.introduction())
                .targetArtistName(command.targetArtistName())
                .build();

        return AgencyApplicationResult.from(agencyApplicationRepository.save(application));
    }

    // F02-02: 신청 목록 조회 (Admin) — status null이면 전체
    public List<AgencyApplicationResult> getApplications(AgencyApplicationStatus status) {
        List<AgencyApplication> applications = (status == null)
                ? agencyApplicationRepository.findAll()
                : agencyApplicationRepository.findAllByStatus(status);
        return applications.stream()
                .map(AgencyApplicationResult::from)
                .toList();
    }

    // F02-02: 승인 — AGENCY_ACCOUNT 생성 + 이메일 발송
    // 이메일 발송 실패 시 @Transactional 롤백 의도적 설계:
    // agency가 임시 비밀번호 이메일을 못 받으면 로그인 방법이 없으므로 승인 자체를 취소해야 함
    @Transactional
    public void approveApplication(Long id) {
        AgencyApplication application = agencyApplicationRepository.findById(id)
                .orElseThrow(() -> new AgencyApplicationNotFoundException(
                        "신청서를 찾을 수 없습니다. id=" + id));

        String loginId = application.getContactEmail();
        if (agencyAccountRepository.existsByLoginId(loginId)) {
            throw new DuplicateAgencyAccountException("이미 해당 이메일로 운영자 계정이 존재합니다: " + loginId);
        }

        application.approve(LocalDateTime.now(ZoneOffset.UTC));
        agencyApplicationRepository.save(application);

        String tempPassword = generateTempPassword();
        AgencyAccount account = AgencyAccount.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .companyName(application.getCompanyName())
                .contactEmail(application.getContactEmail())
                .status(AgencyAccountStatus.ACTIVE)
                .role(UserRole.AGENCY)
                .build();
        AgencyAccount savedAccount = agencyAccountRepository.save(account);

        artistProfileRepository.save(ArtistProfile.builder()
                .agencyId(savedAccount.getId())
                .name(application.getTargetArtistName())
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        emailNotificationPort.sendApplicationApprovedEmail(
                application.getContactEmail(), loginId, tempPassword);
    }

    // F02-02: 반려 — 이메일 발송
    @Transactional
    public void rejectApplication(Long id, String rejectReason) {
        AgencyApplication application = agencyApplicationRepository.findById(id)
                .orElseThrow(() -> new AgencyApplicationNotFoundException(
                        "신청서를 찾을 수 없습니다. id=" + id));

        application.reject(rejectReason, LocalDateTime.now(ZoneOffset.UTC));
        agencyApplicationRepository.save(application);

        emailNotificationPort.sendApplicationRejectedEmail(
                application.getContactEmail(), rejectReason);
    }

    private String generateTempPassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
