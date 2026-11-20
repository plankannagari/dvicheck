package com.dvicheck.backend.service.referral;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferralCodeGeneratorTest {

    private final ReferralCodeGenerator generator = new ReferralCodeGenerator();

    @Test
    void generate_isSevenCharsFromAlphabet_over10000Runs() {
        for (int i = 0; i < 10_000; i++) {
            String code = generator.generate();
            assertEquals(7, code.length(), "length of " + code);
            for (char c : code.toCharArray()) {
                assertTrue(ReferralCodeGenerator.ALPHABET.indexOf(c) >= 0,
                    "unexpected character '" + c + "' in " + code);
            }
        }
    }

    @Test
    void alphabet_hasNoAmbiguousCharacters() {
        for (char c : "ILO01".toCharArray()) {
            assertTrue(ReferralCodeGenerator.ALPHABET.indexOf(c) < 0, "alphabet contains " + c);
        }
    }

    @Test
    void normalize_trimsUppercasesAndStripsSpacesAndHyphens() {
        assertEquals("ABCD2", ReferralCodeGenerator.normalize(" ab-c d2 "));
    }

    @Test
    void normalize_nullIsEmpty() {
        assertEquals("", ReferralCodeGenerator.normalize(null));
    }
}
