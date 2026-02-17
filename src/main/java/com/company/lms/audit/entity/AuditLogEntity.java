package com.company.lms.audit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter @Setter
public class AuditLogEntity {
    @Id
    private UUID id;
    @Column(name = "actor_user_id")
    private UUID actorUserId;
    private String action;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private String entityId;
    @Column(name = "before_data", columnDefinition = "jsonb")
    private String beforeData;
    @Column(name = "after_data", columnDefinition = "jsonb")
    private String afterData;
    private String ip;
    @Column(name = "user_agent")
    private String userAgent;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
