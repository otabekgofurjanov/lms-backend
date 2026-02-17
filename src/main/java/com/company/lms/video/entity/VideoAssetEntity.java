package com.company.lms.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_assets")
@Getter
@Setter
public class VideoAssetEntity {
    @Id
    private UUID id;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(name = "storage_provider")
    private String storageProvider;

    private String bucket;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256")
    private String checksumSha256;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
