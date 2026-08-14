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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiItemAnalyser {

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String SYSTEM_PROMPT = """
        You are a spending analyst. Analyse grocery/utility line items and categorise each one.
        Return ONLY a valid JSON array — no markdown, no explanation.
        Categories: ESSENTIAL (staple foods, medications, basics), REDUCIBLE (branded item with cheaper alternative), AVOIDABLE (impulse/luxury/recently bought again), DUPLICATE (bought very recently).
        For each item include: name, category, reason (max 15 words), suggestion (max 20 words or null), savingEstimate (number or null), confidence (0.0-1.0).
        Be direct and specific. Never vague. Consider household size for portion estimates.""";

    @Value("${app.google.gemini.api-key}")
    private String apiKey;

    @Value("${app.google.gemini.model}")
    private String model;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    // Reuses ClaudeItemAnalyser.ItemAnalysis rather than declaring its own record, so
    // BillScanController can switch between the two analysers via config with no code change.
    public List<ClaudeItemAnalyser.ItemAnalysis> analyseItems(
            List<ReceiptParser.ParsedLineItem> items, int householdSize) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<Map<String, Object>> itemPayload = items.stream()
                .map(item -> Map.<String, Object>of("name", item.name(), "price", item.totalPrice()))
                .toList();
            String itemsJson = objectMapper.writeValueAsString(itemPayload);

            String userPrompt = "Household size: " + householdSize + " people.\n"
                + "Analyse these grocery items and categorise each:\n" + itemsJson;

            Map<String, Object> requestBody = Map.of(
                "generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json"),
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
                    return fallback(items);
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();

                // responseMimeType: application/json means fences shouldn't appear, but strip
                // defensively in case Gemini wraps the output anyway.
                String cleanedJson = text.replaceAll("```json|```", "").trim();
                JsonNode analysisArray = objectMapper.readTree(cleanedJson);

                List<ClaudeItemAnalyser.ItemAnalysis> analyses = new ArrayList<>();
                for (JsonNode node : analysisArray) {
                    String name = node.path("name").asText();
                    String category = node.path("category").asText("ESSENTIAL");
                    String reason = node.path("reason").asText("");
                    String suggestion = node.hasNonNull("suggestion") ? node.path("suggestion").asText() : null;
                    BigDecimal savingEstimate = node.hasNonNull("savingEstimate")
                        ? BigDecimal.valueOf(node.path("savingEstimate").asDouble())
                        : null;
                    double confidence = node.path("confidence").asDouble(0.5);

                    analyses.add(new ClaudeItemAnalyser.ItemAnalysis(
                        name, category, reason, suggestion, savingEstimate, confidence));
                }

                if (analyses.isEmpty()) {
                    log.warn("Gemini returned no usable item analyses");
                    return fallback(items);
                }

                log.info("GeminiItemAnalyser: analysed {} items", analyses.size());
                return analyses;
            }
        } catch (Exception e) {
            log.warn("GeminiItemAnalyser failed", e);
            return fallback(items);
        }
    }

    private List<ClaudeItemAnalyser.ItemAnalysis> fallback(List<ReceiptParser.ParsedLineItem> items) {
        log.warn("GeminiItemAnalyser falling back to default ESSENTIAL categorisation");
        return items.stream()
            .map(item -> new ClaudeItemAnalyser.ItemAnalysis(
                item.name(),
                "ESSENTIAL",
                "Default categorisation — AI analysis unavailable",
                null,
                null,
                0.5))
            .toList();
    }
}
