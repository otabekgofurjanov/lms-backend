package com.company.lms.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 32) String phone,
        @NotEmpty Set<@Pattern(regexp = "TEACHER|STUDENT|ADMIN") String> roles
) {
}
