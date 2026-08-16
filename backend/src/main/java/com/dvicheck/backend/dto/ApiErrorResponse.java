package com.dvicheck.backend.dto;

import java.time.Instant;

public record ApiErrorResponse(boolean success, String code, String message, Instant timestamp) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, code, message, Instant.now());
    }
}
