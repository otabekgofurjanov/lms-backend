package com.company.lms.exam.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "quiz_questions")
@IdClass(QuizQuestionId.class)
@Getter
@Setter
public class QuizQuestionEntity {
    @Id
    @Column(name = "quiz_id")
    private UUID quizId;

    @Id
    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
