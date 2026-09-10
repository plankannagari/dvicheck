package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.AuthResponse;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final SupabaseAuthClient supabaseAuthClient;

    @Value("${app.auth.dev-mode}")
    private boolean devMode;

    // Only used when devMode is true — real Supabase send/verify goes through supabaseAuthClient.
    private final Map<String, String> devOtpStore = new ConcurrentHashMap<>();

    public void sendOtp(String phone) {
        if (devMode) {
            devOtpStore.put(phone, DEV_OTP_CODE);
            log.info("DEV MODE — OTP for {}: {}", phone, DEV_OTP_CODE);
            return;
        }

        supabaseAuthClient.sendOtp(phone);
        log.info("OTP sent for phone ending in {}", maskPhone(phone));
    }

    @Transactional
    public AuthResponse verifyOtp(String phone, String otp) {
        if (devMode) {
            if (!DEV_OTP_CODE.equals(otp)) {
                throw DvicheckException.badRequest("Invalid or expired OTP");
            }
            devOtpStore.remove(phone);
        } else {
            supabaseAuthClient.verifyOtp(phone, otp);
        }

        User user = userService.findOrCreateByPhone(phone);
        String accessToken = jwtService.generateToken(user.getId());
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        log.info("User authenticated: {}", user.getId());
        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getPhone(), user.getOnboardingCompleted());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
