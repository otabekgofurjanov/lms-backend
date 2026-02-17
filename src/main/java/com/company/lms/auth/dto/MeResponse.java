package com.company.lms.auth.dto;

import java.util.Set;
import java.util.UUID;

public record MeResponse(UUID id, String fullName, String email, Set<String> roles) {}
