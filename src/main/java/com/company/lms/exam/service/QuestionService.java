package com.company.lms.exam.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.exam.dto.CreateQuestionRequest;
import com.company.lms.exam.dto.QuestionAdminResponse;
import com.company.lms.exam.entity.QuestionEntity;
import com.company.lms.exam.mapper.ExamMapper;
import com.company.lms.exam.repository.QuestionRepository;
import com.company.lms.exam.repository.QuizQuestionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizPolicyService quizPolicyService;
    private final ExamMapper examMapper;
    private final AuditService auditService;

    @Transactional
    public QuestionAdminResponse create(CreateQuestionRequest req, String actorEmail, HttpServletRequest httpRequest) {
        validate(req);
        UserEntity actor = quizPolicyService.requireActor(actorEmail);

        QuestionEntity e = new QuestionEntity();
        e.setId(UUID.randomUUID());
        e.setQuestionType("MCQ");
        e.setText(req.text());
        e.setOptions(examMapper.toOptionsJson(req.options()));
        e.setCorrectIndex(req.correctIndex());
        e.setExplanation(req.explanation());
        e.setCreatedBy(actor.getId());
        e.setCreatedAt(OffsetDateTime.now());
        QuestionEntity saved = questionRepository.save(e);

        auditService.log(actor.getId(), "QUESTION_CREATE", "QUESTION", saved.getId().toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return examMapper.toQuestionAdmin(saved);
    }

    @Transactional
    public QuestionAdminResponse update(UUID id, CreateQuestionRequest req, String actorEmail, HttpServletRequest httpRequest) {
        validate(req);
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        QuestionEntity q = getQuestion(id);
        if (!isAdmin(actor) && !actor.getId().equals(q.getCreatedBy())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot update this question");
        }
        q.setText(req.text());
        q.setOptions(examMapper.toOptionsJson(req.options()));
        q.setCorrectIndex(req.correctIndex());
        q.setExplanation(req.explanation());
        QuestionEntity saved = questionRepository.save(q);

        auditService.log(actor.getId(), "QUESTION_UPDATE", "QUESTION", saved.getId().toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return examMapper.toQuestionAdmin(saved);
    }

    @Transactional
    public String delete(UUID id, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        QuestionEntity q = getQuestion(id);
        if (!isAdmin(actor) && !actor.getId().equals(q.getCreatedBy())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot delete this question");
        }
        if (quizQuestionRepository.countByQuestionId(id) > 0) {
            throw new AppException(HttpStatus.CONFLICT, "QUESTION_IN_USE", "Question is attached to a quiz");
        }
        questionRepository.delete(q);
        auditService.log(actor.getId(), "QUESTION_DELETE", "QUESTION", id.toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return "deleted";
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionAdminResponse> list(String actorEmail, String search, int page, int size) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        Page<QuestionEntity> result = isAdmin(actor)
                ? questionRepository.search(blankToNull(search), PageRequest.of(page, size))
                : questionRepository.searchByCreator(actor.getId(), blankToNull(search), PageRequest.of(page, size));

        return new PageResponse<>(result.map(examMapper::toQuestionAdmin).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private void validate(CreateQuestionRequest req) {
        if (req.options().size() < 2) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_OPTIONS", "At least 2 options are required");
        }
        if (req.correctIndex() < 0 || req.correctIndex() >= req.options().size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_CORRECT_INDEX", "correctIndex is out of range");
        }
    }

    private QuestionEntity getQuestion(UUID id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question not found"));
    }

    private boolean isAdmin(UserEntity actor) {
        return actor.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
