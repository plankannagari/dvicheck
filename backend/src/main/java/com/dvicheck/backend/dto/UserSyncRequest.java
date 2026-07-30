package com.dvicheck.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserSyncRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,19}$", message = "must be E.164 format (+12125551234)")
        String phone
) {}
