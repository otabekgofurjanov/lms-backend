package com.company.lms.auth.service;

import com.company.lms.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, String roles) {
        Instant now = Instant.now();
        return Jwts.builder().subject(userId.toString()).issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(jwtProperties.accessMinutes(), ChronoUnit.MINUTES)))
                .claims(Map.of("email", email, "roles", roles, "type", "access"))
                .signWith(key).compact();
    }

    public String generateRefreshToken(UUID userId, String jti) {
        Instant now = Instant.now();
        return Jwts.builder().subject(userId.toString()).id(jti).issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(jwtProperties.refreshDays(), ChronoUnit.DAYS)))
                .claim("type", "refresh").signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
