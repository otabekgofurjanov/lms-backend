package com.company.lms.courses.lessons.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.dto.CreateLessonRequest;
import com.company.lms.courses.lessons.dto.LessonResponse;
import com.company.lms.courses.lessons.dto.ReorderLessonsRequest;
import com.company.lms.courses.lessons.dto.UpdateLessonRequest;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.mapper.LessonMapper;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.service.CourseModuleService;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.courses.service.CourseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LessonService {
    private final LessonRepository lessonRepository;
    private final LessonMapper lessonMapper;
    private final CourseModuleService moduleService;
    private final CourseService courseService;
    private final CourseAccessService accessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public LessonResponse create(UUID moduleId, CreateLessonRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseModuleEntity module = moduleService.getModule(moduleId);
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);

        LessonEntity lesson = new LessonEntity();
        lesson.setId(UUID.randomUUID());
        lesson.setModuleId(moduleId);
        lesson.setTitle(request.title().trim());
        lesson.setLessonType(request.lessonType());
        lesson.setAvailableAt(request.availableAt());
        lesson.setSortOrder((int) lessonRepository.countByModuleId(moduleId));
        lesson.setCreatedAt(OffsetDateTime.now());
        lesson.setUpdatedAt(OffsetDateTime.now());

        LessonEntity saved = lessonRepository.save(lesson);
        LessonResponse response = lessonMapper.toResponse(saved);
        auditService.log(actor.getId(), "LESSON_CREATE", "LESSON", saved.getId().toString(), null, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public LessonResponse update(UUID lessonId, UpdateLessonRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        LessonEntity lesson = getLesson(lessonId);
        CourseModuleEntity module = moduleService.getModule(lesson.getModuleId());
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);

        String before = toJson(lessonMapper.toResponse(lesson));
        lesson.setTitle(request.title().trim());
        lesson.setLessonType(request.lessonType());
        lesson.setAvailableAt(request.availableAt());
        lesson.setUpdatedAt(OffsetDateTime.now());

        LessonEntity saved = lessonRepository.save(lesson);
        LessonResponse response = lessonMapper.toResponse(saved);
        auditService.log(actor.getId(), "LESSON_UPDATE", "LESSON", lessonId.toString(), before, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public void delete(UUID lessonId, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        LessonEntity lesson = getLesson(lessonId);
        CourseModuleEntity module = moduleService.getModule(lesson.getModuleId());
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);

        String before = toJson(lessonMapper.toResponse(lesson));
        lessonRepository.delete(lesson);
        auditService.log(actor.getId(), "LESSON_DELETE", "LESSON", lessonId.toString(), before, null, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    }

    @Transactional
    public List<LessonResponse> reorder(UUID moduleId, ReorderLessonsRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseModuleEntity module = moduleService.getModule(moduleId);
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);

        List<LessonEntity> lessons = lessonRepository.findByModuleIdOrderBySortOrderAsc(moduleId);
        if (lessons.size() != request.lessonIdsInOrder().size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_ORDER", "Order list must include all lessons");
        }

        Set<UUID> expected = lessons.stream().map(LessonEntity::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> actual = new HashSet<>(request.lessonIdsInOrder());
        if (!expected.equals(actual)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_ORDER", "Invalid lesson ids in order");
        }

        int temp = 1000;
        for (LessonEntity lesson : lessons) {
            lesson.setSortOrder(temp++);
            lessonRepository.save(lesson);
        }
        lessonRepository.flush();

        for (int i = 0; i < request.lessonIdsInOrder().size(); i++) {
            LessonEntity lesson = getLesson(request.lessonIdsInOrder().get(i));
            if (!lesson.getModuleId().equals(moduleId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_LESSON_ORDER", "Invalid lesson id");
            }
            lesson.setSortOrder(i);
            lessonRepository.save(lesson);
        }

        List<LessonResponse> response = lessonRepository.findByModuleIdOrderBySortOrderAsc(moduleId).stream().map(lessonMapper::toResponse).toList();
        auditService.log(actor.getId(), "LESSON_REORDER", "MODULE", moduleId.toString(), null, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> listByModule(UUID moduleId, String actorEmail) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseModuleEntity module = moduleService.getModule(moduleId);
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);
        return lessonRepository.findByModuleIdOrderBySortOrderAsc(moduleId).stream().map(lessonMapper::toResponse).toList();
    }

    public LessonEntity getLesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
