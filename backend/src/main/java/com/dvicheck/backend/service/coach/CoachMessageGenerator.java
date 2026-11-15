package com.dvicheck.backend.service.coach;

import com.dvicheck.backend.service.GeminiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns {@link CoachFacts} into a four-part weekly coach message. Never throws: any AI
 * failure, timeout or validation miss returns the deterministic {@link #buildFallback}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CoachMessageGenerator {

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_FALLBACK = "FALLBACK";

    // Match the V14 column sizes
    static final int MAX_HEADLINE = 120;
    static final int MAX_PART = 300;

    static final double TEMPERATURE = 0;
    static final int MAX_OUTPUT_TOKENS = 300;
    static final int THINKING_BUDGET = 0;

    // Verbatim from the Day 35 spec. It asks for shorter text (80/160) than validation
    // enforces (120/300 = the V14 column sizes) — the prompt is the target, the limits the hard stop.
    static final String COACH_SYSTEM_PROMPT = """
        You are a friendly, practical weekly spending coach inside a household budgeting app.
        You receive one JSON object of facts about the user's past week. Every amount, percentage
        and date label in it is already formatted; copy them exactly as written.

        Reply with ONLY a JSON object with exactly these keys:
          "headline": one short sentence summarising the week (max 80 characters; it is shown as a push notification)
          "win":      one sentence on something that went well (max 160 characters)
          "watch":    one sentence on something to keep an eye on (max 160 characters)
          "action":   one specific, doable suggestion for next week (max 160 characters)

        Rules:
        - Use ONLY numbers, names and dates that appear in the facts. Never calculate, estimate,
          round, convert or invent a number, date or percentage.
        - changePercent already includes its sign: write "up 12%" or "down 8%", never "up +12%".
        - "win" and "watch" must be about different facts.
        - If a fact is null or missing, do not mention it.
        - No investment, tax or legal advice. No shaming or moralising language.
        - Plain, warm, direct sentences. No emojis, no markdown, no lists.
        - Never mention AI, the app's name, or yourself.""";

    // Digit runs with optional thousands separators and decimals: 142.30, 1,234.56, 12, 2026
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern MARKDOWN_FENCE = Pattern.compile("```(?:json)?");
    // "up +12%" / "down -8%" — the prompt forbids it; changePercent already carries the sign
    private static final Pattern DOUBLED_SIGN = Pattern.compile("(?i)\\b(up|down)\\s+[+-]\\d");

    private final GeminiClient geminiClient;
    private final CoachFactsBuilder factsBuilder;
    private final ObjectMapper objectMapper;

    public CoachDraft generate(CoachFacts facts) {
        String factsJson;
        try {
            factsJson = factsBuilder.toJson(facts);
        } catch (Exception e) {
            log.warn("Coach generation: facts serialisation failed ({})", e.getClass().getSimpleName());
            return buildFallback(facts, null);
        }

        try {
            ParsedDraft d = parse(objectMapper, callModel(geminiClient, factsJson));
            Optional<String> rejection = validate(d, factsJson);
            if (rejection.isPresent()) {
                // Reason only — never the model output or facts (store names etc.) in logs
                log.warn("Coach generation: AI draft rejected ({}); using fallback", rejection.get());
                return buildFallback(facts, factsJson);
            }
            return new CoachDraft(d.headline(), d.win(), d.watch(), d.action(), SOURCE_AI, factsJson);
        } catch (Exception e) {
            log.warn("Coach generation: AI call failed ({}); using fallback", safeReason(e));
            return buildFallback(facts, factsJson);
        }
    }

    // ---- separately testable pieces ----

    /**
     * The one model call. Throws on any transport/HTTP/finish/empty failure. Thinking is off
     * (budget 0): with it on, the thinking model spent the whole 300-token cap reasoning and
     * every live call ended MAX_TOKENS → fallback.
     */
    static String callModel(GeminiClient client, String factsJson) throws Exception {
        return client.generate(COACH_SYSTEM_PROMPT, factsJson, TEMPERATURE, MAX_OUTPUT_TOKENS, true, THINKING_BUDGET);
    }

    record ParsedDraft(String headline, String win, String watch, String action) {}

    /** Strips markdown fences defensively (JSON mode shouldn't produce them), then reads the four keys. */
    static ParsedDraft parse(ObjectMapper mapper, String raw) throws Exception {
        String cleaned = MARKDOWN_FENCE.matcher(raw).replaceAll("").trim();
        JsonNode node = mapper.readTree(cleaned);
        return new ParsedDraft(text(node, "headline"), text(node, "win"), text(node, "watch"), text(node, "action"));
    }

    /** Empty when the draft is usable; otherwise a short, data-free reason for the log. */
    static Optional<String> validate(ParsedDraft d, String factsJson) {
        if (isBlank(d.headline()) || isBlank(d.win()) || isBlank(d.watch()) || isBlank(d.action())) {
            return Optional.of("missing or blank field");
        }
        if (d.headline().length() > MAX_HEADLINE) {
            return Optional.of("headline too long");
        }
        if (d.win().length() > MAX_PART || d.watch().length() > MAX_PART || d.action().length() > MAX_PART) {
            return Optional.of("part too long");
        }
        String output = String.join(" ", d.headline(), d.win(), d.watch(), d.action());
        if (!numbersGrounded(output, factsJson)) {
            return Optional.of("ungrounded number");
        }
        if (hasDoubledSign(output)) {
            return Optional.of("doubled sign");
        }
        return Optional.empty();
    }

    /**
     * True when every number token in the output also appears as a number token in the facts
     * JSON. Thousands separators are ignored on both sides ("1,234.56" == "1234.56"); a
     * trailing sentence comma/period is not part of a token. Deliberately strict: "142" does
     * not match a fact of "142.30" — the prompt says to copy figures exactly.
     */
    static boolean numbersGrounded(String output, String factsJson) {
        Set<String> allowed = numberTokens(factsJson);
        for (String token : numberTokens(output)) {
            if (!allowed.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Log-safe failure reason. Jackson parse errors quote the offending input (e.g.
     * "Unrecognized token 'Costco'"), i.e. model output that can contain store names — so only
     * their type is logged. Other messages (GeminiClient's "Gemini HTTP 503", "finishReason
     * MAX_TOKENS", timeouts) never contain model or user text and are kept.
     */
    static String safeReason(Exception e) {
        String type = e.getClass().getSimpleName();
        if (e instanceof com.fasterxml.jackson.core.JacksonException || e.getMessage() == null) {
            return type;
        }
        return type + ": " + e.getMessage();
    }

    /** True for "up +12%" / "Down -8%" style double signs. */
    static boolean hasDoubledSign(String text) {
        return text != null && DOUBLED_SIGN.matcher(text).find();
    }

    private static Set<String> numberTokens(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        Matcher m = NUMBER_TOKEN.matcher(text);
        while (m.find()) {
            String token = m.group();
            // "1,234.56" -> "1234.56"; a comma followed by exactly 3 digits is a thousands separator
            tokens.add(token.replaceAll(",(?=\\d{3}(?!\\d))", ""));
        }
        return tokens;
    }

    /** Deterministic, no AI; only references non-null facts. */
    static CoachDraft buildFallback(CoachFacts facts, String factsJson) {
        String headline = "Your week: " + facts.totalSpent();

        String change = facts.changePercent();
        String win = change != null && change.startsWith("-")
            ? "Spending is down " + change.substring(1) + " from the week before."
            : "You logged " + facts.receiptCount() + (facts.receiptCount() == 1 ? " receipt" : " receipts")
                + " - nice tracking.";

        String watch;
        if (facts.topCategory() != null) {
            watch = "Biggest category: " + facts.topCategory().name()
                + " (" + facts.topCategory().sharePercent() + " of the week).";
        } else if (facts.topStore() != null) {
            watch = "Most of your spending was at " + facts.topStore().name() + ".";
        } else {
            watch = "Keep scanning receipts to see where your money goes.";
        }

        String action = facts.budget() != null
            ? "Check your budget progress on the Home tab."
            : "Set a monthly budget in Profile to get alerts.";

        return new CoachDraft(headline, win, watch, action, SOURCE_FALLBACK, factsJson);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
