package com.dvicheck.backend.service.admin;

import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminGuard guard = new AdminGuard(userRepository);
    private final UUID id = UUID.randomUUID();

    private void givenUser(boolean admin) {
        when(userRepository.findById(id)).thenReturn(Optional.of(
            User.builder().id(id).phone("+15550000001").admin(admin).build()));
    }

    @Test
    void admin_passes_andIsReturned() {
        givenUser(true);
        assertSame(id, guard.require(id).getId());
    }

    @Test
    void nonAdmin_notFound() {
        givenUser(false);
        assertGuard404(() -> guard.require(id));
    }

    @Test
    void unknownUser_notFound() {
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        assertGuard404(() -> guard.require(id));
    }

    // Same code and message as any missing route would give — never 403
    private static void assertGuard404(org.junit.jupiter.api.function.Executable call) {
        DvicheckException e = assertThrows(DvicheckException.class, call);
        assertEquals("NOT_FOUND", e.getErrorCode());
        assertEquals("Not found", e.getMessage());
    }
}
