package com.company.lms.exam.service;

import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.service.EnrollmentService;
import com.company.lms.exam.entity.QuizEntity;
import com.company.lms.video.service.VideoProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizPolicyService {
    private final CourseAccessService courseAccessService;
    private final EnrollmentService enrollmentService;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final VideoProgressService videoProgressService;

    public UserEntity requireActor(String email) {
        return courseAccessService.requireActor(email);
    }

    public void assertCanManageCourse(UserEntity actor, CourseEntity course) {
        courseAccessService.assertCanManageCourse(actor, course);
    }

    public void assertLessonBelongsToCourse(UUID lessonId, UUID courseId) {
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        if (!module.getCourseId().equals(courseId)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "LESSON_COURSE_MISMATCH", "Lesson does not belong to quiz course");
        }
    }

    public boolean canUnlockQuiz(UUID studentId, UUID lessonId) {
        return lessonId == null || videoProgressService.isLessonCompleted(studentId, lessonId);
    }

    public void assertStudentCanTakeQuiz(UUID studentId, QuizEntity quiz) {
        if (!enrollmentService.hasActiveEnrollment(quiz.getCourseId(), studentId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment required");
        }
        if (!Boolean.TRUE.equals(quiz.getIsActive())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QUIZ_INACTIVE", "Quiz is inactive");
        }
        if (quiz.getLessonId() != null && !canUnlockQuiz(studentId, quiz.getLessonId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "QUIZ_LOCKED", "Lesson video completion is required before starting this quiz");
        }
    }
}
