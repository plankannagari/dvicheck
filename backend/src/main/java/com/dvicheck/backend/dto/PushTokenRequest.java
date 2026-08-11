package com.dvicheck.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record PushTokenRequest(
        @NotBlank(message = "token is required") String token
) {}
