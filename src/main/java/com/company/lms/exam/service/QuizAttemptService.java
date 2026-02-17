package com.company.lms.exam.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.repository.CourseRepository;
import com.company.lms.enrollment.entity.EnrollmentEntity;
import com.company.lms.enrollment.repository.EnrollmentRepository;
import com.company.lms.exam.dto.*;
import com.company.lms.exam.entity.*;
import com.company.lms.exam.mapper.ExamMapper;
import com.company.lms.exam.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuizAttemptService {
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final QuizPolicyService quizPolicyService;
    private final ExamMapper examMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StudentQuizListItem> studentList(UUID courseId, String studentEmail) {
        UserEntity student = quizPolicyService.requireActor(studentEmail);
        if (!enrollmentRepository.existsByCourseIdAndStudentIdAndStatus(courseId, student.getId(), "ACTIVE")) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment required");
        }
        List<QuizEntity> quizzes = quizRepository.findByCourseIdAndIsActiveTrue(courseId);
        List<StudentQuizListItem> result = new ArrayList<>();
        for (QuizEntity q : quizzes) {
            long used = quizAttemptRepository.countByQuizIdAndStudentId(q.getId(), student.getId());
            boolean canStart = quizPolicyService.canUnlockQuiz(student.getId(), q.getLessonId());
            result.add(new StudentQuizListItem(q.getId(), q.getTitle(), q.getLessonId(), q.getPassScorePct(), q.getMaxAttempts(), used, canStart));
        }
        return result;
    }

    @Transactional
    public StudentQuizAttemptResponse start(UUID quizId, String studentEmail, HttpServletRequest httpRequest) {
        UserEntity student = quizPolicyService.requireActor(studentEmail);
        QuizEntity quiz = getQuiz(quizId);
        quizPolicyService.assertStudentCanTakeQuiz(student.getId(), quiz);

        long attemptsUsed = quizAttemptRepository.countByQuizIdAndStudentId(quizId, student.getId());
        if (attemptsUsed >= quiz.getMaxAttempts()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ATTEMPTS_EXCEEDED", "Maximum attempts reached");
        }

        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setQuizId(quizId);
        attempt.setStudentId(student.getId());
        attempt.setAttemptNo((int) attemptsUsed + 1);
        attempt.setStartedAt(OffsetDateTime.now());
        attempt.setStatus("STARTED");
        QuizAttemptEntity saved = quizAttemptRepository.save(attempt);

        List<QuizQuestionEntity> links = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quizId);
        List<QuestionEntity> questions = questionRepository.findByIdIn(links.stream().map(QuizQuestionEntity::getQuestionId).toList());
        Map<UUID, QuestionEntity> questionMap = new HashMap<>();
        for (QuestionEntity q : questions) questionMap.put(q.getId(), q);

        List<StudentAttemptQuestionItem> items = new ArrayList<>();
        for (QuizQuestionEntity link : links) {
            QuestionEntity q = questionMap.get(link.getQuestionId());
            if (q != null) {
                items.add(new StudentAttemptQuestionItem(q.getId(), q.getText(), examMapper.parseOptions(q.getOptions())));
            }
        }

        auditService.log(student.getId(), "QUIZ_ATTEMPT_START", "QUIZ_ATTEMPT", saved.getId().toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return new StudentQuizAttemptResponse(saved.getId(), quizId, quiz.getTimeLimitSec(), items);
    }

    @Transactional
    public StudentQuizResultResponse submit(UUID attemptId, SubmitQuizAttemptRequest req, String studentEmail, HttpServletRequest httpRequest) {
        UserEntity student = quizPolicyService.requireActor(studentEmail);
        QuizAttemptEntity attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "Attempt not found"));
        if (!attempt.getStudentId().equals(student.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot submit this attempt");
        }
        if (!"STARTED".equals(attempt.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ATTEMPT_NOT_OPEN", "Attempt is already submitted");
        }

        QuizEntity quiz = getQuiz(attempt.getQuizId());
        if (quiz.getTimeLimitSec() != null) {
            OffsetDateTime deadline = attempt.getStartedAt().plusSeconds(quiz.getTimeLimitSec());
            if (OffsetDateTime.now().isAfter(deadline)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "ATTEMPT_TIME_EXPIRED", "Quiz time limit exceeded");
            }
        }

        List<QuizQuestionEntity> links = quizQuestionRepository.findByQuizIdOrderBySortOrderAsc(quiz.getId());
        List<UUID> qids = links.stream().map(QuizQuestionEntity::getQuestionId).toList();
        List<QuestionEntity> questions = questionRepository.findByIdIn(qids);
        Map<UUID, QuestionEntity> map = new HashMap<>();
        for (QuestionEntity q : questions) map.put(q.getId(), q);

        Map<UUID, Integer> answers = new HashMap<>();
        for (AttemptAnswerRequest a : req.answers()) {
            answers.put(a.questionId(), a.selectedIndex());
        }

        int correct = 0;
        for (UUID qid : qids) {
            QuestionEntity question = map.get(qid);
            Integer selected = answers.get(qid);
            boolean isCorrect = selected != null && question != null && selected.equals(question.getCorrectIndex());
            if (isCorrect) correct++;

            QuizAttemptAnswerEntity row = quizAttemptAnswerRepository.findByAttemptIdAndQuestionId(attemptId, qid).orElseGet(() -> {
                QuizAttemptAnswerEntity e = new QuizAttemptAnswerEntity();
                e.setId(UUID.randomUUID());
                e.setAttemptId(attemptId);
                e.setQuestionId(qid);
                return e;
            });
            row.setSelectedIndex(selected);
            row.setIsCorrect(isCorrect);
            row.setAnsweredAt(OffsetDateTime.now());
            quizAttemptAnswerRepository.save(row);
        }

        int total = qids.size();
        BigDecimal score = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
        attempt.setCorrectCount(correct);
        attempt.setTotalQuestions(total);
        attempt.setScorePct(score);
        attempt.setFinishedAt(OffsetDateTime.now());
        attempt.setStatus("EVALUATED");
        quizAttemptRepository.save(attempt);

        boolean passed = score.compareTo(quiz.getPassScorePct()) >= 0;
        auditService.log(student.getId(), "QUIZ_ATTEMPT_SUBMIT", "QUIZ_ATTEMPT", attemptId.toString(), null, "{}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        auditService.log(student.getId(), "QUIZ_ATTEMPT_EVALUATED", "QUIZ_ATTEMPT", attemptId.toString(), null, "{\"score\":\"" + score + "\"}", httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));

        return new StudentQuizResultResponse(attemptId, quiz.getId(), total, correct, score, passed, attempt.getFinishedAt());
    }

    @Transactional(readOnly = true)
    public List<StudentAttemptHistoryItem> history(UUID quizId, String studentEmail) {
        UserEntity student = quizPolicyService.requireActor(studentEmail);
        QuizEntity quiz = getQuiz(quizId);
        if (!enrollmentRepository.existsByCourseIdAndStudentIdAndStatus(quiz.getCourseId(), student.getId(), "ACTIVE")) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment required");
        }
        List<QuizAttemptEntity> attempts = quizAttemptRepository.findByQuizIdAndStudentIdOrderByAttemptNoAsc(quizId, student.getId());
        List<StudentAttemptHistoryItem> out = new ArrayList<>();
        for (QuizAttemptEntity a : attempts) {
            boolean passed = a.getScorePct() != null && a.getScorePct().compareTo(quiz.getPassScorePct()) >= 0;
            out.add(examMapper.toHistory(a, passed));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseQuizResultItem> teacherResults(UUID courseId, String teacherEmail, int page, int size) {
        UserEntity teacher = quizPolicyService.requireActor(teacherEmail);
        CourseEntity course = requireCourse(courseId);
        quizPolicyService.assertCanManageCourse(teacher, course);
        return results(courseId, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseQuizResultItem> adminResults(UUID courseId, String adminEmail, int page, int size) {
        quizPolicyService.requireActor(adminEmail);
        requireCourse(courseId);
        return results(courseId, page, size);
    }

    private PageResponse<CourseQuizResultItem> results(UUID courseId, int page, int size) {
        var enrollmentsPage = enrollmentRepository.findByCourse(courseId, "ACTIVE", PageRequest.of(page, size));
        List<EnrollmentEntity> enrollments = enrollmentsPage.getContent();
        List<UUID> studentIds = enrollments.stream().map(EnrollmentEntity::getStudentId).toList();

        List<QuizEntity> quizzes = quizRepository.findByCourseIdAndIsActiveTrue(courseId);
        List<UUID> quizIds = quizzes.stream().map(QuizEntity::getId).toList();

        List<QuizAttemptEntity> attempts = (quizIds.isEmpty() || studentIds.isEmpty()) ? List.of() : quizAttemptRepository.findByQuizIdInAndStudentIdIn(quizIds, studentIds);

        Map<String, List<QuizAttemptEntity>> grouped = new HashMap<>();
        for (QuizAttemptEntity a : attempts) grouped.computeIfAbsent(a.getStudentId() + ":" + a.getQuizId(), k -> new ArrayList<>()).add(a);

        List<CourseQuizResultItem> rows = new ArrayList<>();
        for (EnrollmentEntity e : enrollments) {
            UserEntity student = userRepository.findById(e.getStudentId()).orElse(null);
            for (QuizEntity q : quizzes) {
                List<QuizAttemptEntity> list = grouped.getOrDefault(e.getStudentId() + ":" + q.getId(), List.of());
                BigDecimal best = list.stream().map(QuizAttemptEntity::getScorePct).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                rows.add(new CourseQuizResultItem(e.getStudentId(), student != null ? student.getFullName() : e.getStudentId().toString(), q.getId(), q.getTitle(), best, list.size()));
            }
        }

        return new PageResponse<>(rows, enrollmentsPage.getNumber(), enrollmentsPage.getSize(), enrollmentsPage.getTotalElements(), enrollmentsPage.getTotalPages());
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
