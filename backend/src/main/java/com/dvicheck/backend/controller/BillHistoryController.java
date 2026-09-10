package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.AddManualItemsRequest;
import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.BillDetailResponse;
import com.dvicheck.backend.dto.RecentBillDto;
import com.dvicheck.backend.dto.UpdateBillRequest;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Bill;
import com.dvicheck.backend.model.BillType;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// @Transactional(readOnly = true): this controller calls BillRepository directly instead of
// going through a service, so it has no service-layer transaction to keep the Hibernate
// session open. Without this, bill.getLineItems() / bill.getUser() below are lazy accesses
// outside any transaction — they only "work" today because spring.jpa.open-in-view defaults
// to true; this makes the requirement explicit instead of relying on that implicit default.
@Transactional(readOnly = true)
@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillHistoryController {

    private final BillRepository billRepository;
    private final BillService billService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecentBillDto>>> getBills(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        List<Bill> bills = (search == null || search.isBlank())
            ? billRepository.findRecentByUserId(currentUserId(), PageRequest.of(page, size))
            : billRepository.findByUserIdAndStoreNameContainingIgnoreCase(
                currentUserId(), search, PageRequest.of(page, size))
                .getContent();

        List<UUID> billIds = bills.stream().map(Bill::getId).toList();
        Map<UUID, Long> itemCounts = billIds.isEmpty()
            ? Map.of()
            : billRepository.countLineItemsByBillIds(billIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        List<RecentBillDto> dtos = bills.stream()
            .map(b -> toRecentBillDto(b, itemCounts))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/{billId}")
    public ResponseEntity<ApiResponse<BillDetailResponse>> getBillDetail(@PathVariable UUID billId) {
        Bill bill = billRepository.findById(billId)
            .orElseThrow(() -> DvicheckException.notFound("Bill"));

        if (!bill.getUser().getId().equals(currentUserId())) {
            throw DvicheckException.unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.ok(billService.toDetailResponse(bill)));
    }

    // Overrides the class-level readOnly=true (see addManualItems() below for the other write).
    @Transactional
    @PatchMapping("/{billId}")
    public ResponseEntity<ApiResponse<BillDetailResponse>> updateBill(
            @PathVariable UUID billId, @Valid @RequestBody UpdateBillRequest request) {
        Bill bill = billRepository.findById(billId)
            .orElseThrow(() -> DvicheckException.notFound("Bill"));

        if (!bill.getUser().getId().equals(currentUserId())) {
            throw DvicheckException.unauthorized();
        }

        if (request.storeName() != null && !request.storeName().isBlank()) {
            bill.setStoreName(request.storeName().trim());
        }
        if (request.billType() != null && !request.billType().isBlank()) {
            try {
                bill.setBillType(BillType.valueOf(request.billType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw DvicheckException.badRequest("Invalid bill type");
            }
        }
        if (request.purchaseDate() != null) {
            bill.setPurchaseDate(request.purchaseDate());
        }

        Bill saved = billRepository.save(bill);
        return ResponseEntity.ok(ApiResponse.ok(billService.toDetailResponse(saved)));
    }

    // Overrides the class-level readOnly=true — this is now the second write in this
    // controller (alongside updateBill() above), and needs the same override for the
    // same reason: without it, BillService.addManualItems()'s own @Transactional would
    // just join this method's read-only transaction under default REQUIRED propagation
    // instead of opening a writable one.
    @Transactional
    @PatchMapping("/{billId}/items")
    public ResponseEntity<ApiResponse<BillDetailResponse>> addManualItems(
            @PathVariable UUID billId,
            @RequestBody AddManualItemsRequest request) {
        var updated = billService.addManualItems(billId, currentUserId(), request.items());
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    private RecentBillDto toRecentBillDto(Bill bill, Map<UUID, Long> itemCounts) {
        return new RecentBillDto(
            bill.getId(),
            bill.getStoreName(),
            bill.getBillType().name(),
            bill.getPurchaseDate(),
            bill.getTotalAmount(),
            bill.getAvoidableAmount(),
            bill.getCurrency(),
            itemCounts.getOrDefault(bill.getId(), 0L).intValue()
        );
    }
}
