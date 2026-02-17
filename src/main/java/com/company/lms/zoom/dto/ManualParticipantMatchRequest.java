package com.company.lms.zoom.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManualParticipantMatchRequest(@NotNull UUID studentId) {
}
