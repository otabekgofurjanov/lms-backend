package com.company.lms.zoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record ZoomMeetingCreateZoomRequest(
        String topic,
        Integer type,
        @JsonProperty("start_time") OffsetDateTime startTime,
        Integer duration,
        String timezone
) {
}
