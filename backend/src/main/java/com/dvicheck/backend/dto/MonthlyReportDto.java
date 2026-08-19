package com.dvicheck.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record MonthlyReportDto(
        LocalDate monthStart,
        LocalDate monthEnd,
        BigDecimal totalSpent,
        BigDecimal avoidableSpend,
        BigDecimal budgetAmount,
        double budgetUsedPercent,
        int billsScanned,
        BigDecimal prevMonthTotal,
        double vsLastMonthPercent,
        Map<String, BigDecimal> spendByCategory,
        List<StoreTotal> topStores
) {
    public record StoreTotal(String storeName, BigDecimal total, int billCount) {}
}
