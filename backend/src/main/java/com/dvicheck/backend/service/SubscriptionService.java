package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.RevenueCatWebhookPayload;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserRepository userRepository;

    @Transactional
    public void handleWebhookEvent(RevenueCatWebhookPayload.Event event) {
        UUID userId;
        try {
            userId = UUID.fromString(event.appUserId());
        } catch (IllegalArgumentException e) {
            log.warn("RevenueCat webhook: app_user_id not a valid UUID: {}", event.appUserId());
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("RevenueCat webhook: no user found for id {}", userId);
            return;
        }

        if (event.expirationAtMs() != null) {
            user.setSubscriptionExpiresAt(Instant.ofEpochMilli(event.expirationAtMs()));
            user.setSubscriptionProductId(event.productId());
            userRepository.save(user);
            log.info("Subscription updated for user {}: expires {} ({}, {})",
                userId, user.getSubscriptionExpiresAt(), event.type(), event.environment());
        } else {
            log.info("RevenueCat event {} for user {} had no expiration_at_ms — no change",
                event.type(), userId);
        }
    }
}
