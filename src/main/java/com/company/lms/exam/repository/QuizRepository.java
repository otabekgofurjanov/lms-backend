package com.company.lms.exam.repository;

import com.company.lms.exam.entity.QuizEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<QuizEntity, UUID> {
    Page<QuizEntity> findByCourseId(UUID courseId, Pageable pageable);

    Page<QuizEntity> findByCourseIdAndCreatedBy(UUID courseId, UUID createdBy, Pageable pageable);

    List<QuizEntity> findByCourseIdAndIsActiveTrue(UUID courseId);
}
