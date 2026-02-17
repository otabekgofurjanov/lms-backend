package com.company.lms.courses.mapper;

import com.company.lms.courses.dto.CoursePublicResponse;
import com.company.lms.courses.dto.CourseResponse;
import com.company.lms.courses.entity.CourseEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CourseMapper {
    CourseResponse toResponse(CourseEntity entity);

    @Mapping(target = "shortDescription", expression = "java(toShortDescription(entity.getDescription()))")
    CoursePublicResponse toPublicResponse(CourseEntity entity);

    default String toShortDescription(String description) {
        if (description == null) {
            return null;
        }
        return description.length() <= 120 ? description : description.substring(0, 120);
    }
}
