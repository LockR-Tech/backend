package com.huynqb.laundrylocker.order.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumbersTest {

    @Test
    void vietnameseFormatsNormalizeToNationalForm() {
        assertEquals("0901234567", PhoneNumbers.normalize("0901234567"));
        assertEquals("0901234567", PhoneNumbers.normalize("+84901234567"));
        assertEquals("0901234567", PhoneNumbers.normalize("84901234567"));
        assertEquals("0901234567", PhoneNumbers.normalize(" 090 123-4567 "));
        assertEquals("0901234567", PhoneNumbers.normalize("+84 (0) 901.234.567"));
    }

    @Test
    void blankOrDigitlessInputHasNoNumber() {
        assertNull(PhoneNumbers.normalize(null));
        assertNull(PhoneNumbers.normalize("  "));
        assertTrue(PhoneNumbers.variants(null).isEmpty());
    }

    @Test
    void variantsCoverEveryStoredWritingOfTheSameNumber() {
        Set<String> variants = PhoneNumbers.variants("+84 901 234 567");

        assertTrue(variants.containsAll(Set.of("0901234567", "+84901234567", "84901234567")));
    }

    @Test
    void sameNumberComparesAcrossFormats() {
        assertTrue(PhoneNumbers.sameNumber("+84901234567", "090 123 4567"));
        assertFalse(PhoneNumbers.sameNumber("0901234567", "0907654321"));
        assertFalse(PhoneNumbers.sameNumber(null, "0901234567"));
    }
}
