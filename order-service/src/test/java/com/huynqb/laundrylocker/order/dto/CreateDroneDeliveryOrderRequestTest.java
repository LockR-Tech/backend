package com.huynqb.laundrylocker.order.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateDroneDeliveryOrderRequestTest {

    @Test
    void phoneMustBeAVietnameseNumber() {
        for (String ok : new String[] {"0901234567", "0281234567", "+84901234567", "84901234567"}) {
            assertTrue(ok.matches(CreateDroneDeliveryOrderRequest.PHONE), ok);
        }
        // "123456789" lọt qua regex cũ (9–15 chữ số) trong test 2026-10-29.
        for (String bad : new String[] {"123456789", "12345", "0012345678", "090123456", "09012345678", "abc"}) {
            assertFalse(bad.matches(CreateDroneDeliveryOrderRequest.PHONE), bad);
        }
    }
}
