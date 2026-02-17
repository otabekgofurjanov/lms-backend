package com.company.lms.zoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ZoomOAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn
) {
}
