package com.company.lms.zoom.mapper;

import com.company.lms.zoom.dto.ZoomMeetingResponse;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ZoomMeetingMapper {
    ZoomMeetingResponse toResponse(ZoomMeetingEntity entity);
}
