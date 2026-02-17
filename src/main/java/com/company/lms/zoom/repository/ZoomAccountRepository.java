package com.company.lms.zoom.repository;

import com.company.lms.zoom.entity.ZoomAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ZoomAccountRepository extends JpaRepository<ZoomAccountEntity, UUID> {
    Optional<ZoomAccountEntity> findByOwnerUserId(UUID ownerUserId);
}
