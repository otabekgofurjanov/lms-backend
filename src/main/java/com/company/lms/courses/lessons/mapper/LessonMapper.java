package com.company.lms.courses.lessons.mapper;

import com.company.lms.courses.lessons.dto.LessonResponse;
import com.company.lms.courses.lessons.entity.LessonEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LessonMapper {
    LessonResponse toResponse(LessonEntity entity);
}
