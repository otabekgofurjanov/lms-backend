package com.company.lms.auth.repository;

import com.company.lms.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);

    java.util.List<UserEntity> findByFullNameIgnoreCase(String fullName);
}

