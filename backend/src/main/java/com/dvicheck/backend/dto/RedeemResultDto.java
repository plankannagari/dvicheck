package com.dvicheck.backend.dto;

public record RedeemResultDto(
        String status,
        int refereeDays,
        String message
) {}
