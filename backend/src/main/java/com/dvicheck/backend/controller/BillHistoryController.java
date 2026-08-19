package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.BillDetailResponse;
import com.dvicheck.backend.dto.RecentBillDto;
import com.dvicheck.backend.dto.UpdateBillRequest;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Bill;
import com.dvicheck.backend.model.BillType;
import com.dvicheck.backend.model.LineItem;
import com.dvicheck.backend.repository.BillRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
        List<RecentBillDto> dtos = (search == null || search.isBlank())
            ? billRepository.findRecentByUserId(currentUserId(), PageRequest.of(page, size))
                .stream()
                .map(this::toRecentBillDto)
                .toList()
            : billRepository.findByUserIdAndStoreNameContainingIgnoreCase(
                currentUserId(), search, PageRequest.of(page, size))
                .map(this::toRecentBillDto)
                .getContent();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/{billId}")
    public ResponseEntity<ApiResponse<BillDetailResponse>> getBillDetail(@PathVariable UUID billId) {
        Bill bill = billRepository.findById(billId)
            .orElseThrow(() -> DvicheckException.notFound("Bill"));

        if (!bill.getUser().getId().equals(currentUserId())) {
            throw DvicheckException.unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.ok(toDetailResponse(bill)));
    }

    // Overrides the class-level readOnly=true — this is the one write in this controller.
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
        return ResponseEntity.ok(ApiResponse.ok(toDetailResponse(saved)));
    }

    private RecentBillDto toRecentBillDto(Bill bill) {
        return new RecentBillDto(
            bill.getId(),
            bill.getStoreName(),
            bill.getBillType().name(),
            bill.getPurchaseDate(),
            bill.getTotalAmount(),
            bill.getAvoidableAmount(),
            bill.getCurrency(),
            bill.getLineItems().size()
        );
    }

    private BillDetailResponse toDetailResponse(Bill bill) {
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
}
