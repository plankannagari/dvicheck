package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.AddItemRequest;
import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.CreateListRequest;
import com.dvicheck.backend.dto.ShoppingItemDto;
import com.dvicheck.backend.dto.ShoppingListDto;
import com.dvicheck.backend.service.ShoppingListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }

    @GetMapping("/lists")
    public ResponseEntity<ApiResponse<List<ShoppingListDto>>> getLists() {
        return ResponseEntity.ok(ApiResponse.ok(shoppingListService.getLists(currentUserId())));
    }

    @PostMapping("/lists")
    public ResponseEntity<ApiResponse<ShoppingListDto>> createList(@Valid @RequestBody CreateListRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shoppingListService.createList(currentUserId(), request.name())));
    }

    @GetMapping("/lists/{listId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getListWithItems(@PathVariable UUID listId) {
        return ResponseEntity.ok(ApiResponse.ok(shoppingListService.getListWithItems(currentUserId(), listId)));
    }

    @PostMapping("/lists/{listId}/items")
    public ResponseEntity<ApiResponse<ShoppingItemDto>> addItem(
            @PathVariable UUID listId, @Valid @RequestBody AddItemRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            shoppingListService.addItem(currentUserId(), listId, request.name(), request.quantity())));
    }

    @PatchMapping("/lists/{listId}/items/{itemId}/toggle")
    public ResponseEntity<ApiResponse<ShoppingItemDto>> toggleItem(
            @PathVariable UUID listId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(shoppingListService.toggleItem(currentUserId(), listId, itemId)));
    }

    @DeleteMapping("/lists/{listId}/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable UUID listId, @PathVariable UUID itemId) {
        shoppingListService.deleteItem(currentUserId(), listId, itemId);
        return ResponseEntity.ok(ApiResponse.ok("Item deleted", null));
    }
}
