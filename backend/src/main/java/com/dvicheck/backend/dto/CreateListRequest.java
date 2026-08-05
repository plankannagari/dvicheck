package com.dvicheck.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateListRequest(
        @NotBlank(message = "name is required") String name
) {}
