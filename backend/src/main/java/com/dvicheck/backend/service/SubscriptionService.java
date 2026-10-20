package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.RevenueCatWebhookPayload;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserRepository userRepository;
    private final BillRepository billRepository;

    @Value("${app.monetization.free-scan-limit}")
    private int freeScanLimit;

    /**
     * Throws PRO_REQUIRED (402) if a free user has hit this calendar month's scan cap.
     * Call before OCR so a rejected scan never costs a Vision/Gemini/Claude call.
     */
    @Transactional(readOnly = true)
    public void assertCanScan(User user) {
        if (user.isPro()) {
            return;
        }
        // Same server-local month boundary MonthlyReportService uses (LocalDate.now())
        Instant monthStart = LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant();
        long scansThisMonth = billRepository.countBillsCreatedSince(user.getId(), monthStart);
        if (scansThisMonth >= freeScanLimit) {
            throw DvicheckException.proRequired(
                "You've used your " + freeScanLimit + " free scans this month. " +
                "Upgrade to Pro for unlimited scanning.");
        }
    }

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
