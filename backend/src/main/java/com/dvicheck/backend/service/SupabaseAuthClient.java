package com.dvicheck.backend.service;

import com.dvicheck.backend.exception.DvicheckException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class SupabaseAuthClient {

    private final RestClient restClient;

    public SupabaseAuthClient(
            @Value("${dvicheck.supabase.url}") String supabaseUrl,
            @Value("${dvicheck.supabase.anon-key}") String anonKey
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl + "/auth/v1")
                .defaultHeader("apikey", anonKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public void sendOtp(String phone) {
        try {
            restClient.post()
                    .uri("/otp")
                    .body(Map.of("phone", phone))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw DvicheckException.badRequest("Failed to send OTP");
        }
    }

    public void verifyOtp(String phone, String otp) {
        try {
            restClient.post()
                    .uri("/verify")
                    .body(Map.of("type", "sms", "phone", phone, "token", otp))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw DvicheckException.badRequest("Invalid or expired OTP");
        }
    }
}
