package com.company.lms.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempt_answers")
@Getter
@Setter
public class QuizAttemptAnswerEntity {
    @Id
    private UUID id;

    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "selected_index")
    private Integer selectedIndex;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "answered_at")
    private OffsetDateTime answeredAt;
}
