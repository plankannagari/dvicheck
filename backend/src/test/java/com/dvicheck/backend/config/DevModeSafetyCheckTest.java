package com.dvicheck.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevModeSafetyCheckTest {

    @Test
    void devModeOn_prodProfile_refusesToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> DevModeSafetyCheck.check(true, new String[] {"prod"}));
        assertEquals("app.auth.dev-mode must be false in production", e.getMessage());
    }

    @Test
    void devModeOn_productionProfileAmongOthers_anyCase_refusesToStart() {
        assertThrows(IllegalStateException.class,
            () -> DevModeSafetyCheck.check(true, new String[] {"local", "PRODUCTION"}));
    }

    @Test
    void devModeOff_prodProfile_ok() {
        assertDoesNotThrow(() -> DevModeSafetyCheck.check(false, new String[] {"prod"}));
    }

    @Test
    void devModeOn_noProdProfile_ok() {
        assertDoesNotThrow(() -> DevModeSafetyCheck.check(true, new String[] {}));
        assertDoesNotThrow(() -> DevModeSafetyCheck.check(true, new String[] {"local"}));
    }
}
