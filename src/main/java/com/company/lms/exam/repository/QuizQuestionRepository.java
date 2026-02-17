package com.company.lms.exam.repository;

import com.company.lms.exam.entity.QuizQuestionEntity;
import com.company.lms.exam.entity.QuizQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, QuizQuestionId> {
    List<QuizQuestionEntity> findByQuizIdOrderBySortOrderAsc(UUID quizId);

    long countByQuestionId(UUID questionId);
}
