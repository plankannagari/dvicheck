package com.dvicheck.backend.service.coach;

import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.model.ItemCategory;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.LineItemRepository;
import com.dvicheck.backend.repository.UserRepository;
import com.dvicheck.backend.service.MonthlyReportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds {@link CoachFacts} for one user and one week.
 *
 * Week rules match the existing weekly code (InsightsService / SpendingTrendsService):
 * weeks are dated by {@code bills.purchase_date}, and every range query is
 * {@code BETWEEN :from AND :to} — inclusive both ends — so [weekStart, weekStart+7)
 * is passed as weekStart .. weekStart+6. Callers pass a Monday weekStart.
 */
@Component
@RequiredArgsConstructor
public class CoachFactsBuilder {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final BillRepository billRepository;
    private final LineItemRepository lineItemRepository;
    private final UserRepository userRepository;
    private final MonthlyReportService monthlyReportService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<CoachFacts> build(UUID userId, LocalDate weekStart) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        LocalDate weekEnd = weekStart.plusDays(6);
        BigDecimal total = orZero(billRepository.sumTotalBetween(userId, weekStart, weekEnd));
        int receiptCount = (int) billRepository.countBillsBetween(userId, weekStart, weekEnd);
        if (receiptCount == 0 || total.signum() == 0) {
            return Optional.empty();
        }

        String currency = currencyOf(user.get());
        // Per call: DecimalFormat is not thread-safe
        DecimalFormat amountFormat = new DecimalFormat("#,##0.00");

        LocalDate prevStart = weekStart.minusDays(7);
        LocalDate prevEnd = weekStart.minusDays(1);
        BigDecimal prevTotal = orZero(billRepository.sumTotalBetween(userId, prevStart, prevEnd));
        boolean hasPrev = prevTotal.signum() > 0;

        Map<ItemCategory, BigDecimal> thisWeek = categoryTotals(userId, weekStart, weekEnd);
        Map<ItemCategory, BigDecimal> lastWeek = categoryTotals(userId, prevStart, prevEnd);

        BigDecimal avoidable = orZero(billRepository.sumAvoidableBetween(userId, weekStart, weekEnd));

        return Optional.of(new CoachFacts(
            weekLabel(weekStart),
            money(currency, amountFormat, total),
            hasPrev ? money(currency, amountFormat, prevTotal) : null,
            hasPrev ? signedPercent(total.subtract(prevTotal), prevTotal) : null,
            receiptCount,
            topCategory(thisWeek, currency, amountFormat),
            risingCategory(thisWeek, lastWeek),
            topStore(userId, weekStart, weekEnd, currency, amountFormat),
            avoidable.signum() > 0 ? money(currency, amountFormat, avoidable) : null,
            budget(userId, currency, amountFormat),
            currency
        ));
    }

    /** "Oct 27 - Nov 2" for weekStart..weekStart+6 — shared with the coach API so both match. */
    public static String weekLabel(LocalDate weekStart) {
        return weekStart.format(DAY_LABEL) + " - " + weekStart.plusDays(6).format(DAY_LABEL);
    }

    /** Record components serialise in declaration order, so the JSON shape is stable. */
    public String toJson(CoachFacts facts) {
        try {
            return objectMapper.writeValueAsString(facts);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise CoachFacts", e);
        }
    }

    private Map<ItemCategory, BigDecimal> categoryTotals(UUID userId, LocalDate from, LocalDate to) {
        Map<ItemCategory, BigDecimal> totals = new EnumMap<>(ItemCategory.class);
        for (Object[] row : lineItemRepository.sumByCategoryBetween(userId, from, to)) {
            if (row[0] instanceof ItemCategory category) {
                totals.put(category, orZero((BigDecimal) row[1]));
            }
        }
        return totals;
    }

    // Share is of this week's line-item total (not the bill total): bill totals and line-item
    // sums can disagree (Day 34 data-quality note), and this keeps the share within 0-100%
    private static CoachFacts.Category topCategory(Map<ItemCategory, BigDecimal> totals,
                                                   String currency, DecimalFormat fmt) {
        BigDecimal sum = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() <= 0) {
            return null;
        }
        Map.Entry<ItemCategory, BigDecimal> top = totals.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow();
        return new CoachFacts.Category(
            prettyCategory(top.getKey()),
            money(currency, fmt, top.getValue()),
            percent(top.getValue(), sum));
    }

    // "Largest increase" = largest absolute increase among categories that also had spend
    // last week (a percent change from zero is undefined). Ties go to enum order.
    private static CoachFacts.RisingCategory risingCategory(Map<ItemCategory, BigDecimal> thisWeek,
                                                            Map<ItemCategory, BigDecimal> lastWeek) {
        ItemCategory best = null;
        BigDecimal bestIncrease = BigDecimal.ZERO;
        for (Map.Entry<ItemCategory, BigDecimal> e : thisWeek.entrySet()) {
            BigDecimal prev = lastWeek.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal increase = e.getValue().subtract(prev);
            if (prev.signum() > 0 && increase.compareTo(bestIncrease) > 0) {
                best = e.getKey();
                bestIncrease = increase;
            }
        }
        if (best == null) {
            return null;
        }
        return new CoachFacts.RisingCategory(
            prettyCategory(best), signedPercent(bestIncrease, lastWeek.get(best)));
    }

    private CoachFacts.Store topStore(UUID userId, LocalDate from, LocalDate to,
                                      String currency, DecimalFormat fmt) {
        List<Object[]> rows = billRepository.findStoreTotalsBetween(userId, from, to, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return null;
        }
        String name = (String) rows.get(0)[0];
        return new CoachFacts.Store(
            name == null || name.isBlank() ? "Unknown store" : name.trim(),
            money(currency, fmt, (BigDecimal) rows.get(0)[1]));
    }

    // Current month via the existing one-arg method, as specified — even when coaching a
    // past week. budgetUsedPercent is a primitive double; only meaningful with a budget.
    private CoachFacts.Budget budget(UUID userId, String currency, DecimalFormat fmt) {
        MonthlyReportDto report = monthlyReportService.getMonthlyReport(userId);
        if (report.budgetAmount() == null || report.budgetAmount().signum() <= 0) {
            return null;
        }
        return new CoachFacts.Budget(
            money(currency, fmt, report.budgetAmount()),
            Math.round(report.budgetUsedPercent()) + "%");
    }

    private static String currencyOf(User user) {
        String code = user.getCurrency();
        return code == null || code.isBlank() ? DEFAULT_CURRENCY : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String money(String currency, DecimalFormat fmt, BigDecimal value) {
        return currency + " " + fmt.format(orZero(value));
    }

    // "34%" — part / whole, rounded half-up to a whole percent
    private static String percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 0, RoundingMode.HALF_UP) + "%";
    }

    // "+12%" / "-8%" / "+0%" — change / base, rounded half-up to a whole percent
    private static String signedPercent(BigDecimal change, BigDecimal base) {
        BigDecimal pct = change.multiply(HUNDRED).divide(base, 0, RoundingMode.HALF_UP);
        return (pct.signum() >= 0 ? "+" : "") + pct + "%";
    }

    // ESSENTIAL -> Essential
    private static String prettyCategory(ItemCategory category) {
        String lower = category.name().toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
