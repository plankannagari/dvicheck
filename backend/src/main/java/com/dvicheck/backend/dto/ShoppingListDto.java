package com.dvicheck.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record ShoppingListDto(
        UUID id,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt,
        int itemCount
) {}
