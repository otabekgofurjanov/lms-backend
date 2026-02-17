package com.company.lms.users.repository;

import com.company.lms.users.entity.RoleAdminEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleAdminRepository extends JpaRepository<RoleAdminEntity, UUID> {
    List<RoleAdminEntity> findByCodeIn(Collection<String> codes);

    Optional<RoleAdminEntity> findByCode(String code);
}
