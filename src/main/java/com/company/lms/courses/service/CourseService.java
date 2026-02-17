package com.company.lms.courses.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.dto.*;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.mapper.CourseMapper;
import com.company.lms.courses.repository.CourseRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final CourseAccessService accessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CourseResponse create(CreateCourseRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseEntity course = new CourseEntity();
        course.setId(UUID.randomUUID());
        course.setTitle(request.title().trim());
        course.setDescription(request.description());
        course.setCoverUrl(request.coverUrl());
        course.setStatus("DRAFT");
        course.setCreatedBy(actor.getId());
        course.setCreatedAt(OffsetDateTime.now());
        course.setUpdatedAt(OffsetDateTime.now());

        CourseEntity saved = courseRepository.save(course);
        CourseResponse response = courseMapper.toResponse(saved);
        auditService.log(actor.getId(), "COURSE_CREATE", "COURSE", saved.getId().toString(), null, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public CourseResponse update(UUID courseId, UpdateCourseRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseEntity course = getCourse(courseId);
        accessService.assertCanManageCourse(actor, course);

        String before = toJson(courseMapper.toResponse(course));
        course.setTitle(request.title().trim());
        course.setDescription(request.description());
        course.setCoverUrl(request.coverUrl());
        course.setUpdatedAt(OffsetDateTime.now());

        CourseEntity saved = courseRepository.save(course);
        CourseResponse response = courseMapper.toResponse(saved);
        auditService.log(actor.getId(), "COURSE_UPDATE", "COURSE", courseId.toString(), before, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public CourseResponse updateStatus(UUID courseId, UpdateCourseStatusRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseEntity course = getCourse(courseId);
        accessService.assertCanManageCourse(actor, course);

        String before = toJson(courseMapper.toResponse(course));
        course.setStatus(request.status());
        course.setUpdatedAt(OffsetDateTime.now());

        CourseEntity saved = courseRepository.save(course);
        CourseResponse response = courseMapper.toResponse(saved);
        auditService.log(actor.getId(), "COURSE_STATUS_CHANGE", "COURSE", courseId.toString(), before, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> list(String search, String status, int page, int size) {
        Page<CourseEntity> result = courseRepository.search(normalize(search), normalize(status), PageRequest.of(page, size));
        return new PageResponse<>(result.map(courseMapper::toResponse).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID courseId) {
        return courseMapper.toResponse(getCourse(courseId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CoursePublicResponse> listActive(String search, int page, int size) {
        Page<CourseEntity> result = courseRepository.searchActive(normalize(search), PageRequest.of(page, size));
        return new PageResponse<>(result.map(courseMapper::toPublicResponse).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CoursePublicDetailResponse getActiveDetail(UUID id) {
        CourseEntity course = courseRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
        return new CoursePublicDetailResponse(course.getId(), course.getTitle(), course.getDescription(), course.getCoverUrl(), course.getStatus());
    }

    public CourseEntity getCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
