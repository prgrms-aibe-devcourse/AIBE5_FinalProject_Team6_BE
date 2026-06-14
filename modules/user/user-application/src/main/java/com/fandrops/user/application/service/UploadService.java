package com.fandrops.user.application.service;

import com.fandrops.user.application.constant.AllowedImageContentType;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.domain.AuditLog;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UploadService {

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

        return result;
    }
}
