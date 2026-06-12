package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AuditLog;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    protected AuditLogJpaEntity() {}

    public static AuditLogJpaEntity from(AuditLog domain) {
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        entity.occurredAt = domain.getOccurredAt();
        entity.actorType = domain.getActorType();
        entity.actorId = domain.getActorId();
        entity.action = domain.getAction();
        entity.resourceType = domain.getResourceType();
        entity.resourceId = domain.getResourceId();
        entity.traceId = domain.getTraceId();
        entity.beforeJson = domain.getBeforeJson();
        entity.afterJson = domain.getAfterJson();
        entity.reason = domain.getReason();
        entity.clientIp = domain.getClientIp();
        return entity;
    }
}
