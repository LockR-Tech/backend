package com.huynqb.laundrylocker.order.settings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DroneFeeRulesTest {

    private static void assertFee(OrderRules rules, int grams, long expected) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(rules.droneDeliveryFee(grams)), grams + " g");
    }

    @Test
    void baseFeeCoversBaseWeightAndEachStartedStepAddsTheStepFee() {
        OrderRules rules = TestOrderRules.defaults();

        assertFee(rules, 100, 15000);
        assertFee(rules, 500, 15000);
        assertFee(rules, 501, 18000);
        assertFee(rules, 750, 18000);
        assertFee(rules, 1000, 21000);
        assertFee(rules, 1200, 24000);
        assertFee(rules, 3000, 45000);
    }

    @Test
    void zeroStepFeeKeepsTheFlatPrice() {
        OrderRules rules = TestOrderRules.of(Map.of("app.order.drone-weight-step-fee", 0));

        assertFee(rules, 3000, 15000);
    }
}
