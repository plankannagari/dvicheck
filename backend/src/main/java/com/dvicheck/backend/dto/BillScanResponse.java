package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BillScanResponse(
        UUID billId,
        String storeName,
        LocalDate purchaseDate,
        BigDecimal totalAmount,
        int itemCount,
        List<LineItemResult> lineItems
) {
    public record LineItemResult(
            UUID id,
            String name,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            String category
    ) {}
}
