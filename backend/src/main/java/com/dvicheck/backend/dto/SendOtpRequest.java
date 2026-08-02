package com.dvicheck.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendOtpRequest(
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,19}$", message = "Phone number is required")
        String phone
) {}
