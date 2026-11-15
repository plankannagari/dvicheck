package com.dvicheck.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal shared Gemini generateContent client (Day 35). Same endpoint/config as the older
 * GeminiReceiptParser / GeminiItemAnalyser / SpendingNarrativeService, which still carry
 * their own inline copies — new callers should use this instead.
 *
 * Differences from those copies: explicit timeouts (they run on OkHttp's 10s defaults),
 * the key goes in the x-goog-api-key header rather than the URL query string, and
 * failures THROW (IOException) so each caller decides its own fallback.
 */
@Component
@Slf4j
public class GeminiClient {

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(5))
        .callTimeout(Duration.ofSeconds(20))
        .build();

    public GeminiClient(ObjectMapper objectMapper,
                        @Value("${app.google.gemini.api-key}") String apiKey,
                        @Value("${app.google.gemini.model}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * One generateContent call; returns the first candidate's text.
     *
     * @param jsonResponse   sets responseMimeType=application/json
     * @param thinkingBudget null = model default; 0 disables "thinking". `gemini-flash-latest`
     *                       resolves to a thinking model (gemini-3.8-flash as of Day 35) whose
     *                       hidden reasoning tokens count against maxOutputTokens — with a
     *                       300-token cap it used 289 on thinking and hit MAX_TOKENS
     * @throws IOException on transport errors, timeouts, non-2xx, a non-STOP finish (e.g.
     *                     MAX_TOKENS — truncated output is never returned), or empty text
     */
    public String generate(String systemPrompt, String userContent, double temperature,
                           int maxOutputTokens, boolean jsonResponse, Integer thinkingBudget) throws IOException {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        if (jsonResponse) {
            generationConfig.put("responseMimeType", "application/json");
        }
        if (thinkingBudget != null) {
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", thinkingBudget));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("generationConfig", generationConfig);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", userContent)))));

        Request request = new Request.Builder()
            .url(GEMINI_API_BASE + model + ":generateContent")
            .header("x-goog-api-key", apiKey)
            .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Gemini HTTP " + response.code());
            }
            JsonNode candidate = objectMapper.readTree(response.body().string()).path("candidates").path(0);
            String finishReason = candidate.path("finishReason").asText("");
            if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
                throw new IOException("Gemini finishReason " + finishReason);
            }
            String text = candidate.path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new IOException("Gemini returned empty text");
            }
            return text;
        }
    }
}
