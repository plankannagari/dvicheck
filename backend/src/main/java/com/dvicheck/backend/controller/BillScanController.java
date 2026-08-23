package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.BillScanResponse;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.Bill;
import com.dvicheck.backend.model.BillType;
import com.dvicheck.backend.model.ItemCategory;
import com.dvicheck.backend.model.LineItem;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.BillRepository;
import com.dvicheck.backend.service.BudgetAlertService;
import com.dvicheck.backend.service.ClaudeItemAnalyser;
import com.dvicheck.backend.service.GeminiItemAnalyser;
import com.dvicheck.backend.service.GeminiReceiptParser;
import com.dvicheck.backend.service.OcrService;
import com.dvicheck.backend.service.PantryService;
import com.dvicheck.backend.service.ReceiptParser;
import com.dvicheck.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
@Slf4j
public class

BillScanController {

    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_RAW_TEXT_LENGTH = 5000;
    private static final String DEFAULT_STORE_NAME = "Unknown Store";

    private final OcrService ocrService;
    private final ReceiptParser receiptParser;
    private final GeminiReceiptParser geminiReceiptParser;
    private final ClaudeItemAnalyser claudeItemAnalyser;
    private final GeminiItemAnalyser geminiItemAnalyser;
    private final BillRepository billRepository;
    private final UserService userService;
    private final PantryService pantryService;
    private final BudgetAlertService budgetAlertService;

    @Value("${app.ai.use-gemini-analyser:false}")
    private boolean useGeminiAnalyser;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BillScanResponse>> scanBill(
            @RequestParam("image") MultipartFile image,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String purchaseDate) {

        validateImage(image);
        User user = userService.findById(currentUserId());

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw DvicheckException.badRequest("Could not read the uploaded image");
        }

        String rawText = ocrService.extractText(bytes);
        // Use heuristic parser for store name and total (fast, no API cost)
        ReceiptParser.ParsedReceipt parsed = receiptParser.parse(rawText);
        // Use Gemini API for line items (handles multi-line OCR splits)
        List<ReceiptParser.ParsedLineItem> lineItems = geminiReceiptParser.parse(rawText);

        // Get household size from user profile (default 1 if not set)
        int householdSize = user.getHouseholdSize() != null ? user.getHouseholdSize() : 1;

        // Run AI analysis pass
        List<ClaudeItemAnalyser.ItemAnalysis> analyses = useGeminiAnalyser
            ? geminiItemAnalyser.analyseItems(lineItems, householdSize)
            : claudeItemAnalyser.analyseItems(lineItems, householdSize);

        // Create a map for quick lookup by name
        Map<String, ClaudeItemAnalyser.ItemAnalysis> analysisMap = analyses.stream()
            .collect(Collectors.toMap(
                a -> a.name().toLowerCase().trim(),
                a -> a,
                (a, b) -> a  // keep first on duplicate key
            ));

        Bill bill = Bill.builder()
            .user(user)
            .storeName(resolveStoreName(storeName, parsed.storeName()))
            .billType(resolveBillType(billType))
            .purchaseDate(resolveDate(purchaseDate))
            .totalAmount(resolveTotal(parsed, lineItems))
            .rawOcrText(truncate(rawText, MAX_RAW_TEXT_LENGTH))
            .build();

        for (ReceiptParser.ParsedLineItem parsedItem : lineItems) {
            LineItem lineItem = LineItem.builder()
                .bill(bill)
                .name(parsedItem.name())
                .unitPrice(parsedItem.unitPrice())
                .totalPrice(parsedItem.totalPrice())
                .quantity(parsedItem.quantity())
                .category(ItemCategory.ESSENTIAL)
                .build();

            ClaudeItemAnalyser.ItemAnalysis analysis =
                analysisMap.get(parsedItem.name().toLowerCase().trim());
            if (analysis != null) {
                lineItem.setCategory(ItemCategory.valueOf(analysis.category()));
                lineItem.setFlagReason(analysis.reason());
                lineItem.setSuggestion(analysis.suggestion());
                lineItem.setSavingEstimate(analysis.savingEstimate());
                lineItem.setConfidence(BigDecimal.valueOf(analysis.confidence()));
            }

            bill.getLineItems().add(lineItem);
        }

        BigDecimal avoidable = bill.getLineItems().stream()
            .filter(li -> li.getCategory() == ItemCategory.AVOIDABLE
                       || li.getCategory() == ItemCategory.REDUCIBLE)
            .map(LineItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        bill.setAvoidableAmount(avoidable);

        Bill saved = billRepository.save(bill);
        pantryService.updateFromBill(currentUserId(), saved);
        // Fire-and-forget: save() above already committed its own transaction (this
        // controller has no @Transactional of its own), so the bill row is guaranteed
        // durable before this runs. checkAndSendAlerts() never throws outward.
        budgetAlertService.checkAndSendAlerts(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(saved)));
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw DvicheckException.badRequest("Image file is required");
        }
        if (image.getSize() >= MAX_IMAGE_SIZE_BYTES) {
            throw DvicheckException.badRequest("Image must be smaller than 10MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw DvicheckException.badRequest("File must be an image");
        }
    }

    private String resolveStoreName(String provided, String parsedStoreName) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        return parsedStoreName != null && !parsedStoreName.isBlank() ? parsedStoreName : DEFAULT_STORE_NAME;
    }

    private LocalDate resolveDate(String provided) {
        if (provided == null || provided.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(provided);
        } catch (DateTimeParseException e) {
            throw DvicheckException.badRequest("purchaseDate must be in yyyy-MM-dd format");
        }
    }

    private BillType resolveBillType(String provided) {
        if (provided == null || provided.isBlank()) {
            return BillType.GROCERY;
        }
        try {
            return BillType.valueOf(provided.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DvicheckException.badRequest("billType must be one of GROCERY, UTILITY, OTHER");
        }
    }

    private BigDecimal resolveTotal(ReceiptParser.ParsedReceipt parsed, List<ReceiptParser.ParsedLineItem> lineItems) {
        if (parsed.total() != null && parsed.total().compareTo(BigDecimal.ZERO) > 0) {
            return parsed.total();
        }
        return lineItems.stream()
            .map(ReceiptParser.ParsedLineItem::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private BillScanResponse toResponse(Bill bill) {
        List<BillScanResponse.LineItemResult> items = bill.getLineItems().stream()
            .map(li -> new BillScanResponse.LineItemResult(
                li.getId(),
                li.getName(),
                li.getUnitPrice(),
                li.getTotalPrice(),
                li.getCategory().name(),
                li.getSuggestion(),
                li.getSavingEstimate()))
            .toList();

        return new BillScanResponse(
            bill.getId(),
            bill.getStoreName(),
            bill.getPurchaseDate(),
            bill.getTotalAmount(),
            items.size(),
            items
        );
    }
}
