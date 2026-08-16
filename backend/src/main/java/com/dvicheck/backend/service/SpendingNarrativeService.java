package com.dvicheck.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpendingNarrativeService {

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String FALLBACK_NARRATIVE =
        "Your weekly spending summary is ready. Check your top items below.";

    private static final String SYSTEM_PROMPT = """
        You are a friendly personal spending coach for a household budget app.
        Write a 2-3 sentence weekly spending summary that is encouraging, specific, and actionable.
        Use the spending data provided. Be warm but direct. Never generic.
        Focus on one key insight and one specific actionable suggestion.
        Do not use bullet points. Plain conversational sentences only.
        Do not mention app names or refer to yourself as an AI.""";

    @Value("${app.google.gemini.api-key}")
    private String apiKey;

    @Value("${app.google.gemini.model}")
    private String model;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Map<String, String> narrativeCache = new ConcurrentHashMap<>();

    public String generateNarrative(NarrativeContext ctx, UUID userId, LocalDate weekStart) {
        String cacheKey = userId + "_" + weekStart;
        if (narrativeCache.containsKey(cacheKey)) {
            log.debug("Returning cached narrative");
            return narrativeCache.get(cacheKey);
        }

        String narrative = fetchNarrative(ctx);
        narrativeCache.put(cacheKey, narrative);
        return narrative;
    }

    public void clearCache() {
        narrativeCache.clear();
    }

    private String fetchNarrative(NarrativeContext ctx) {
        try {
            String userPrompt = buildUserPrompt(ctx);

            // No responseMimeType here (unlike GeminiReceiptParser/GeminiItemAnalyser) — this
            // call produces free-form prose, not JSON, so forcing application/json would fight
            // the output we actually want.
            Map<String, Object> requestBody = Map.of(
                "generationConfig", Map.of("temperature", 0),
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt))))
            );
            String json = objectMapper.writeValueAsString(requestBody);

            String url = GEMINI_API_BASE + model + ":generateContent?key=" + apiKey;

            Request request = new Request.Builder()
                .url(url)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Gemini API call failed, status={}", response.code());
                    return FALLBACK_NARRATIVE;
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();

                if (text.isBlank()) {
                    log.warn("Gemini returned an empty narrative");
                    return FALLBACK_NARRATIVE;
                }

                String narrative = text.trim();
                log.info("SpendingNarrativeService: generated narrative ({} chars)", narrative.length());
                return narrative;
            }
        } catch (Exception e) {
            log.warn("SpendingNarrativeService failed", e);
            return FALLBACK_NARRATIVE;
        }
    }

    private String buildUserPrompt(NarrativeContext ctx) {
        double avoidablePercent = ctx.totalSpent().compareTo(BigDecimal.ZERO) > 0
            ? ctx.avoidableSpend().doubleValue() / ctx.totalSpent().doubleValue() * 100
            : 0.0;
        String topItems = ctx.topItemNames() == null || ctx.topItemNames().isEmpty()
            ? "none"
            : String.join(", ", ctx.topItemNames());

        return """
            Weekly spending data:
            Total spent this week: $%.2f
            Avoidable spend: $%.2f (%.0f%% of total)
            vs last week: %+.1f%%
            Bills scanned: %d
            Household size: %d people
            Most bought this week: %s
            Biggest spend category: %s

            Write a 2-3 sentence personalised spending summary.
            """.formatted(
                ctx.totalSpent(),
                ctx.avoidableSpend(),
                avoidablePercent,
                ctx.vsLastWeekPercent(),
                ctx.billsScanned(),
                ctx.householdSize(),
                topItems,
                ctx.topCategory()
            );
    }

    public record NarrativeContext(
            BigDecimal totalSpent,
            BigDecimal avoidableSpend,
            BigDecimal prevWeekTotal,
            double vsLastWeekPercent,
            int billsScanned,
            int householdSize,
            String topCategory,
            List<String> topItemNames
    ) {}
}
