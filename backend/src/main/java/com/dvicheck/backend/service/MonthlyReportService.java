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

    @Transactional(readOnly = true)
    public MonthlyReportDto getMonthlyReport(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate prevMonthStart = monthStart.minusMonths(1);
        LocalDate prevMonthEnd = monthStart.minusDays(1);

        BigDecimal totalSpent = billRepository.sumTotalBetween(userId, monthStart, today);
        BigDecimal prevMonthTotal = billRepository.sumTotalBetween(userId, prevMonthStart, prevMonthEnd);
        BigDecimal avoidableSpend = billRepository.sumAvoidableBetween(userId, monthStart, today);
        long billsScanned = billRepository.countBillsBetween(userId, monthStart, today);

        double vsLastMonthPercent = calculatePercentChange(totalSpent, prevMonthTotal).doubleValue();

        List<LineItem> thisMonthItems = lineItemRepository
            .findByBillUserIdAndBillPurchaseDateBetween(userId, monthStart, today);
        Map<String, BigDecimal> spendByCategory = spendByCategory(thisMonthItems);

        List<Object[]> storeRows = billRepository.findStoreTotalsBetween(
            userId, monthStart, today, PageRequest.of(0, TOP_STORES_LIMIT));
        List<MonthlyReportDto.StoreTotal> topStores = storeRows.stream()
            .map(row -> new MonthlyReportDto.StoreTotal(
                (String) row[0],
                (BigDecimal) row[1],
                ((Number) row[2]).intValue()))
            .toList();

        BigDecimal budgetAmount = userRepository.findById(userId)
            .map(User::getBudgetAmount)
            .orElse(null);

        double budgetUsedPercent = budgetAmount != null && budgetAmount.compareTo(BigDecimal.ZERO) > 0
            ? totalSpent.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(HUNDRED).doubleValue()
            : 0.0;

        return new MonthlyReportDto(
            monthStart,
            today,
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
