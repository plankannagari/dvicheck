package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPCellEvent;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Pro monthly PDF report (OpenPDF 3.x — classes live under org.openpdf, not com.lowagie).
 * Contains spend figures only: no phone number, user id or any other identifier.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyReportPdfService {

    private static final int MAX_MONTHS_BACK = 12;
    private static final int MAX_CATEGORY_ROWS = 8;
    private static final String DEFAULT_CURRENCY = "USD";

    private static final Color ACCENT = new Color(0xE8, 0x62, 0x2A);   // #E8622A
    private static final Color INK = new Color(0x1A, 0x16, 0x12);      // COLORS.ink
    private static final Color INK_LIGHT = new Color(0x8A, 0x84, 0x7E);
    private static final Color BORDER = new Color(0xE4, 0xDF, 0xD8);
    private static final Color BAR_TRACK = new Color(0xF5, 0xF2, 0xEE); // COLORS.bg

    private static final DateTimeFormatter FOOTER_DATE =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private final MonthlyReportService monthlyReportService;
    private final UserRepository userRepository;

    public byte[] buildForUser(UUID userId, String monthParam) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> DvicheckException.notFound("User"));

        // Before validating monthParam, so a free user always gets 402 whatever they send
        if (!user.isPro()) {
            throw DvicheckException.proRequired("Monthly PDF reports are a Pro feature");
        }

        YearMonth month = parseMonth(monthParam);
        MonthlyReportDto report = monthlyReportService.getMonthlyReport(userId, month);

        if (report.totalSpent() == null || report.totalSpent().compareTo(BigDecimal.ZERO) == 0) {
            // Constructor, not notFound(): that factory appends " not found" to its argument
            throw new DvicheckException("NOT_FOUND", "No spending recorded for " + month);
        }

        String currency = user.getCurrency() == null || user.getCurrency().isBlank()
            ? DEFAULT_CURRENCY : user.getCurrency();
        return generate(report, month, currency);
    }

    private YearMonth parseMonth(String monthParam) {
        YearMonth current = YearMonth.now();
        if (monthParam == null || monthParam.isBlank()) {
            return current;
        }
        YearMonth month;
        try {
            month = YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException e) {
            throw DvicheckException.badRequest("month must be in yyyy-MM format");
        }
        if (month.isAfter(current)) {
            throw DvicheckException.badRequest("month cannot be in the future");
        }
        if (month.isBefore(current.minusMonths(MAX_MONTHS_BACK))) {
            throw DvicheckException.badRequest("Reports are available for the last " + MAX_MONTHS_BACK + " months");
        }
        return month;
    }

    // Pure: no repositories, no clock other than the footer date — package-private for tests
    byte[] generate(MonthlyReportDto report, YearMonth month, String currencyCode) {
        // Per call: DecimalFormat is not thread-safe
        DecimalFormat amount = new DecimalFormat("#,##0.00");

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, ACCENT);
        Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 13, INK_LIGHT);
        Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
        Font label = FontFactory.getFont(FontFactory.HELVETICA, 11, INK);
        Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK);
        Font footer = FontFactory.getFont(FontFactory.HELVETICA, 9, INK_LIGHT);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("dvicheck", title));
            String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            Paragraph sub = new Paragraph("Monthly report - " + monthName + " " + month.getYear(), subtitle);
            sub.setSpacingAfter(20);
            document.add(sub);

            // Summary
            document.add(sectionHeading("Summary", heading));
            PdfPTable summary = twoColumnTable();
            addRow(summary, "Total spent", money(currencyCode, amount, report.totalSpent()), label, value);
            if (report.budgetAmount() != null) {
                String budget = money(currencyCode, amount, report.budgetAmount())
                    + String.format(Locale.ENGLISH, " (%.0f%% used)", report.budgetUsedPercent());
                addRow(summary, "Budget", budget, label, value);
            }
            // vsLastMonthPercent is a primitive double that is 0.0 when last month had no
            // spend — indistinguishable from "no change", so only show it when there's a
            // real previous month to compare against
            if (report.prevMonthTotal() != null && report.prevMonthTotal().compareTo(BigDecimal.ZERO) > 0) {
                addRow(summary, "vs last month",
                    String.format(Locale.ENGLISH, "%+.1f%%", report.vsLastMonthPercent()), label, value);
            }
            addRow(summary, "Avoidable spend", money(currencyCode, amount, report.avoidableSpend()), label, value);
            document.add(summary);

            // Top stores
            List<MonthlyReportDto.StoreTotal> stores = report.topStores();
            if (stores != null && !stores.isEmpty()) {
                document.add(sectionHeading("Top stores", heading));
                PdfPTable storeTable = twoColumnTable();
                for (MonthlyReportDto.StoreTotal store : stores.subList(0, Math.min(5, stores.size()))) {
                    String name = store.storeName() == null || store.storeName().isBlank()
                        ? "Unknown store" : store.storeName();
                    addRow(storeTable, name, money(currencyCode, amount, store.total()), label, value);
                }
                document.add(storeTable);
            }

            // Spend by category — keys are ItemCategory names (ESSENTIAL/REDUCIBLE/...),
            // pre-seeded with zeros, so drop the empty ones
            List<Map.Entry<String, BigDecimal>> categories = report.spendByCategory() == null ? List.of()
                : report.spendByCategory().entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().compareTo(BigDecimal.ZERO) > 0)
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(MAX_CATEGORY_ROWS)
                    .toList();
            if (!categories.isEmpty()) {
                document.add(sectionHeading("Spend by category", heading));
                BigDecimal largest = categories.get(0).getValue();
                PdfPTable categoryTable = new PdfPTable(new float[] {3, 2, 4});
                categoryTable.setWidthPercentage(100);
                for (Map.Entry<String, BigDecimal> entry : categories) {
                    categoryTable.addCell(textCell(prettyCategory(entry.getKey()), label, Element.ALIGN_LEFT));
                    categoryTable.addCell(textCell(money(currencyCode, amount, entry.getValue()), value, Element.ALIGN_RIGHT));
                    PdfPCell bar = new PdfPCell(new Phrase(" ", label));
                    bar.setBorder(Rectangle.BOTTOM);
                    bar.setBorderColor(BORDER);
                    bar.setPadding(6);
                    bar.setCellEvent(new BarEvent(entry.getValue().doubleValue() / largest.doubleValue()));
                    categoryTable.addCell(bar);
                }
                document.add(categoryTable);
            }

            Paragraph generated = new Paragraph(
                "Generated by dvicheck on " + LocalDate.now().format(FOOTER_DATE), footer);
            generated.setSpacingBefore(28);
            document.add(generated);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private static String money(String currencyCode, DecimalFormat format, BigDecimal value) {
        return currencyCode + " " + format.format(value == null ? BigDecimal.ZERO : value);
    }

    // ESSENTIAL -> Essential
    private static String prettyCategory(String key) {
        if (key == null || key.isBlank()) return "Other";
        String lower = key.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Paragraph sectionHeading(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(16);
        p.setSpacingAfter(6);
        return p;
    }

    private static PdfPTable twoColumnTable() {
        PdfPTable table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(100);
        return table;
    }

    private static void addRow(PdfPTable table, String left, String right, Font leftFont, Font rightFont) {
        table.addCell(textCell(left, leftFont, Element.ALIGN_LEFT));
        table.addCell(textCell(right, rightFont, Element.ALIGN_RIGHT));
    }

    private static PdfPCell textCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(8);
        return cell;
    }

    /** Draws a horizontal bar (track + filled portion) inside the cell, scaled to fraction 0..1. */
    private record BarEvent(double fraction) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte canvas = canvases[PdfPTable.BACKGROUNDCANVAS];
            float height = 8;
            float x = position.getLeft() + 6;
            float width = position.getWidth() - 12;
            float y = position.getBottom() + (position.getHeight() - height) / 2;

            canvas.saveState();
            canvas.setColorFill(BAR_TRACK);
            canvas.rectangle(x, y, width, height);
            canvas.fill();
            canvas.setColorFill(ACCENT);
            canvas.rectangle(x, y, (float) (width * Math.max(0, Math.min(1, fraction))), height);
            canvas.fill();
            canvas.restoreState();
        }
    }
}
