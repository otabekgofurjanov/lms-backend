package com.company.lms.enrollment.repository;

import com.company.lms.enrollment.entity.EnrollmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, UUID> {
    Optional<EnrollmentEntity> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    boolean existsByCourseIdAndStudentIdAndStatus(UUID courseId, UUID studentId, String status);

    @Query("""
            select e from EnrollmentEntity e
            where e.courseId = :courseId
            and (:status is null or e.status = :status)
            """)
    Page<EnrollmentEntity> findByCourse(@Param("courseId") UUID courseId, @Param("status") String status, Pageable pageable);

    @Query("""
            select e from EnrollmentEntity e
            where e.studentId = :studentId and e.status = 'ACTIVE'
            """)
    Page<EnrollmentEntity> findActiveByStudent(@Param("studentId") UUID studentId, Pageable pageable);

    List<EnrollmentEntity> findByCourseId(UUID courseId);

    List<EnrollmentEntity> findByCourseIdAndStatus(UUID courseId, String status);
}

