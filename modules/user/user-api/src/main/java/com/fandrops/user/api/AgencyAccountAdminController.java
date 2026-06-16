package com.fandrops.user.api;

import com.fandrops.user.application.service.AgencyAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgencyAccountAdminController extends UserControllerSupport {

    private final AgencyAccountService agencyAccountService;

    public AgencyAccountAdminController(AgencyAccountService agencyAccountService,
                                        Environment environment) {
        super(environment);
        this.agencyAccountService = agencyAccountService;
    }

    // F02-03: 관리자 Agency 임시 비밀번호 재발급
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/admin/agency-accounts/{id}/reset-password")
    public ResponseEntity<Void> resetTempPassword(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long adminId = resolveAdminId(authentication);
        String clientIp = resolveClientIp(httpRequest);
        agencyAccountService.resetTempPassword(id, adminId, clientIp, traceId());
        return ResponseEntity.noContent().build();
    }
}
