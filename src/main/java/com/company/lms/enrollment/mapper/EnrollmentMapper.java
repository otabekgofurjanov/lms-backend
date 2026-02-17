package com.company.lms.enrollment.mapper;

import com.company.lms.enrollment.dto.EnrollmentResponse;
import com.company.lms.enrollment.entity.EnrollmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {
    @Mapping(target = "enrollmentId", source = "id")
    @Mapping(target = "enrollmentStatus", source = "status")
    @Mapping(target = "student", ignore = true)
    EnrollmentResponse toResponse(EnrollmentEntity entity);
}
