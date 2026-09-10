package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record AddManualItemsRequest(List<ManualItem> items) {
    public record ManualItem(String name, BigDecimal unitPrice, BigDecimal totalPrice, Integer quantity) {}
}
