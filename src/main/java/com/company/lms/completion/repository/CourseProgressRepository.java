package com.company.lms.completion.repository;

import com.company.lms.completion.entity.CourseProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseProgressRepository extends JpaRepository<CourseProgressEntity, UUID> {
    Optional<CourseProgressEntity> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    List<CourseProgressEntity> findByCourseId(UUID courseId);
}
