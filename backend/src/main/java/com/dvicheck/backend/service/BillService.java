package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.AddManualItemsRequest;
import com.dvicheck.backend.dto.BillDetailResponse;
import com.dvicheck.backend.dto.HomeSummaryResponse;
import com.dvicheck.backend.dto.RecentBillDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Bill;
import com.dvicheck.backend.model.ItemCategory;
import com.dvicheck.backend.model.LineItem;
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

    // Moved here (was private on BillHistoryController) so addManualItems() below can reuse
    // the exact same GET /api/bills/{billId} response shape instead of duplicating it.
    public BillDetailResponse toDetailResponse(Bill bill) {
        List<BillDetailResponse.LineItemDetail> lineItems = bill.getLineItems().stream()
            .map(this::toLineItemDetail)
            .toList();

        return new BillDetailResponse(
            bill.getId(),
            bill.getStoreName(),
            bill.getBillType().name(),
            bill.getPurchaseDate(),
            bill.getTotalAmount(),
            bill.getAvoidableAmount(),
            bill.getCurrency(),
            bill.getAiSummary(),
            lineItems
        );
    }

    private BillDetailResponse.LineItemDetail toLineItemDetail(LineItem item) {
        return new BillDetailResponse.LineItemDetail(
            item.getId(),
            item.getName(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getTotalPrice(),
            item.getCategory().name(),
            item.getSuggestion(),
            item.getSavingEstimate(),
            item.getConfidence()
        );
    }

    @Transactional
    public BillDetailResponse addManualItems(
            UUID billId, UUID userId, List<AddManualItemsRequest.ManualItem> items) {
        Bill bill = billRepository.findById(billId)
            .orElseThrow(() -> DvicheckException.notFound("Bill"));
        if (!bill.getUser().getId().equals(userId)) {
            throw DvicheckException.unauthorized();
        }

        for (var item : items) {
            int qty = item.quantity() != null ? item.quantity() : 1;
            BigDecimal qtyDecimal = BigDecimal.valueOf(qty);
            BigDecimal totalPrice = item.totalPrice() != null
                ? item.totalPrice()
                : item.unitPrice().multiply(qtyDecimal);
            bill.getLineItems().add(LineItem.builder()
                .bill(bill)
                .name(item.name())
                .quantity(qtyDecimal)
                .unitPrice(item.unitPrice())
                .totalPrice(totalPrice)
                .category(ItemCategory.ESSENTIAL)
                .confidence(BigDecimal.ONE)
                .build());
            bill.setTotalAmount(bill.getTotalAmount().add(totalPrice));
        }

        Bill saved = billRepository.save(bill);
        return toDetailResponse(saved);
    }
}
