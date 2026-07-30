package com.dvicheck.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String phone, Instant createdAt) {}
