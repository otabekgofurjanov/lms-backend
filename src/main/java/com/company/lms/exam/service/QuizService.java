package com.company.lms.exam.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.repository.CourseRepository;
import com.company.lms.exam.dto.*;
import com.company.lms.exam.entity.QuestionEntity;
import com.company.lms.exam.entity.QuizEntity;
import com.company.lms.exam.entity.QuizQuestionEntity;
import com.company.lms.exam.mapper.ExamMapper;
import com.company.lms.exam.repository.QuestionRepository;
import com.company.lms.exam.repository.QuizQuestionRepository;
import com.company.lms.exam.repository.QuizRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuizService {
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final QuizPolicyService quizPolicyService;
    private final ExamMapper examMapper;
    private final AuditService auditService;

    @Transactional
    public QuizAdminResponse create(CreateQuizRequest req, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        CourseEntity course = requireCourse(req.courseId());
        quizPolicyService.assertCanManageCourse(actor, course);
        if (req.lessonId() != null) {
            quizPolicyService.assertLessonBelongsToCourse(req.lessonId(), req.courseId());
        }

        QuizEntity q = new QuizEntity();
        q.setId(UUID.randomUUID());
        q.setCourseId(req.courseId());
        q.setLessonId(req.lessonId());
        q.setTitle(req.title());
        q.setTimeLimitSec(req.timeLimitSec());
        q.setMaxAttempts(req.maxAttempts());
        q.setPassScorePct(req.passScorePct());
        q.setIsActive(req.isActive());
        q.setCreatedBy(actor.getId());
        q.setCreatedAt(OffsetDateTime.now());

        QuizEntity saved = quizRepository.save(q);
        auditService.log(actor.getId(), "QUIZ_CREATE", "QUIZ", saved.getId().toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return examMapper.toQuizAdmin(saved);
    }

    @Transactional
    public QuizAdminResponse update(UUID id, CreateQuizRequest req, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        QuizEntity q = getQuiz(id);
        CourseEntity course = requireCourse(q.getCourseId());
        quizPolicyService.assertCanManageCourse(actor, course);

        if (!q.getCourseId().equals(req.courseId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "COURSE_CHANGE_NOT_ALLOWED", "Changing quiz course is not allowed");
        }
        if (req.lessonId() != null) {
            quizPolicyService.assertLessonBelongsToCourse(req.lessonId(), req.courseId());
        }

        q.setLessonId(req.lessonId());
        q.setTitle(req.title());
        q.setTimeLimitSec(req.timeLimitSec());
        q.setMaxAttempts(req.maxAttempts());
        q.setPassScorePct(req.passScorePct());
        q.setIsActive(req.isActive());
        QuizEntity saved = quizRepository.save(q);

        auditService.log(actor.getId(), "QUIZ_UPDATE", "QUIZ", saved.getId().toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return examMapper.toQuizAdmin(saved);
    }

    @Transactional
    public String delete(UUID id, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        QuizEntity quiz = getQuiz(id);
        CourseEntity course = requireCourse(quiz.getCourseId());
        quizPolicyService.assertCanManageCourse(actor, course);
        quizRepository.delete(quiz);
        auditService.log(actor.getId(), "QUIZ_DELETE", "QUIZ", id.toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return "deleted";
    }

    @Transactional
    public String attachQuestions(UUID quizId, AttachQuestionsRequest req, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        QuizEntity quiz = getQuiz(quizId);
        CourseEntity course = requireCourse(quiz.getCourseId());
        quizPolicyService.assertCanManageCourse(actor, course);

        List<QuizQuestionEntity> existing = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quizId);
        Set<UUID> existingIds = new HashSet<>();
        int sort = 0;
        for (QuizQuestionEntity e : existing) {
            existingIds.add(e.getQuestionId());
            sort = Math.max(sort, Optional.ofNullable(e.getSortOrder()).orElse(0));
        }

        List<QuestionEntity> questions = questionRepository.findByIdIn(req.questionIds());
        if (questions.size() != req.questionIds().size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QUESTION_NOT_FOUND", "One or more questions not found");
        }

        for (UUID qid : req.questionIds()) {
            if (existingIds.contains(qid)) {
                continue;
            }
            QuizQuestionEntity link = new QuizQuestionEntity();
            link.setQuizId(quizId);
            link.setQuestionId(qid);
            link.setSortOrder(++sort);
            quizQuestionRepository.save(link);
        }

        auditService.log(actor.getId(), "QUIZ_UPDATE", "QUIZ", quizId.toString(), null, "{\"attach\":true}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return "attached";
    }

    @Transactional
    public String reorderQuestions(UUID quizId, ReorderQuizQuestionsRequest req, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        QuizEntity quiz = getQuiz(quizId);
        CourseEntity course = requireCourse(quiz.getCourseId());
        quizPolicyService.assertCanManageCourse(actor, course);

        List<QuizQuestionEntity> existing = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quizId);
        Set<UUID> currentIds = existing.stream().map(QuizQuestionEntity::getQuestionId).collect(java.util.stream.Collectors.toSet());
        if (req.questionIdsInOrder().size() != currentIds.size() || !currentIds.equals(new HashSet<>(req.questionIdsInOrder()))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_REORDER", "questionIdsInOrder must contain exact quiz question ids");
        }

        Map<UUID, QuizQuestionEntity> map = new HashMap<>();
        for (QuizQuestionEntity e : existing) map.put(e.getQuestionId(), e);

        int idx = 1;
        for (UUID qid : req.questionIdsInOrder()) {
            QuizQuestionEntity e = map.get(qid);
            e.setSortOrder(idx++);
            quizQuestionRepository.save(e);
        }

        auditService.log(actor.getId(), "QUIZ_UPDATE", "QUIZ", quizId.toString(), null, "{\"reorder\":true}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return "reordered";
    }

    @Transactional(readOnly = true)
    public PageResponse<QuizAdminResponse> list(String actorEmail, UUID courseId, int page, int size) {
        UserEntity actor = quizPolicyService.requireActor(actorEmail);
        Page<QuizEntity> result;
        if (actor.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()))) {
            result = quizRepository.findByCourseId(courseId, PageRequest.of(page, size));
        } else {
            result = quizRepository.findByCourseIdAndCreatedBy(courseId, actor.getId(), PageRequest.of(page, size));
        }
        return new PageResponse<>(result.map(examMapper::toQuizAdmin).getContent(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private QuizEntity getQuiz(UUID quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "QUIZ_NOT_FOUND", "Quiz not found"));
    }

    private CourseEntity requireCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
    }
}
