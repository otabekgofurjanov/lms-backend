package com.company.lms.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter @Setter
public class UserSessionEntity {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "refresh_jti")
    private String refreshJti;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
    @Column(name = "user_agent")
    private String userAgent;
    private String ip;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
