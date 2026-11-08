package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.MonthlyReportDto;
import com.dvicheck.backend.model.ItemCategory;
import com.dvicheck.backend.model.LineItem;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.repository.LineItemRepository;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyReportService {

    private static final int TOP_STORES_LIMIT = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BillRepository billRepository;
    private final LineItemRepository lineItemRepository;
    private final UserRepository userRepository;

    // Not cached, deliberately: BudgetAlertService calls this right after every bill save
    // and needs current numbers to detect threshold crossings.
    @Transactional(readOnly = true)
    public MonthlyReportDto getMonthlyReport(UUID userId) {
        return getMonthlyReport(userId, YearMonth.now());
    }

    @Transactional(readOnly = true)
    public MonthlyReportDto getMonthlyReport(UUID userId, YearMonth month) {
        // Every query below is `purchaseDate BETWEEN :from AND :to` — INCLUSIVE on both ends —
        // so the end is the month's last day, never the 1st of the next month (that would
        // pull in next month's day-1 bills). The current month is still capped at today,
        // exactly as before, so GET /api/reports/monthly (monthEnd = today) is unchanged.
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.equals(YearMonth.now()) ? LocalDate.now() : month.atEndOfMonth();
        YearMonth prevMonth = month.minusMonths(1);
        LocalDate prevMonthStart = prevMonth.atDay(1);
        LocalDate prevMonthEnd = prevMonth.atEndOfMonth();

        BigDecimal totalSpent = billRepository.sumTotalBetween(userId, monthStart, monthEnd);
        BigDecimal prevMonthTotal = billRepository.sumTotalBetween(userId, prevMonthStart, prevMonthEnd);
        BigDecimal avoidableSpend = billRepository.sumAvoidableBetween(userId, monthStart, monthEnd);
        long billsScanned = billRepository.countBillsBetween(userId, monthStart, monthEnd);

        double vsLastMonthPercent = calculatePercentChange(totalSpent, prevMonthTotal).doubleValue();

        List<LineItem> thisMonthItems = lineItemRepository
            .findByBillUserIdAndBillPurchaseDateBetween(userId, monthStart, monthEnd);
        Map<String, BigDecimal> spendByCategory = spendByCategory(thisMonthItems);

        List<Object[]> storeRows = billRepository.findStoreTotalsBetween(
            userId, monthStart, monthEnd, PageRequest.of(0, TOP_STORES_LIMIT));
        List<MonthlyReportDto.StoreTotal> topStores = storeRows.stream()
            .map(row -> new MonthlyReportDto.StoreTotal(
                (String) row[0],
                (BigDecimal) row[1],
                ((Number) row[2]).intValue()))
            .toList();

        // Always the user's CURRENT budget_amount — there is no budget history, so for a
        // past month budgetUsedPercent compares that month's spend to today's budget, not
        // whatever the budget was at the time.
        BigDecimal budgetAmount = userRepository.findById(userId)
            .map(User::getBudgetAmount)
            .orElse(null);

        double budgetUsedPercent = budgetAmount != null && budgetAmount.compareTo(BigDecimal.ZERO) > 0
            ? totalSpent.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(HUNDRED).doubleValue()
            : 0.0;

        return new MonthlyReportDto(
            monthStart,
            monthEnd,
            totalSpent,
            avoidableSpend,
            budgetAmount,
            budgetUsedPercent,
            (int) billsScanned,
            prevMonthTotal,
            vsLastMonthPercent,
            spendByCategory,
            topStores
        );
    }

    private BigDecimal calculatePercentChange(BigDecimal thisTotal, BigDecimal prevTotal) {
        if (prevTotal == null || prevTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return thisTotal.subtract(prevTotal)
            .divide(prevTotal, 4, RoundingMode.HALF_UP)
            .multiply(HUNDRED)
            .setScale(1, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> spendByCategory(List<LineItem> items) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ItemCategory category : ItemCategory.values()) {
            byCategory.put(category.name(), BigDecimal.ZERO);
        }
        for (LineItem item : items) {
            BigDecimal price = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
            byCategory.merge(item.getCategory().name(), price, BigDecimal::add);
        }
        return byCategory;
    }
}
