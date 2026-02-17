package com.company.lms.exam.repository;

import com.company.lms.exam.entity.QuizAttemptAnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswerEntity, UUID> {
    Optional<QuizAttemptAnswerEntity> findByAttemptIdAndQuestionId(UUID attemptId, UUID questionId);

    List<QuizAttemptAnswerEntity> findByAttemptId(UUID attemptId);
}
