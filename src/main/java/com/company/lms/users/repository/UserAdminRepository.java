package com.company.lms.users.repository;

import com.company.lms.users.entity.UserAdminEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAdminRepository extends JpaRepository<UserAdminEntity, UUID> {
    Optional<UserAdminEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    @Query("""
            select u from UserAdminEntity u
            where u.status <> 'DELETED'
            and (:search is null or lower(u.email) like lower(concat('%', :search, '%'))
            or lower(u.fullName) like lower(concat('%', :search, '%')))
            """)
    Page<UserAdminEntity> search(@Param("search") String search, Pageable pageable);
}
