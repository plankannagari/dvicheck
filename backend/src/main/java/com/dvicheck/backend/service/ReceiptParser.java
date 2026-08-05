package com.dvicheck.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ReceiptParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("\\d+\\.\\d{2}");
    private static final String[] SKIP_KEYWORDS = {
        "total", "tax", "subtotal", "change", "cash", "card", "thank", "receipt", "balance", "visa", "master"
    };

    public ParsedReceipt parse(String rawText) {
        try {
            String[] lines = rawText.split("\n");

            String storeName = findStoreName(lines);
            BigDecimal total = findTotal(lines);
            List<ParsedLineItem> items = findItems(lines);

            return new ParsedReceipt(storeName, items, total);
        } catch (Exception e) {
            log.error("Failed to parse receipt", e);
            return new ParsedReceipt(null, new ArrayList<>(), null);
        }
    }

    private String findStoreName(String[] lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isOnlyDigitsOrSymbols(trimmed)) {
                continue;
            }
            return trimmed;
        }
        return null;
    }

    private BigDecimal findTotal(String[] lines) {
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("total") && !lower.contains("subtotal")) {
                PriceMatch match = lastPrice(line);
                if (match != null) {
                    return match.value();
                }
            }
        }
        return null;
    }

    private List<ParsedLineItem> findItems(String[] lines) {
        List<ParsedLineItem> items = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String lower = trimmed.toLowerCase();
            if (containsSkipKeyword(lower) || isOnlyDigitsOrSymbols(trimmed)) {
                continue;
            }

            PriceMatch match = lastPrice(trimmed);
            if (match == null || match.value().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String name = trimmed.substring(0, match.start())
                .replaceAll("[\\s$:\\-.]+$", "")
                .trim();
            if (name.length() <= 2) {
                continue;
            }

            items.add(new ParsedLineItem(name, match.value(), match.value(), BigDecimal.ONE));
        }
        return items;
    }

    private boolean containsSkipKeyword(String lowerLine) {
        for (String keyword : SKIP_KEYWORDS) {
            if (lowerLine.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnlyDigitsOrSymbols(String line) {
        return line.chars().noneMatch(Character::isLetter);
    }

    private PriceMatch lastPrice(String line) {
        Matcher m = PRICE_PATTERN.matcher(line);
        PriceMatch last = null;
        while (m.find()) {
            last = new PriceMatch(m.start(), new BigDecimal(m.group()));
        }
        return last;
    }

    private record PriceMatch(int start, BigDecimal value) {}

    public record ParsedLineItem(String name, BigDecimal unitPrice, BigDecimal totalPrice, BigDecimal quantity) {}

    public record ParsedReceipt(String storeName, List<ParsedLineItem> items, BigDecimal total) {}
}
