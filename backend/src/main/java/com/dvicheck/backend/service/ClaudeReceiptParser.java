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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaudeReceiptParser {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Claude occasionally hallucinates the receipt's total/currency line as if it were a
    // product (observed: a line item literally named "USD"). This is a deterministic
    // safety net independent of prompt wording, since LLM output isn't guaranteed.
    private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final String[] NON_ITEM_KEYWORDS = {
        "total", "subtotal", "tax", "balance", "change", "payment", "thank you"
    };

    private static final String SYSTEM_PROMPT = """
        You are a receipt parser. Extract line items from receipt OCR text.
        Return ONLY a valid JSON array — no markdown, no explanation, nothing else.
        Each object must have: name (string), quantity (number), unitPrice (number), totalPrice (number).
        If quantity is not shown assume 1. Prices are decimal numbers without currency symbols.
        Skip non-items: store name, address, subtotal, tax, total, payment method, thank you messages.
        If the receipt has no items return an empty array [].""";

    @Value("${app.anthropic.api-key}")
    private String apiKey;

    @Value("${app.anthropic.model}")
    private String model;

    @Value("${app.anthropic.max-tokens}")
    private int maxTokens;

    private final ReceiptParser receiptParser;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient();

    public List<ReceiptParser.ParsedLineItem> parse(String rawOcrText) {
        if (rawOcrText == null || rawOcrText.isBlank()) {
            return new ArrayList<>();
        }

        try {
            String userPrompt = "Extract all line items from this receipt OCR text:\n\n" + rawOcrText;

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
                    return fallback(rawOcrText);
                }

                String responseBody = response.body().string();
                JsonNode root = objectMapper.readTree(responseBody);
                String text = extractTextContent(root);

                String cleanedJson = text.replaceAll("```json|```", "").trim();
                JsonNode itemsArray = objectMapper.readTree(cleanedJson);

                List<ReceiptParser.ParsedLineItem> items = new ArrayList<>();
                for (JsonNode itemNode : itemsArray) {
                    String name = itemNode.path("name").asText();
                    BigDecimal quantity = BigDecimal.valueOf(itemNode.path("quantity").asDouble(1.0));
                    BigDecimal unitPrice = BigDecimal.valueOf(itemNode.path("unitPrice").asDouble(0.0));
                    BigDecimal totalPrice = BigDecimal.valueOf(itemNode.path("totalPrice").asDouble(0.0));

                    if (name.isBlank() || totalPrice.compareTo(BigDecimal.ZERO) <= 0 || isLikelyNotAnItem(name)) {
                        continue;
                    }

                    items.add(new ReceiptParser.ParsedLineItem(name, unitPrice, totalPrice, quantity));
                }

                if (items.isEmpty()) {
                    log.warn("Claude returned no usable line items");
                    return fallback(rawOcrText);
                }

                return items;
            }
        } catch (Exception e) {
            log.warn("ClaudeReceiptParser failed", e);
            return fallback(rawOcrText);
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

    private boolean isLikelyNotAnItem(String name) {
        String trimmed = name.trim();
        if (CURRENCY_CODE_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        String lower = trimmed.toLowerCase();
        for (String keyword : NON_ITEM_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<ReceiptParser.ParsedLineItem> fallback(String rawText) {
        log.warn("ClaudeReceiptParser falling back to heuristic parser");
        return receiptParser.parse(rawText).items();
    }
}
