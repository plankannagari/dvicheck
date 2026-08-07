package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BillDetailResponse(
        UUID id,
        String storeName,
        String billType,
        LocalDate purchaseDate,
        BigDecimal totalAmount,
        BigDecimal avoidableAmount,
        String currency,
        String aiSummary,
        List<LineItemDetail> lineItems
) {
    public record LineItemDetail(
            UUID id,
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            String category,
            String suggestion,
            BigDecimal savingEstimate,
            BigDecimal confidence
    ) {}
}
