package com.fandrops.user.application.port;

import com.fandrops.user.domain.AuditLog;

public interface AuditLogPort {
    void save(AuditLog auditLog);
}
