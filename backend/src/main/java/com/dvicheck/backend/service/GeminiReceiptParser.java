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
public class GeminiReceiptParser {

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String SYSTEM_PROMPT = """
        You are a receipt parser. Extract line items from receipt OCR text.
        Return ONLY a valid JSON array — no markdown, no explanation, nothing else.
        Each object must have: name (string), quantity (number), unitPrice (number), totalPrice (number).
        If quantity not shown assume 1. Prices are decimal numbers without currency symbols.
        Skip: store name, address, subtotal, tax, total, payment method, date, thank you messages.
        Skip any item where name is a currency code (USD, AUD, GBP, EUR, INR, CAD, SGD) or clearly not a product.
        If no items found return [].""";

    @Value("${app.google.gemini.api-key}")
    private String apiKey;

    @Value("${app.google.gemini.model}")
    private String model;

    private final ReceiptParser receiptParser;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    public List<ReceiptParser.ParsedLineItem> parse(String rawOcrText) {
        if (rawOcrText == null || rawOcrText.isBlank()) {
            return new ArrayList<>();
        }

        try {
            String userPrompt = "Extract all line items:\n\n" + rawOcrText;

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
                    return fallback(rawOcrText);
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String text = root.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();

                // responseMimeType: application/json means fences shouldn't appear, but strip
                // defensively in case Gemini wraps the output anyway.
                String cleanedJson = text.replaceAll("```json|```", "").trim();
                JsonNode itemsArray = objectMapper.readTree(cleanedJson);

                List<ReceiptParser.ParsedLineItem> items = new ArrayList<>();
                for (JsonNode itemNode : itemsArray) {
                    String name = itemNode.path("name").asText();
                    BigDecimal quantity = BigDecimal.valueOf(itemNode.path("quantity").asDouble(1.0));
                    BigDecimal unitPrice = BigDecimal.valueOf(itemNode.path("unitPrice").asDouble(0.0));
                    BigDecimal totalPrice = BigDecimal.valueOf(itemNode.path("totalPrice").asDouble(0.0));

                    if (name.isBlank() || totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    items.add(new ReceiptParser.ParsedLineItem(name, unitPrice, totalPrice, quantity));
                }

                if (items.isEmpty()) {
                    log.warn("Gemini returned no usable line items");
                    return fallback(rawOcrText);
                }

                log.info("GeminiReceiptParser extracted {} items", items.size());
                return items;
            }
        } catch (Exception e) {
            log.warn("GeminiReceiptParser failed", e);
            return fallback(rawOcrText);
        }
    }

    private List<ReceiptParser.ParsedLineItem> fallback(String rawText) {
        log.warn("GeminiReceiptParser falling back to heuristic parser");
        return receiptParser.parse(rawText).items();
    }
}
