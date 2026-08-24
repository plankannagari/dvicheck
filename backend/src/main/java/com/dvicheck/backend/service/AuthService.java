package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.AuthResponse;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEV_OTP_CODE = "123456";

    private final UserService userService;
    private final JwtService jwtService;

    // TODO: wire real Supabase send/verify call (see SupabaseAuthClient) before production —
    // this in-memory store + hardcoded OTP is dev-only
    private final Map<String, String> devOtpStore = new ConcurrentHashMap<>();

    public void sendOtp(String phone) {
        devOtpStore.put(phone, DEV_OTP_CODE);
        log.info("DEV MODE — OTP for {}: {}", phone, DEV_OTP_CODE);
    }

    @Transactional
    public AuthResponse verifyOtp(String phone, String otp) {
        if (!DEV_OTP_CODE.equals(otp)) {
            throw DvicheckException.badRequest("Invalid or expired OTP");
        }

        User user = userService.findOrCreateByPhone(phone);
        String accessToken = jwtService.generateToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        devOtpStore.remove(phone);
        log.info("User authenticated: {}", user.getId());
        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getPhone(), user.getOnboardingCompleted());
    }
}
