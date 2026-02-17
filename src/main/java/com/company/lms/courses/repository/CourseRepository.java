package com.company.lms.courses.repository;

import com.company.lms.courses.entity.CourseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<CourseEntity, UUID> {
    @Query("""
            select c from CourseEntity c
            where (:status is null or c.status = :status)
            and (:search is null or lower(c.title) like lower(concat('%', :search, '%')))
            """)
    Page<CourseEntity> search(@Param("search") String search, @Param("status") String status, Pageable pageable);

    @Query("""
            select c from CourseEntity c
            where c.status = 'ACTIVE'
            and (:search is null or lower(c.title) like lower(concat('%', :search, '%')))
            """)
    Page<CourseEntity> searchActive(@Param("search") String search, Pageable pageable);

    Optional<CourseEntity> findByIdAndStatus(UUID id, String status);
}
