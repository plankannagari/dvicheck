package com.dvicheck.backend.service;

import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    public void sendNotification(String pushToken, String title, String body) {
        sendNotification(pushToken, title, body, Map.of());
    }

    public void sendNotification(String pushToken, String title, String body, Map<String, Object> data) {
        if (pushToken == null || pushToken.isBlank()) {
            log.debug("Skipping push notification — no push token");
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("to", pushToken);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("sound", "default");
            payload.put("priority", "normal");
            if (data != null && !data.isEmpty()) {
                payload.put("data", data);
            }
            String json = objectMapper.writeValueAsString(payload);

            Request request = new Request.Builder()
                .url(EXPO_PUSH_URL)
                .post(RequestBody.create(json, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                log.info("Push notification sent to {}, status={}", pushToken, response.code());
            }
        } catch (Exception e) {
            log.warn("Failed to send push notification to {}", pushToken, e);
        }
    }

    @Scheduled(cron = "0 0 19 * * SUN")
    public void sendWeeklyInsightsReminder() {
        List<User> users = userRepository.findByPushTokenIsNotNullAndNotificationsEnabledTrue();
        log.info("Sending weekly insights reminder to {} users", users.size());
        for (User user : users) {
            sendNotification(
                user.getPushToken(),
                "Your weekly insights are ready",
                "See how your spending looked this week"
            );
        }
    }
}
