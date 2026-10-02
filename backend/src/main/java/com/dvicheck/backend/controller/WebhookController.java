package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.RevenueCatWebhookPayload;
import com.dvicheck.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final SubscriptionService subscriptionService;

    @Value("${app.revenuecat.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/revenuecat")
    public ResponseEntity<Void> handleRevenueCatWebhook(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody RevenueCatWebhookPayload payload) {
        // A blank secret (env var unset) must reject everything — otherwise an empty
        // Authorization header would equal the empty default and pass.
        if (webhookSecret == null || webhookSecret.isBlank() || !secretMatches(authHeader)) {
            log.warn("RevenueCat webhook: invalid Authorization header");
            return ResponseEntity.status(401).build();
        }
        subscriptionService.handleWebhookEvent(payload.event());
        return ResponseEntity.ok().build();
    }

    private boolean secretMatches(String authHeader) {
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                authHeader.getBytes(StandardCharsets.UTF_8));
    }
}
