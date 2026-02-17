package com.company.lms.courses.modules.mapper;

import com.company.lms.courses.modules.dto.ModuleResponse;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CourseModuleMapper {
    ModuleResponse toResponse(CourseModuleEntity entity);
}
