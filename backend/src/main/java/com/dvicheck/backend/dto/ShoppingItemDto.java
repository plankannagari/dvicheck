package com.dvicheck.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ShoppingItemDto(
        UUID id,
        String name,
        String quantity,
        boolean isChecked,
        boolean isDuplicate,
        String duplicateWarning,
        LocalDate lastPurchasedDate
) {}
