package com.company.lms.exam.mapper;

import com.company.lms.exam.dto.*;
import com.company.lms.exam.entity.QuestionEntity;
import com.company.lms.exam.entity.QuizAttemptEntity;
import com.company.lms.exam.entity.QuizEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExamMapper {
    private final ObjectMapper objectMapper;

    public QuestionAdminResponse toQuestionAdmin(QuestionEntity q) {
        return new QuestionAdminResponse(q.getId(), q.getText(), parseOptions(q.getOptions()), q.getCorrectIndex(), q.getExplanation(), q.getCreatedAt());
    }

    public QuizAdminResponse toQuizAdmin(QuizEntity q) {
        return new QuizAdminResponse(q.getId(), q.getCourseId(), q.getLessonId(), q.getTitle(), q.getTimeLimitSec(), q.getMaxAttempts(), q.getPassScorePct(), q.getIsActive(), q.getCreatedAt());
    }

    public StudentAttemptHistoryItem toHistory(QuizAttemptEntity attempt, boolean passed) {
        return new StudentAttemptHistoryItem(attempt.getAttemptNo(), attempt.getScorePct(), passed, attempt.getStartedAt(), attempt.getFinishedAt(), attempt.getStatus());
    }

    public String toOptionsJson(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<String> parseOptions(String optionsJson) {
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
