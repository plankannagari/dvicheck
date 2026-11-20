package com.dvicheck.backend.service.admin;

import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gate for every /api/admin/** handler (Day 38). Non-admins — and unknown users — get the
 * same 404 a missing route would, never 403, so admin endpoints aren't advertised.
 */
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final UserRepository userRepository;

    public User require(UUID userId) {
        return userRepository.findById(userId)
            .filter(User::isAdmin)
            // Constructor, not notFound(): that factory appends " not found" to its argument
            .orElseThrow(() -> new DvicheckException("NOT_FOUND", "Not found"));
    }
}
