package com.company.lms.zoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZoomMeetingCreateZoomResponse(
        String id,
        @JsonProperty("join_url") String joinUrl
) {
}
