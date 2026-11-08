package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportPdfServiceTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Mock MonthlyReportService monthlyReportService;
    @Mock UserRepository userRepository;

    @InjectMocks MonthlyReportPdfService service;

    private final UUID userId = UUID.randomUUID();

    // ---- fixtures ----

    private static MonthlyReportDto report(String totalSpent, String budget, String prevMonthTotal,
                                           Map<String, BigDecimal> categories,
                                           List<MonthlyReportDto.StoreTotal> stores) {
        return new MonthlyReportDto(
            OCTOBER.atDay(1), OCTOBER.atEndOfMonth(),
            new BigDecimal(totalSpent), new BigDecimal("120.00"),
            budget == null ? null : new BigDecimal(budget),
            budget == null ? 0.0 : 82.3,
            12,
            prevMonthTotal == null ? null : new BigDecimal(prevMonthTotal),
            -4.5,
            categories, stores);
    }

    private static MonthlyReportDto fullReport() {
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        categories.put("ESSENTIAL", new BigDecimal("612.40"));
        categories.put("REDUCIBLE", new BigDecimal("143.10"));
        categories.put("AVOIDABLE", new BigDecimal("88.75"));
        categories.put("DUPLICATE", new BigDecimal("12.00"));
        return report("1234.56", "1500.00", "1293.00", categories, List.of(
            new MonthlyReportDto.StoreTotal("Trader Joe's", new BigDecimal("402.10"), 5),
            new MonthlyReportDto.StoreTotal("Costco", new BigDecimal("380.00"), 2),
            new MonthlyReportDto.StoreTotal("Fresh Mart", new BigDecimal("210.46"), 4)));
    }

    private User user(boolean pro) {
        User user = User.builder()
            .id(userId)
            .currency("USD")
            .subscriptionExpiresAt(pro ? Instant.now().plus(30, ChronoUnit.DAYS) : null)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        return user;
    }

    private static void assertValidPdf(byte[] pdf) {
        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        int tailStart = Math.max(0, pdf.length - 32);
        String tail = new String(pdf, tailStart, pdf.length - tailStart, StandardCharsets.ISO_8859_1);
        assertThat(tail).contains("%%EOF");
    }

    // Page content is Flate-compressed, so read it back through OpenPDF rather than grepping bytes
    private static String extractText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    // ---- generate() ----

    @Test
    void generate_fullReport_isValidPdf() throws IOException {
        byte[] pdf = service.generate(fullReport(), OCTOBER, "USD");

        assertValidPdf(pdf);
        assertThat(pdf.length).isGreaterThan(1500);
        assertThat(extractText(pdf))
            .contains("Monthly report - October 2026")
            .contains("USD 1,234.56")
            .contains("Trader Joe's")
            .contains("Essential");
    }

    @Test
    void generate_noBudget_noPreviousMonth_stillValid() throws IOException {
        // vsLastMonthPercent is a primitive double in MonthlyReportDto and can't be null —
        // the real "not available" signal is a null/zero prevMonthTotal
        MonthlyReportDto report = report("200.00", null, null,
            Map.of("ESSENTIAL", new BigDecimal("200.00")),
            List.of(new MonthlyReportDto.StoreTotal("Costco", new BigDecimal("200.00"), 1)));

        byte[] pdf = service.generate(report, OCTOBER, "EUR");

        assertValidPdf(pdf);
        assertThat(extractText(pdf))
            .contains("EUR 200.00")
            .doesNotContain("Budget")
            .doesNotContain("vs last month");
    }

    @Test
    void generate_emptyStoresAndCategories_stillValid() throws IOException {
        byte[] pdf = service.generate(report("42.00", "100.00", "50.00", Map.of(), List.of()), OCTOBER, "USD");

        assertValidPdf(pdf);
        assertThat(extractText(pdf))
            .contains("Summary")
            .doesNotContain("Top stores")
            .doesNotContain("Spend by category");
    }

    @Test
    void generate_thirtyCategories_rendersOnlyTopEight() throws IOException {
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        for (int i = 1; i <= 30; i++) {
            // CAT_01 largest ... CAT_30 smallest; rendered as "Cat 01", "Cat 02", ...
            categories.put(String.format("CAT_%02d", i), BigDecimal.valueOf(310 - i * 10L));
        }

        byte[] pdf = service.generate(report("4650.00", null, null, categories, List.of()), OCTOBER, "USD");

        assertValidPdf(pdf);
        String text = extractText(pdf);
        for (int i = 1; i <= 8; i++) {
            assertThat(text).contains(String.format("Cat %02d", i));
        }
        assertThat(text).doesNotContain("Cat 09").doesNotContain("Cat 30");
    }

    // ---- buildForUser() ----

    @Test
    void buildForUser_freeUser_proRequired_andReportNeverBuilt() {
        user(false);

        DvicheckException ex = assertThrows(DvicheckException.class,
            () -> service.buildForUser(userId, "garbage"));

        // 402 even with an invalid month — the Pro check runs before month validation
        assertThat(ex.getErrorCode()).isEqualTo("PRO_REQUIRED");
        verifyNoInteractions(monthlyReportService);
    }

    @Test
    void buildForUser_pro_invalidMonths_badRequest() {
        user(true);
        String thirteenMonthsAgo = YearMonth.now().minusMonths(13).toString();

        for (String month : List.of("2099-01", "garbage", thirteenMonthsAgo)) {
            DvicheckException ex = assertThrows(DvicheckException.class,
                () -> service.buildForUser(userId, month), month);
            assertThat(ex.getErrorCode()).as(month).isEqualTo("BAD_REQUEST");
        }
        verify(monthlyReportService, never()).getMonthlyReport(any(), any());
    }

    @Test
    void buildForUser_pro_zeroSpend_notFound() {
        user(true);
        YearMonth month = YearMonth.now().minusMonths(1);
        when(monthlyReportService.getMonthlyReport(userId, month))
            .thenReturn(report("0.00", "100.00", "0.00", Map.of(), List.of()));

        DvicheckException ex = assertThrows(DvicheckException.class,
            () -> service.buildForUser(userId, month.toString()));

        assertThat(ex.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("No spending recorded for " + month);
    }

    @Test
    void buildForUser_pro_validMonth_returnsPdfForThatMonth() {
        user(true);
        YearMonth month = YearMonth.now().minusMonths(2);
        when(monthlyReportService.getMonthlyReport(userId, month)).thenReturn(fullReport());

        byte[] pdf = service.buildForUser(userId, month.toString());

        assertValidPdf(pdf);
        verify(monthlyReportService).getMonthlyReport(userId, month);
    }
}
