package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecentBillDto(
        UUID id,
        String storeName,
        String billType,
        LocalDate purchaseDate,
        BigDecimal totalAmount,
        BigDecimal avoidableAmount,
        String currency,
        int itemCount
) {}
