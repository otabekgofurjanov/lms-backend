package com.company.lms.users.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.users.dto.*;
import com.company.lms.users.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUsersController {
    private final UserAdminService userAdminService;

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.create(request, currentUserId(authentication), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.update(id, request, currentUserId(authentication), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.updateStatus(id, request, currentUserId(authentication), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String search,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.list(page, size, search), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> detail(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.getById(id), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RoleResponse>> roles(HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.getRoles(), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PermissionResponse>> permissions(HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.getPermissions(), httpRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/users/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserImportResultResponse> importCsv(@RequestPart("file") MultipartFile file,
                                                            Authentication authentication,
                                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(userAdminService.importUsers(file, currentUserId(authentication), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    private UUID currentUserId(Authentication authentication) {
        return userAdminService.getIdByEmail(authentication.getName());
    }
}
