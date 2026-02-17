package com.company.lms.completion.mapper;

import com.company.lms.completion.entity.CourseProgressEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CompletionMapper {
    default double pct(CourseProgressEntity entity) {
        return entity.getAttendancePct() == null ? 0 : entity.getAttendancePct().doubleValue();
    }
}
