package com.company.lms.courses.modules.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.modules.dto.CreateModuleRequest;
import com.company.lms.courses.modules.dto.ModuleResponse;
import com.company.lms.courses.modules.dto.ReorderModulesRequest;
import com.company.lms.courses.modules.dto.UpdateModuleRequest;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.mapper.CourseModuleMapper;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.courses.service.CourseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseModuleService {
    private final CourseModuleRepository moduleRepository;
    private final CourseModuleMapper moduleMapper;
    private final CourseService courseService;
    private final CourseAccessService accessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ModuleResponse create(UUID courseId, CreateModuleRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseEntity course = courseService.getCourse(courseId);
        accessService.assertCanManageCourse(actor, course);

        CourseModuleEntity module = new CourseModuleEntity();
        module.setId(UUID.randomUUID());
        module.setCourseId(courseId);
        module.setTitle(request.title().trim());
        module.setSortOrder((int) moduleRepository.countByCourseId(courseId));

        CourseModuleEntity saved = moduleRepository.save(module);
        ModuleResponse response = moduleMapper.toResponse(saved);
        auditService.log(actor.getId(), "MODULE_CREATE", "MODULE", saved.getId().toString(), null, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public ModuleResponse update(UUID moduleId, UpdateModuleRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseModuleEntity module = getModule(moduleId);
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);

        String before = toJson(moduleMapper.toResponse(module));
        module.setTitle(request.title().trim());
        CourseModuleEntity saved = moduleRepository.save(module);

        ModuleResponse response = moduleMapper.toResponse(saved);
        auditService.log(actor.getId(), "MODULE_UPDATE", "MODULE", moduleId.toString(), before, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional
    public void delete(UUID moduleId, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseModuleEntity module = getModule(moduleId);
        CourseEntity course = courseService.getCourse(module.getCourseId());
        accessService.assertCanManageCourse(actor, course);

        String before = toJson(moduleMapper.toResponse(module));
        moduleRepository.delete(module);
        auditService.log(actor.getId(), "MODULE_DELETE", "MODULE", moduleId.toString(), before, null, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    }

    @Transactional
    public List<ModuleResponse> reorder(UUID courseId, ReorderModulesRequest request, String actorEmail, HttpServletRequest httpRequest) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseEntity course = courseService.getCourse(courseId);
        accessService.assertCanManageCourse(actor, course);

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        if (modules.size() != request.moduleIdsInOrder().size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_MODULE_ORDER", "Order list must include all modules");
        }

        Set<UUID> expected = modules.stream().map(CourseModuleEntity::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> actual = new HashSet<>(request.moduleIdsInOrder());
        if (!expected.equals(actual)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_MODULE_ORDER", "Invalid module ids in order");
        }

        int temp = 1000;
        for (CourseModuleEntity m : modules) {
            m.setSortOrder(temp++);
            moduleRepository.save(m);
        }
        moduleRepository.flush();

        for (int i = 0; i < request.moduleIdsInOrder().size(); i++) {
            CourseModuleEntity module = moduleRepository.findByIdAndCourseId(request.moduleIdsInOrder().get(i), courseId)
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "INVALID_MODULE_ORDER", "Invalid module id"));
            module.setSortOrder(i);
            moduleRepository.save(module);
        }

        List<ModuleResponse> response = moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream().map(moduleMapper::toResponse).toList();
        auditService.log(actor.getId(), "MODULE_REORDER", "COURSE", courseId.toString(), null, toJson(response), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> listByCourse(UUID courseId, String actorEmail) {
        UserEntity actor = accessService.requireActor(actorEmail);
        CourseEntity course = courseService.getCourse(courseId);
        accessService.assertCanManageCourse(actor, course);
        return moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream().map(moduleMapper::toResponse).toList();
    }

    public CourseModuleEntity getModule(UUID moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
