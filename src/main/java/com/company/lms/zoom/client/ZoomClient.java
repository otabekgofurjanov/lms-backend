package com.company.lms.zoom.client;

import com.company.lms.zoom.dto.ZoomOAuthTokenResponse;
import com.company.lms.zoom.dto.ZoomUserProfileResponse;
import com.company.lms.zoom.dto.ZoomMeetingCreateRequest;
import com.company.lms.zoom.dto.ZoomMeetingCreateZoomRequest;
import com.company.lms.zoom.dto.ZoomMeetingCreateZoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ZoomClient {
    private final ZoomProperties properties;

    public ZoomOAuthTokenResponse exchangeCode(String code) {
        return oauthClient().post()
                .uri(uriBuilder -> uriBuilder.path("/oauth/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("code", code)
                        .queryParam("redirect_uri", properties.redirectUri())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .retrieve()
                .bodyToMono(ZoomOAuthTokenResponse.class)
                .block();
    }

    public ZoomUserProfileResponse currentUser(String accessToken) {
        return apiClient(accessToken).get()
                .uri("/users/me")
                .retrieve()
                .bodyToMono(ZoomUserProfileResponse.class)
                .block();
    }

    public ZoomMeetingCreateZoomResponse createMeeting(String accessToken, ZoomMeetingCreateRequest request) {
        ZoomMeetingCreateZoomRequest body = new ZoomMeetingCreateZoomRequest(request.topic(), 2, request.startTime(), request.durationMinutes(), "UTC");
        return apiClient(accessToken).post()
                .uri("/users/me/meetings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(ZoomMeetingCreateZoomResponse.class)
                .block();
    }

    private WebClient oauthClient() {
        return WebClient.builder().baseUrl(properties.oauthBaseUrl()).build();
    }

    private WebClient apiClient(String accessToken) {
        return WebClient.builder().baseUrl(properties.apiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    private String basicAuth() {
        String value = properties.clientId() + ":" + properties.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
