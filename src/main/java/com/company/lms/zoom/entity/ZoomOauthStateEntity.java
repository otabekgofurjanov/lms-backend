package com.company.lms.zoom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zoom_oauth_states")
@Getter
@Setter
public class ZoomOauthStateEntity {
    @Id
    private String state;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
