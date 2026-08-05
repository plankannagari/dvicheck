package com.dvicheck.backend.controller;

import com.dvicheck.backend.dto.ApiResponse;
import com.dvicheck.backend.dto.UserResponse;
import com.dvicheck.backend.dto.UserSyncRequest;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<UserResponse>> sync(@Valid @RequestBody UserSyncRequest request) {
        User user = userService.findOrCreateByPhone(request.phone());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID id) {
        if (!id.equals(currentUserId())) {
            throw DvicheckException.unauthorized();
        }
        User user = userService.findById(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    private UUID currentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal().toString();
        return UUID.fromString(principal);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getPhone(), user.getCreatedAt());
    }
}
