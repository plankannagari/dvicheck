package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.AdminAuditEntryDto;
import com.dvicheck.backend.dto.AdminGrantProRequest;
import com.dvicheck.backend.dto.AdminLookupRequest;
import com.dvicheck.backend.dto.AdminOverviewDto;
import com.dvicheck.backend.dto.AdminReasonRequest;
import com.dvicheck.backend.dto.AdminUserDto;
import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.service.admin.AdminAuditService;
import com.dvicheck.backend.service.admin.AdminGuard;
import com.dvicheck.backend.service.admin.AdminMetricsService;
import com.dvicheck.backend.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin dashboard API (Day 38), used from the terminal with curl. Every handler starts with
 * adminGuard.require(...) — non-admins get the same 404 as a missing route.
 *
 * Deliberately absent: any endpoint that lists users, and any endpoint that sets is_admin
 * (SQL only).
 *
 * Phone lookup: an E.164 '+' must be URL-encoded in a query string (%2B), otherwise it
 * arrives as a space. Either
 *   curl -G $API/admin/users/lookup --data-urlencode 'phone=+15551234567' -H "Authorization: Bearer $T"
 * or send it in a body:
 *   curl -X POST $API/admin/users/lookup -H 'Content-Type: application/json' -d '{"phone":"+15551234567"}' ...
 * (An unencoded '+1...' still resolves, since the service re-adds +1 for 11-digit numbers
 * starting with 1, but don't rely on that for other country codes.)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminGuard adminGuard;
    private final AdminMetricsService adminMetricsService;
    private final AdminUserService adminUserService;
    private final AdminAuditService adminAuditService;

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverviewDto>> overview() {
        User admin = adminGuard.require(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(adminMetricsService.overview()));
    }

    @GetMapping("/users/lookup")
    public ResponseEntity<ApiResponse<AdminUserDto>> lookup(@RequestParam(required = false) String phone) {
        User admin = adminGuard.require(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.lookup(admin.getId(), phone)));
    }

    @PostMapping("/users/lookup")
    public ResponseEntity<ApiResponse<AdminUserDto>> lookupByBody(
            @RequestBody(required = false) AdminLookupRequest request) {
        User admin = adminGuard.require(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(
            adminUserService.lookup(admin.getId(), request == null ? null : request.phone())));
    }

    @PostMapping("/users/{id}/grant-pro")
    public ResponseEntity<ApiResponse<AdminUserDto>> grantPro(
            @PathVariable String id, @RequestBody(required = false) AdminGrantProRequest request) {
        User admin = adminGuard.require(currentUserId());
        if (request == null) throw DvicheckException.badRequest("Body { days, reason } is required");
        return ResponseEntity.ok(ApiResponse.ok(
            adminUserService.grantPro(admin.getId(), parseId(id), request.days(), request.reason())));
    }

    @PostMapping("/users/{id}/revoke-bonus-pro")
    public ResponseEntity<ApiResponse<AdminUserDto>> revokeBonusPro(
            @PathVariable String id, @RequestBody(required = false) AdminReasonRequest request) {
        User admin = adminGuard.require(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(
            adminUserService.revokeBonusPro(admin.getId(), parseId(id), request == null ? null : request.reason())));
    }

    @PostMapping("/users/{id}/coach/regenerate")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateCoach(@PathVariable String id) {
        User admin = adminGuard.require(currentUserId());
        String result = adminUserService.regenerateCoach(admin.getId(), parseId(id));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("result", result)));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<AdminAuditEntryDto>>> audit() {
        User admin = adminGuard.require(currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(adminAuditService.recent()));
    }

    // Taken as a String: a UUID path variable that fails to bind would hit the catch-all 500
    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw DvicheckException.badRequest("Invalid user id");
        }
    }
}
