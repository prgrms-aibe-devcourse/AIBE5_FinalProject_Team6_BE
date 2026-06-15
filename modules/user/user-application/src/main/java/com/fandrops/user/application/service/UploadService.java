package com.fandrops.user.application.service;

import com.fandrops.user.application.constant.AllowedImageContentType;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.domain.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final S3PresignedUrlPort s3PresignedUrlPort;
    private final AuditLogPort auditLogPort;

    public UploadService(S3PresignedUrlPort s3PresignedUrlPort, AuditLogPort auditLogPort) {
        this.s3PresignedUrlPort = s3PresignedUrlPort;
        this.auditLogPort = auditLogPort;
    }

    public PresignedUploadResult requestPresignedUrl(
            String contentType, long contentLength, Long adminId, String clientIp, String traceId) {
        if (!AllowedImageContentType.isAllowed(contentType)) {
            throw new InvalidContentTypeException(contentType);
        }
        PresignedUploadResult result = s3PresignedUrlPort.generate(contentType, contentLength);

        try {
            auditLogPort.save(AuditLog.builder()
                    .occurredAt(Instant.now())
                    .actorType("ADMIN")
                    .actorId(adminId)
                    .action("ADMIN_IMAGE_UPLOAD_URL_ISSUED")
                    .resourceType("BANNER_IMAGE")
                    .traceId(traceId)
                    .afterJson("{\"contentType\":\"" + contentType + "\",\"contentLength\":" + contentLength + "}")
                    .clientIp(clientIp)
                    .build());
        } catch (Exception e) {
            // audit log 누락은 허용 — URL 발급은 이미 완료되었으므로 클라이언트에 결과를 전달
            log.error("[AUDIT_FAIL] Presigned URL 발급 로그 저장 실패 traceId={} adminId={}", traceId, adminId, e);
        }

        return result;
    }
}
