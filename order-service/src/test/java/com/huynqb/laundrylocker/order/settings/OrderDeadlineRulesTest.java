package com.huynqb.laundrylocker.order.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDeadlineRulesTest {

    @Test
    void defaultAutoCancelUnpaidMinutesIs15() {
        OrderRules rules = TestOrderRules.defaults();
        assertEquals(15, rules.autoCancelUnpaidMinutes());
        assertEquals(24, rules.autoCancelHours());
    }

    @Test
    void overriddenAutoCancelUnpaidMinutesReadsAdminSetting() {
        OrderRules rules = TestOrderRules.of(Map.of("app.order.auto-cancel-unpaid-minutes", 30));
        assertEquals(30, rules.autoCancelUnpaidMinutes());
    }
}
