package com.company.lms.zoom.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zoom")
public record ZoomProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String webhookSecret,
        String apiBaseUrl
) {
    public String oauthBaseUrl() {
        if (apiBaseUrl == null) {
            return "https://zoom.us";
        }
        if (apiBaseUrl.endsWith("/v2")) {
            return apiBaseUrl.substring(0, apiBaseUrl.length() - 3);
        }
        return apiBaseUrl;
    }
}
