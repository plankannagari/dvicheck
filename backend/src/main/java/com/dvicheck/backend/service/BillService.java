package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.HomeSummaryResponse;
import com.dvicheck.backend.dto.RecentBillDto;
import com.dvicheck.backend.model.Bill;
import com.dvicheck.backend.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillService {

    private final BillRepository billRepository;

    @Transactional(readOnly = true)
    public HomeSummaryResponse getHomeSummary(UUID userId) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);

        long billsScanned = billRepository.countBillsSince(userId, thirtyDaysAgo);
        BigDecimal totalSpent = billRepository.sumTotalSince(userId, thirtyDaysAgo);
        BigDecimal avoidableSpend = billRepository.sumAvoidableSince(userId, thirtyDaysAgo);

        return new HomeSummaryResponse(
            billsScanned,
            totalSpent,
            avoidableSpend,
            avoidableSpend,   // estimatedSaved = avoidable for now
            0L,               // duplicatesCaught — wired in Day 17
            null              // topSuggestion — wired in Day 17
        );
    }

    @Transactional(readOnly = true)
    public List<RecentBillDto> getRecentBills(UUID userId, int limit) {
        List<Bill> bills = billRepository.findRecentByUserId(
            userId, PageRequest.of(0, limit));

        return bills.stream()
            .map(b -> new RecentBillDto(
                b.getId(),
                b.getStoreName(),
                b.getBillType().name(),
                b.getPurchaseDate(),
                b.getTotalAmount(),
                b.getAvoidableAmount(),
                b.getCurrency(),
                b.getLineItems().size()
            ))
            .toList();
    }
}
