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
public class ClaudeItemAnalyser {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String SYSTEM_PROMPT = """
        You are a spending analyst. Analyse grocery/utility line items and categorise each one.
        Return ONLY a valid JSON array — no markdown, no explanation.
        Categories: ESSENTIAL (staple foods, medications, basics), REDUCIBLE (branded item with cheaper alternative), AVOIDABLE (impulse/luxury/recently bought again), DUPLICATE (bought very recently).
        For each item include: name, category, reason (max 15 words), suggestion (max 20 words or null), savingEstimate (number or null), confidence (0.0-1.0).
        Be direct and specific. Never vague. Consider household size for portion estimates.""";

    @Value("${app.anthropic.api-key}")
    private String apiKey;

    @Value("${app.anthropic.model}")
    private String model;

    @Value("${app.anthropic.max-tokens}")
    private int maxTokens;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    public List<ItemAnalysis> analyseItems(List<ReceiptParser.ParsedLineItem> items, int householdSize) {
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

            // NOTE: temperature is deliberately NOT set here — claude-sonnet-5 rejects the
            // request entirely with 400 "temperature is deprecated for this model" if it's
            // present at all (verified directly against the API), even at 0.
            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
            );
            String json = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Claude API call failed, status={}", response.code());
                    return fallback(items);
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String text = extractTextContent(root);

                String cleanedJson = text.replaceAll("```json|```", "").trim();
                JsonNode analysisArray = objectMapper.readTree(cleanedJson);

                List<ItemAnalysis> analyses = new ArrayList<>();
                for (JsonNode node : analysisArray) {
                    String name = node.path("name").asText();
                    String category = node.path("category").asText("ESSENTIAL");
                    String reason = node.path("reason").asText("");
                    String suggestion = node.hasNonNull("suggestion") ? node.path("suggestion").asText() : null;
                    BigDecimal savingEstimate = node.hasNonNull("savingEstimate")
                        ? BigDecimal.valueOf(node.path("savingEstimate").asDouble())
                        : null;
                    double confidence = node.path("confidence").asDouble(0.5);

                    analyses.add(new ItemAnalysis(name, category, reason, suggestion, savingEstimate, confidence));
                }

                if (analyses.isEmpty()) {
                    log.warn("Claude returned no usable item analyses");
                    return fallback(items);
                }

                log.info("ItemAnalyser: analysed {} items", analyses.size());
                return analyses;
            }
        } catch (Exception e) {
            log.warn("ClaudeItemAnalyser failed", e);
            return fallback(items);
        }
    }

    // Claude's content array isn't always [textBlock] — when extended thinking triggers,
    // it's [thinkingBlock, textBlock] (or more). content[0] is NOT reliably the text block,
    // so we must scan for the first block whose type is actually "text".
    private String extractTextContent(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }
        return "";
    }

    private List<ItemAnalysis> fallback(List<ReceiptParser.ParsedLineItem> items) {
        log.warn("ClaudeItemAnalyser falling back to default ESSENTIAL categorisation");
        return items.stream()
            .map(item -> new ItemAnalysis(
                item.name(),
                "ESSENTIAL",
                "Default categorisation — AI analysis unavailable",
                null,
                null,
                0.5))
            .toList();
    }

    public record ItemAnalysis(
            String name,
            String category,
            String reason,
            String suggestion,
            BigDecimal savingEstimate,
            double confidence
    ) {}
}
