package com.company.lms.auth.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.dto.AuthTokensDto;
import com.company.lms.auth.dto.LoginRequest;
import com.company.lms.auth.dto.MeResponse;
import com.company.lms.auth.dto.RefreshRequest;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.entity.UserSessionEntity;
import com.company.lms.auth.mapper.AuthMapper;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.auth.repository.UserSessionRepository;
import com.company.lms.common.exception.AppException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuthMapper authMapper;
    private final AuditService auditService;

    @Transactional
    public AuthTokensDto login(LoginRequest request, HttpServletRequest servletRequest) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserEntity user = userRepository.findByEmail(request.email()).orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User is not active");
        }
        String jti = UUID.randomUUID().toString();
        var access = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().toString());
        var refresh = jwtService.generateRefreshToken(user.getId(), jti);

        UserSessionEntity session = new UserSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setRefreshJti(jti);
        session.setExpiresAt(OffsetDateTime.now().plusDays(7));
        session.setUserAgent(servletRequest.getHeader("User-Agent"));
        session.setIp(servletRequest.getRemoteAddr());
        session.setCreatedAt(OffsetDateTime.now());
        userSessionRepository.save(session);
        auditService.log(user.getId(), "LOGIN", "AUTH", user.getId().toString(), null, "{}", servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
        return new AuthTokensDto(access, refresh);
    }

    @Transactional
    public AuthTokensDto refresh(RefreshRequest request) {
        Claims claims = jwtService.parse(request.refreshToken());
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid token type");
        }
        UserSessionEntity session = userSessionRepository.findByRefreshJti(claims.getId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "SESSION_NOT_FOUND", "Session not found"));
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "Session revoked or expired");
        }
        UserEntity user = userRepository.findById(UUID.fromString(claims.getSubject()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "User not found"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User is not active");
        }

        return new AuthTokensDto(
                jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().toString()),
                request.refreshToken());
    }

    @Transactional
    public void logout(RefreshRequest request, HttpServletRequest servletRequest) {
        Claims claims = jwtService.parse(request.refreshToken());
        UserSessionEntity session = userSessionRepository.findByRefreshJti(claims.getId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "SESSION_NOT_FOUND", "Session not found"));
        session.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userSessionRepository.save(session);
        auditService.log(UUID.fromString(claims.getSubject()), "LOGOUT", "AUTH", claims.getSubject(), null, "{}", servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
    }

    public MeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = userRepository.findByEmail(auth.getName()).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User is not active");
        }
        return authMapper.toMe(user);
    }
}
