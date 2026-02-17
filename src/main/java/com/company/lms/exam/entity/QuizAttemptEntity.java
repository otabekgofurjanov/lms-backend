package com.company.lms.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
public class QuizAttemptEntity {
    @Id
    private UUID id;

    @Column(name = "quiz_id")
    private UUID quizId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "score_pct")
    private BigDecimal scorePct;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    private String status;
}
