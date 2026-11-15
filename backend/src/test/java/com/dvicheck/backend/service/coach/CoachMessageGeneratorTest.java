package com.dvicheck.backend.service.coach;

import com.dvicheck.backend.service.GeminiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachMessageGeneratorTest {

    @Mock GeminiClient geminiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // toJson only needs the ObjectMapper; the repositories are never touched
    private final CoachFactsBuilder factsBuilder = new CoachFactsBuilder(null, null, null, null, objectMapper);

    private CoachMessageGenerator generator() {
        return new CoachMessageGenerator(geminiClient, factsBuilder, objectMapper);
    }

    // ---- fixtures ----

    private static CoachFacts fullFacts() {
        return new CoachFacts(
            "Oct 26 - Nov 1", "USD 142.30", "USD 127.05", "+12%", 3,
            new CoachFacts.Category("Essential", "USD 90.00", "63%"),
            new CoachFacts.RisingCategory("Avoidable", "+40%"),
            new CoachFacts.Store("Costco", "USD 80.10"),
            "USD 12.50",
            new CoachFacts.Budget("USD 500.00", "61%"),
            "USD");
    }

    private static String draftJson(String headline, String win, String watch, String action) throws Exception {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("headline", headline);
        m.put("win", win);
        m.put("watch", watch);
        m.put("action", action);
        return new ObjectMapper().writeValueAsString(m);
    }

    private static final String VALID = "{\"headline\":\"You spent USD 142.30 this week.\","
        + "\"win\":\"You logged 3 receipts.\","
        + "\"watch\":\"Essential was 63% of the week.\","
        + "\"action\":\"Keep Costco trips under USD 80.10.\"}";

    private void modelReturns(String raw) throws Exception {
        when(geminiClient.generate(anyString(), anyString(), anyDouble(), anyInt(), anyBoolean(), any()))
            .thenReturn(raw);
    }

    // ---- 1-5: generate() ----

    @Test
    void groundedJson_isAccepted_asAi() throws Exception {
        modelReturns(VALID);

        CoachDraft draft = generator().generate(fullFacts());

        assertThat(draft.source()).isEqualTo(CoachMessageGenerator.SOURCE_AI);
        assertThat(draft.headline()).isEqualTo("You spent USD 142.30 this week.");
        assertThat(draft.action()).isEqualTo("Keep Costco trips under USD 80.10.");
        assertThat(draft.factsJson()).isEqualTo(factsBuilder.toJson(fullFacts()));
    }

    @Test
    void numberNotInFacts_fallsBack() throws Exception {
        // Facts say USD 142.30; the model rounded it
        modelReturns(draftJson("You spent USD 140 this week.", "You logged 3 receipts.",
            "Essential was 63% of the week.", "Keep it up."));

        assertThat(generator().generate(fullFacts()).source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    @Test
    void malformedJson_fallsBack() throws Exception {
        modelReturns("{\"headline\": \"You spent USD 142.30\", \"win\": ");

        assertThat(generator().generate(fullFacts()).source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    @Test
    void fencedJson_isStripped_andParsed() throws Exception {
        modelReturns("```json\n" + VALID + "\n```");

        CoachDraft draft = generator().generate(fullFacts());

        assertThat(draft.source()).isEqualTo(CoachMessageGenerator.SOURCE_AI);
        assertThat(draft.watch()).isEqualTo("Essential was 63% of the week.");
    }

    @Test
    void headlineOver120_fallsBack() throws Exception {
        modelReturns(draftJson("x".repeat(121), "You logged 3 receipts.", "Essential led.", "Keep it up."));

        assertThat(generator().generate(fullFacts()).source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    @Test
    void headlineOfExactly120_isAccepted() throws Exception {
        modelReturns(draftJson("x".repeat(120), "You logged 3 receipts.", "Essential led.", "Keep it up."));

        assertThat(generator().generate(fullFacts()).source()).isEqualTo(CoachMessageGenerator.SOURCE_AI);
    }

    @Test
    void blankWin_fallsBack() throws Exception {
        modelReturns(draftJson("You spent USD 142.30.", "   ", "Essential led.", "Keep it up."));

        assertThat(generator().generate(fullFacts()).source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    @Test
    void clientThrows_fallsBack_withoutThrowing() throws Exception {
        when(geminiClient.generate(anyString(), anyString(), anyDouble(), anyInt(), anyBoolean(), any()))
            .thenThrow(new IOException("Gemini HTTP 503"));

        CoachDraft draft = assertDoesNotThrow(() -> generator().generate(fullFacts()));

        assertThat(draft.source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
        // facts are still recorded on the fallback row
        assertThat(draft.factsJson()).isEqualTo(factsBuilder.toJson(fullFacts()));
    }

    @Test
    void clientTimesOut_fallsBack_withoutThrowing() throws Exception {
        when(geminiClient.generate(anyString(), anyString(), anyDouble(), anyInt(), anyBoolean(), any()))
            .thenThrow(new SocketTimeoutException("timeout"));

        CoachDraft draft = assertDoesNotThrow(() -> generator().generate(fullFacts()));

        assertThat(draft.source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    @Test
    void clientThrowsRuntimeException_stillFallsBack() throws Exception {
        when(geminiClient.generate(anyString(), anyString(), anyDouble(), anyInt(), anyBoolean(), any()))
            .thenThrow(new IllegalStateException("boom"));

        assertThat(assertDoesNotThrow(() -> generator().generate(fullFacts())).source())
            .isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    // ---- 6: numbersGrounded / doubled sign ----

    @Test
    void numbersGrounded_trailingPeriod_matchesToken() {
        String facts = factsBuilder.toJson(fullFacts());
        assertThat(CoachMessageGenerator.numbersGrounded("You spent USD 142.30.", facts)).isTrue();
    }

    @Test
    void numbersGrounded_percent_matchesBareNumber() {
        String facts = factsBuilder.toJson(fullFacts()); // contains "+12%"
        assertThat(CoachMessageGenerator.numbersGrounded("Spending was up 12% on last week.", facts)).isTrue();
    }

    @Test
    void numbersGrounded_ignoresThousandsSeparators_bothWays() {
        assertThat(CoachMessageGenerator.numbersGrounded("You spent USD 1,234.50.", "{\"t\":\"USD 1234.50\"}")).isTrue();
        assertThat(CoachMessageGenerator.numbersGrounded("You spent USD 1234.50.", "{\"t\":\"USD 1,234.50\"}")).isTrue();
    }

    @Test
    void numbersGrounded_rejectsRoundedOrInventedNumbers() {
        String facts = factsBuilder.toJson(fullFacts());
        assertThat(CoachMessageGenerator.numbersGrounded("About USD 142 this week.", facts)).isFalse();
        assertThat(CoachMessageGenerator.numbersGrounded("Try to save 20 next week.", facts)).isFalse();
        assertThat(CoachMessageGenerator.numbersGrounded("No figures at all.", facts)).isTrue();
    }

    @Test
    void doubledSign_isRejected() throws Exception {
        assertThat(CoachMessageGenerator.hasDoubledSign("Spending was up +12% on last week.")).isTrue();
        assertThat(CoachMessageGenerator.hasDoubledSign("DOWN -8% overall")).isTrue();
        assertThat(CoachMessageGenerator.hasDoubledSign("Spending was up 12%.")).isFalse();
        assertThat(CoachMessageGenerator.hasDoubledSign("Avoidable rose +40%.")).isFalse();

        // end to end: grounded numbers, but "up +12%" still falls back
        modelReturns(draftJson("Spending was up +12% this week.", "You logged 3 receipts.",
            "Essential was 63% of the week.", "Keep it up."));
        assertThat(generator().generate(fullFacts()).source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
    }

    @Test
    void logReason_neverQuotesModelOutput() {
        Exception parse = parseFailure("{\"headline\": Costco}");
        assertThat(CoachMessageGenerator.safeReason(parse)).doesNotContain("Costco");
        assertThat(CoachMessageGenerator.safeReason(new IOException("Gemini HTTP 503")))
            .isEqualTo("IOException: Gemini HTTP 503");
    }

    private Exception parseFailure(String raw) {
        try {
            CoachMessageGenerator.parse(objectMapper, raw);
            throw new AssertionError("expected a parse failure");
        } catch (Exception e) {
            return e;
        }
    }

    // ---- 7: fallback ----

    @Test
    void fallback_fullFacts() {
        CoachDraft d = CoachMessageGenerator.buildFallback(fullFacts(), "{}");

        assertThat(d.source()).isEqualTo(CoachMessageGenerator.SOURCE_FALLBACK);
        assertThat(d.headline()).isEqualTo("Your week: USD 142.30");
        assertThat(d.win()).isEqualTo("You logged 3 receipts - nice tracking."); // change is +12%, not down
        assertThat(d.watch()).isEqualTo("Biggest category: Essential (63% of the week).");
        assertThat(d.action()).isEqualTo("Check your budget progress on the Home tab.");
    }

    @Test
    void fallback_spendingDown_usesAbsoluteChange() {
        CoachFacts down = new CoachFacts("w", "USD 50.00", "USD 54.35", "-8%", 2,
            new CoachFacts.Category("Essential", "USD 50.00", "100%"), null, null, null, null, "USD");

        assertThat(CoachMessageGenerator.buildFallback(down, null).win())
            .isEqualTo("Spending is down 8% from the week before.");
    }

    @Test
    void fallback_nullPrevWeekAndBudget_onlyMentionsPresentFields() {
        CoachFacts sparse = new CoachFacts("w", "USD 8.99", null, null, 1,
            null, null, null, null, null, "USD");

        CoachDraft d = CoachMessageGenerator.buildFallback(sparse, null);

        assertThat(d.headline()).isEqualTo("Your week: USD 8.99");
        assertThat(d.win()).isEqualTo("You logged 1 receipt - nice tracking.");
        assertThat(d.action()).isEqualTo("Set a monthly budget in Profile to get alerts.");
        for (String part : new String[] {d.headline(), d.win(), d.watch(), d.action()}) {
            assertThat(part).isNotBlank().doesNotContain("null");
        }
    }
}
