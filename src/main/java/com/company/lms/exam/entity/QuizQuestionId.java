package com.company.lms.exam.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class QuizQuestionId implements Serializable {
    private UUID quizId;
    private UUID questionId;

    public QuizQuestionId() {}

    public QuizQuestionId(UUID quizId, UUID questionId) {
        this.quizId = quizId;
        this.questionId = questionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuizQuestionId that = (QuizQuestionId) o;
        return Objects.equals(quizId, that.quizId) && Objects.equals(questionId, that.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quizId, questionId);
    }
}
