package com.company.lms.video.repository;

import com.company.lms.video.entity.VideoAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoAssetRepository extends JpaRepository<VideoAssetEntity, UUID> {
    Optional<VideoAssetEntity> findTopByRecordingIdOrderByCreatedAtDesc(UUID recordingId);
}
