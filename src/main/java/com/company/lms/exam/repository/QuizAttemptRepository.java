package com.company.lms.exam.repository;

import com.company.lms.exam.entity.QuizAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttemptEntity, UUID> {
    List<QuizAttemptEntity> findByQuizIdAndStudentIdOrderByAttemptNoAsc(UUID quizId, UUID studentId);

    Optional<QuizAttemptEntity> findTopByQuizIdAndStudentIdOrderByAttemptNoDesc(UUID quizId, UUID studentId);

    long countByQuizIdAndStudentId(UUID quizId, UUID studentId);

    List<QuizAttemptEntity> findByQuizIdInAndStudentIdIn(List<UUID> quizIds, List<UUID> studentIds);
}
