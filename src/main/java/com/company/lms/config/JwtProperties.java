package com.company.lms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long accessMinutes, long refreshDays, String issuer) {
}
