package com.dvicheck.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEntryDto(
        String action,
        UUID targetUserId,
        String details,
        Instant createdAt,
        UUID adminId
) {}
