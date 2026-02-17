package com.company.lms.users.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.users.dto.*;
import com.company.lms.users.entity.RoleAdminEntity;
import com.company.lms.users.entity.UserAdminEntity;
import com.company.lms.users.mapper.UserAdminMapper;
import com.company.lms.users.repository.PermissionAdminRepository;
import com.company.lms.users.repository.RoleAdminRepository;
import com.company.lms.users.repository.UserAdminRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserAdminService {
    private static final String DEFAULT_USER_STATUS = "ACTIVE";
    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserAdminRepository userRepository;
    private final RoleAdminRepository roleRepository;
    private final PermissionAdminRepository permissionRepository;
    private final UserAdminMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID actorId, HttpServletRequest httpRequest) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "EMAIL_EXISTS", "Email already exists");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PHONE_EXISTS", "Phone already exists");
        }

        UserAdminEntity user = new UserAdminEntity();
        user.setId(UUID.randomUUID());
        user.setFullName(request.fullName());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(DEFAULT_USER_STATUS);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setRoles(resolveRoles(request.roles()));

        UserAdminEntity saved = userRepository.save(user);
        auditService.log(actorId, "USER_CREATE", "USER", saved.getId().toString(), null, toJson(saved), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, UUID actorId, HttpServletRequest httpRequest) {
        UserAdminEntity user = getActiveOrBlocked(id);
        String before = toJson(userMapper.toResponse(user));

        if (userRepository.existsByPhoneAndIdNot(request.phone().trim(), id)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PHONE_EXISTS", "Phone already exists");
        }

        user.setFullName(request.fullName());
        user.setPhone(request.phone().trim());
        user.setUpdatedAt(OffsetDateTime.now());
        user.setRoles(resolveRoles(request.roles()));

        UserAdminEntity saved = userRepository.save(user);
        auditService.log(actorId, "USER_UPDATE", "USER", saved.getId().toString(), before, toJson(userMapper.toResponse(saved)), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UpdateUserStatusRequest request, UUID actorId, HttpServletRequest httpRequest) {
        UserAdminEntity user = getActiveOrBlocked(id);
        String before = toJson(userMapper.toResponse(user));

        user.setStatus(request.status());
        user.setUpdatedAt(OffsetDateTime.now());

        UserAdminEntity saved = userRepository.save(user);
        auditService.log(actorId, "USER_STATUS_CHANGE", "USER", saved.getId().toString(), before, toJson(userMapper.toResponse(saved)), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size, String search) {
        Page<UserAdminEntity> users = userRepository.search(normalizeSearch(search), PageRequest.of(page, size));
        return new PageResponse<>(
                users.stream().map(userMapper::toResponse).toList(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return userMapper.toResponse(getActiveOrBlocked(id));
    }


    @Transactional(readOnly = true)
    public UUID getIdByEmail(String email) {
        UserAdminEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        return user.getId();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getPermissions() {
        return permissionRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Transactional
    public UserImportResultResponse importUsers(MultipartFile file, UUID actorId, HttpServletRequest httpRequest) {
        int total = 0, created = 0, updated = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null || !line.equalsIgnoreCase("fullName,email,phone,role")) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_CSV_HEADER", "CSV header must be fullName,email,phone,role");
            }

            int row = 1;
            while ((line = reader.readLine()) != null) {
                row++;
                if (line.isBlank()) {
                    continue;
                }
                total++;
                try {
                    String[] parts = line.split(",");
                    if (parts.length != 4) {
                        throw new IllegalArgumentException("Invalid column count");
                    }
                    String fullName = parts[0].trim();
                    String email = parts[1].trim().toLowerCase();
                    String phone = parts[2].trim();
                    String roleCode = parts[3].trim().toUpperCase();
                    if (!("TEACHER".equals(roleCode) || "STUDENT".equals(roleCode))) {
                        throw new IllegalArgumentException("Role must be TEACHER or STUDENT");
                    }

                    Optional<UserAdminEntity> existing = userRepository.findByEmail(email);
                    if (existing.isEmpty() && userRepository.existsByPhone(phone)) {
                        throw new IllegalArgumentException("Phone already exists");
                    }
                    if (existing.isPresent()) {
                        UserAdminEntity user = existing.get();
                        user.setFullName(fullName);
                        user.setPhone(phone);
                        user.setStatus("ACTIVE");
                        user.setRoles(resolveRoles(Set.of(roleCode)));
                        user.setUpdatedAt(OffsetDateTime.now());
                        userRepository.save(user);
                        updated++;
                    } else {
                        UserAdminEntity user = new UserAdminEntity();
                        user.setId(UUID.randomUUID());
                        user.setFullName(fullName);
                        user.setEmail(email);
                        user.setPhone(phone);
                        user.setPasswordHash(passwordEncoder.encode(generatePassword(10)));
                        user.setStatus("ACTIVE");
                        user.setCreatedAt(OffsetDateTime.now());
                        user.setUpdatedAt(OffsetDateTime.now());
                        user.setRoles(resolveRoles(Set.of(roleCode)));
                        userRepository.save(user);
                        created++;
                    }
                } catch (Exception ex) {
                    failed++;
                    errors.add("row " + row + ": " + ex.getMessage());
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "CSV_READ_ERROR", "Failed to read CSV: " + e.getMessage());
        }

        UserImportResultResponse result = new UserImportResultResponse(total, created, updated, failed, errors);
        auditService.log(actorId, "USER_IMPORT", "USER", null, null, toJson(result), httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return result;
    }

    private UserAdminEntity getActiveOrBlocked(UUID id) {
        UserAdminEntity user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        if ("DELETED".equals(user.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        return user;
    }

    private Set<RoleAdminEntity> resolveRoles(Set<String> roles) {
        List<RoleAdminEntity> roleEntities = roleRepository.findByCodeIn(roles.stream().map(String::toUpperCase).toList());
        if (roleEntities.size() != roles.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ROLE_NOT_FOUND", "One or more roles not found");
        }
        return new HashSet<>(roleEntities);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private String generatePassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return builder.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
