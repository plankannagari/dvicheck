package com.dvicheck.backend.dto;

import java.util.UUID;

public record AuthResponse(String accessToken, String refreshToken, UUID userId, String phone, Boolean onboardingCompleted) {}
