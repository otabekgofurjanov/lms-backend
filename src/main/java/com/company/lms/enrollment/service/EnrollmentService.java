package com.company.lms.enrollment.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.dto.LessonResponse;
import com.company.lms.courses.lessons.mapper.LessonMapper;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.dto.ModuleResponse;
import com.company.lms.courses.modules.mapper.CourseModuleMapper;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.repository.CourseRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.dto.*;
import com.company.lms.enrollment.entity.EnrollmentEntity;
import com.company.lms.enrollment.mapper.EnrollmentMapper;
import com.company.lms.enrollment.repository.EnrollmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EnrollmentService {
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMapper enrollmentMapper;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseAccessService courseAccessService;
    private final CourseModuleRepository moduleRepository;
    private final CourseModuleMapper moduleMapper;
    private final LessonRepository lessonRepository;
    private final LessonMapper lessonMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BulkEnrollmentResponse bulkEnroll(UUID courseId, BulkEnrollmentRequest request, String adminEmail, HttpServletRequest httpRequest) {
        UserEntity admin = courseAccessService.requireActor(adminEmail);
        CourseEntity course = getCourse(courseId);
        validateCourseForEnrollment(course);

        int total = request.studentIds().size();
        int created = 0;
        int reactivated = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (UUID studentId : request.studentIds()) {
            try {
                UserEntity student = getUser(studentId);
                validateStudentRole(student);

                Optional<EnrollmentEntity> existing = enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId);
                if (existing.isPresent()) {
                    EnrollmentEntity enrollment = existing.get();
                    if ("ACTIVE".equals(enrollment.getStatus())) {
                        skipped++;
                    } else {
                        enrollment.setStatus("ACTIVE");
                        enrollmentRepository.save(enrollment);
                        reactivated++;
                    }
                } else {
                    EnrollmentEntity enrollment = new EnrollmentEntity();
                    enrollment.setId(UUID.randomUUID());
                    enrollment.setCourseId(courseId);
                    enrollment.setStudentId(studentId);
                    enrollment.setStatus("ACTIVE");
                    enrollment.setEnrolledAt(OffsetDateTime.now());
                    enrollmentRepository.save(enrollment);
                    created++;
                }
            } catch (Exception ex) {
                failed++;
                errors.add(studentId + ": " + ex.getMessage());
            }
        }

        BulkEnrollmentResponse response = new BulkEnrollmentResponse(total, created, reactivated, skipped, failed, errors);
        auditService.log(admin.getId(), "BULK_ENROLLMENT_CREATE", "COURSE", courseId.toString(), null, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public EnrollmentResponse remove(UUID enrollmentId, String adminEmail, HttpServletRequest httpRequest) {
        UserEntity admin = courseAccessService.requireActor(adminEmail);
        EnrollmentEntity enrollment = getEnrollment(enrollmentId);
        String before = toJson(toResponse(enrollment));

        enrollment.setStatus("REMOVED");
        EnrollmentEntity saved = enrollmentRepository.save(enrollment);
        EnrollmentResponse response = toResponse(saved);

        auditService.log(admin.getId(), "ENROLLMENT_REMOVE", "ENROLLMENT", enrollmentId.toString(), before, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public EnrollmentResponse updateStatus(UUID enrollmentId, UpdateEnrollmentStatusRequest request, String adminEmail, HttpServletRequest httpRequest) {
        UserEntity admin = courseAccessService.requireActor(adminEmail);
        EnrollmentEntity enrollment = getEnrollment(enrollmentId);
        String before = toJson(toResponse(enrollment));

        enrollment.setStatus(request.status());
        EnrollmentEntity saved = enrollmentRepository.save(enrollment);
        EnrollmentResponse response = toResponse(saved);

        auditService.log(admin.getId(), "ENROLLMENT_STATUS_CHANGE", "ENROLLMENT", enrollmentId.toString(), before, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> listCourseEnrollmentsForAdmin(UUID courseId, String search, int page, int size) {
        getCourse(courseId);
        Page<EnrollmentEntity> result = enrollmentRepository.findByCourse(courseId, null, PageRequest.of(page, size));
        List<EnrollmentResponse> filtered = result.stream()
                .map(this::toResponse)
                .filter(r -> matchesSearch(r.student(), search))
                .toList();

        return new PageResponse<>(filtered, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> listCourseEnrollmentsForTeacher(UUID courseId, String teacherEmail, String search, int page, int size) {
        UserEntity teacher = courseAccessService.requireActor(teacherEmail);
        CourseEntity course = getCourse(courseId);
        courseAccessService.assertCanManageCourse(teacher, course);

        Page<EnrollmentEntity> result = enrollmentRepository.findByCourse(courseId, null, PageRequest.of(page, size));
        List<EnrollmentResponse> filtered = result.stream()
                .map(this::toResponse)
                .filter(r -> matchesSearch(r.student(), search))
                .toList();

        return new PageResponse<>(filtered, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentCourseResponse> listStudentCourses(String studentEmail, int page, int size) {
        UserEntity student = courseAccessService.requireActor(studentEmail);
        Page<EnrollmentEntity> result = enrollmentRepository.findActiveByStudent(student.getId(), PageRequest.of(page, size));
        List<StudentCourseResponse> items = result.stream().map(enrollment -> {
            CourseEntity course = getCourse(enrollment.getCourseId());
            return new StudentCourseResponse(course.getId(), course.getTitle(), course.getCoverUrl(), course.getStatus(), enrollment.getEnrolledAt());
        }).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudentCourseDetailResponse studentCourseDetail(UUID courseId, String studentEmail) {
        UserEntity student = courseAccessService.requireActor(studentEmail);
        EnrollmentEntity enrollment = enrollmentRepository.findByCourseIdAndStudentId(courseId, student.getId())
                .orElseThrow(() -> new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Student is not enrolled to this course"));

        if (!"ACTIVE".equals(enrollment.getStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_INACTIVE", "Enrollment is not active");
        }

        CourseEntity course = getCourse(courseId);
        List<StudentCourseDetailResponse.ModuleWithLessons> modules = moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId)
                .stream()
                .map(module -> {
                    ModuleResponse moduleResponse = moduleMapper.toResponse(module);
                    List<LessonResponse> lessons = lessonRepository.findByModuleIdOrderBySortOrderAsc(module.getId()).stream().map(lessonMapper::toResponse).toList();
                    return new StudentCourseDetailResponse.ModuleWithLessons(moduleResponse, lessons);
                }).toList();

        return new StudentCourseDetailResponse(course.getId(), course.getTitle(), course.getDescription(), course.getCoverUrl(), modules);
    }

    public boolean hasActiveEnrollment(UUID courseId, UUID studentId) {
        return enrollmentRepository.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, "ACTIVE");
    }

    private EnrollmentResponse toResponse(EnrollmentEntity entity) {
        EnrollmentResponse base = enrollmentMapper.toResponse(entity);
        UserEntity student = getUser(entity.getStudentId());
        EnrollmentStudentDto studentDto = new EnrollmentStudentDto(student.getId(), student.getFullName(), student.getEmail(), student.getPhone(), student.getStatus());
        return new EnrollmentResponse(base.enrollmentId(), base.courseId(), studentDto, base.enrollmentStatus(), base.enrolledAt());
    }

    private CourseEntity getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
    }

    private EnrollmentEntity getEnrollment(UUID enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found"));
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private void validateStudentRole(UserEntity user) {
        boolean isStudent = user.getRoles().stream().anyMatch(r -> "STUDENT".equals(r.getCode()));
        if (!isStudent) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT", "Only STUDENT users can be enrolled");
        }
    }

    private void validateCourseForEnrollment(CourseEntity course) {
        if ("DRAFT".equals(course.getStatus()) || "ARCHIVED".equals(course.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "COURSE_NOT_ENROLLABLE", "Course must be ACTIVE for enrollment");
        }
    }

    private boolean matchesSearch(EnrollmentStudentDto student, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String q = search.trim().toLowerCase();
        return student.fullName().toLowerCase().contains(q) || student.email().toLowerCase().contains(q);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
