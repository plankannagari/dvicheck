package com.dvicheck.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record AddItemRequest(
        @NotBlank(message = "name is required") String name,
        String quantity
) {
    public String quantityOrDefault() {
        return quantity != null && !quantity.isBlank() ? quantity : "1";
    }
}
