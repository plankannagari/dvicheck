package com.dvicheck.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RevenueCatWebhookPayload(Event event) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
        String type,
        @JsonProperty("app_user_id") String appUserId,
        @JsonProperty("product_id") String productId,
        @JsonProperty("expiration_at_ms") Long expirationAtMs,
        String environment
    ) {}
}
