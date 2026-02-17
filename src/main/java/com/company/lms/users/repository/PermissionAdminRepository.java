package com.company.lms.users.repository;

import com.company.lms.users.entity.PermissionAdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PermissionAdminRepository extends JpaRepository<PermissionAdminEntity, UUID> {
}
