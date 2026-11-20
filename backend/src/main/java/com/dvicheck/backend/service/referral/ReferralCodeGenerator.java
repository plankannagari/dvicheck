package com.dvicheck.backend.service.referral;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// Invite codes: 7 characters from an unambiguous alphabet (no I, L, O, 0, 1), so codes
// read aloud or typed from a screenshot don't get mistyped. 31 chars, 31^7 ≈ 2.75e10 combinations;
// the UNIQUE constraint on users.referral_code is the final guard against a collision.
@Component
public class ReferralCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    // User input -> stored form: trimmed, uppercased, spaces and hyphens removed.
    // "" for null, so callers can treat blank and missing the same way.
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase().replace(" ", "").replace("-", "");
    }
}
