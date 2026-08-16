package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.WeeklyInsightDto;
import com.dvicheck.backend.model.ItemCategory;
import com.dvicheck.backend.model.LineItem;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.LineItemRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightsService {

    private static final int TOP_ITEMS_LIMIT = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal AVOIDABLE_RATIO_THRESHOLD = new BigDecimal("0.20");
    private static final BigDecimal SPENDING_UP_MULTIPLIER = new BigDecimal("1.2");
    private static final BigDecimal SPENDING_DOWN_MULTIPLIER = new BigDecimal("0.9");

    private final BillRepository billRepository;
    private final LineItemRepository lineItemRepository;
    private final UserRepository userRepository;
    private final SpendingNarrativeService spendingNarrativeService;

    @Transactional(readOnly = true)
    public WeeklyInsightDto getWeeklyInsights(UUID userId) {
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEnd = LocalDate.now();
        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        LocalDate prevWeekEnd = weekStart.minusDays(1);

        long thisWeekBills = billRepository.countBillsSince(userId, weekStart);
        BigDecimal thisWeekTotal = billRepository.sumTotalSince(userId, weekStart);
        BigDecimal thisWeekAvoidable = billRepository.sumAvoidableSince(userId, weekStart);
        BigDecimal prevWeekTotal = billRepository.sumTotalBetween(userId, prevWeekStart, prevWeekEnd);

        BigDecimal vsLastWeekPercent = calculatePercentChange(thisWeekTotal, prevWeekTotal);

        List<LineItem> thisWeekItems = lineItemRepository
            .findByBillUserIdAndBillPurchaseDateBetween(userId, weekStart, weekEnd);

        List<WeeklyInsightDto.TopItemDto> topItems = thisWeekItems.stream()
            .sorted(Comparator.comparing(this::safeTotalPrice, Comparator.reverseOrder()))
            .limit(TOP_ITEMS_LIMIT)
            .map(item -> new WeeklyInsightDto.TopItemDto(
                item.getName(), item.getTotalPrice(), item.getCategory().name()))
            .toList();

        Map<String, BigDecimal> spendByCategory = spendByCategory(thisWeekItems);

        String pattern = determinePattern(thisWeekTotal, thisWeekAvoidable, prevWeekTotal);

        // Get household size from user profile
        int householdSize = userRepository.findById(userId)
            .map(u -> u.getHouseholdSize() != null ? u.getHouseholdSize() : 1)
            .orElse(1);

        // Top category: the category with highest spend value
        String topCategory = spendByCategory.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        // Top item names (first 3 from topItems)
        List<String> topItemNames = topItems.stream()
            .limit(3)
            .map(WeeklyInsightDto.TopItemDto::name)
            .toList();

        // Build context and generate narrative
        SpendingNarrativeService.NarrativeContext ctx =
            new SpendingNarrativeService.NarrativeContext(
                thisWeekTotal, thisWeekAvoidable, prevWeekTotal,
                vsLastWeekPercent.doubleValue(), (int) thisWeekBills, householdSize,
                topCategory, topItemNames
            );
        String narrative = spendingNarrativeService.generateNarrative(ctx, userId, weekStart);

        return new WeeklyInsightDto(
            weekStart,
            weekEnd,
            thisWeekBills,
            thisWeekTotal,
            thisWeekAvoidable,
            prevWeekTotal,
            vsLastWeekPercent.doubleValue(),
            topItems,
            spendByCategory,
            pattern,
            narrative
        );
    }

    private BigDecimal safeTotalPrice(LineItem item) {
        return item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
    }

    private BigDecimal calculatePercentChange(BigDecimal thisWeekTotal, BigDecimal prevWeekTotal) {
        if (prevWeekTotal == null || prevWeekTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return thisWeekTotal.subtract(prevWeekTotal)
            .divide(prevWeekTotal, 4, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(1, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> spendByCategory(List<LineItem> items) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ItemCategory category : ItemCategory.values()) {
            byCategory.put(category.name(), BigDecimal.ZERO);
        }
        for (LineItem item : items) {
            String key = item.getCategory().name();
            byCategory.merge(key, safeTotalPrice(item), BigDecimal::add);
        }
        return byCategory;
    }

    private String determinePattern(BigDecimal thisWeekTotal, BigDecimal thisWeekAvoidable, BigDecimal prevWeekTotal) {
        if (thisWeekTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avoidableRatio = thisWeekAvoidable.divide(thisWeekTotal, 4, RoundingMode.HALF_UP);
            if (avoidableRatio.compareTo(AVOIDABLE_RATIO_THRESHOLD) > 0) {
                return "Higher than usual avoidable spend this week";
            }
        }
        if (prevWeekTotal.compareTo(BigDecimal.ZERO) > 0) {
            if (thisWeekTotal.compareTo(prevWeekTotal.multiply(SPENDING_UP_MULTIPLIER)) > 0) {
                return "Spending up vs last week";
            }
            if (thisWeekTotal.compareTo(prevWeekTotal.multiply(SPENDING_DOWN_MULTIPLIER)) < 0) {
                return "Great — spending down vs last week";
            }
        }
        return "Spending on track this week";
    }
}
