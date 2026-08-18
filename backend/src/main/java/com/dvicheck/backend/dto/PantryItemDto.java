package com.dvicheck.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PantryItemDto(
        UUID id,
        String itemName,
        String normalisedName,
        LocalDate lastBoughtDate,
        int purchaseCount,
        String typicalQuantity,
        int estimatedRemainingDays,
        String depletionStatus
) {}
