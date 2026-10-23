package com.huynqb.laundrylocker.order.settings;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RentalPriceRulesTest {

    private static void assertPrice(OrderRules rules, String cellType, long hours, long expected) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(rules.rentalPrice(cellType, hours)),
                cellType + " " + hours + " h");
    }

    @Test
    void leftoverHoursAreHourlyButNeverMoreThanOneDay() {
        OrderRules rules = TestOrderRules.of(Map.of(
                "app.order.rental-rate-standard", 5000,
                "app.order.rental-daily-rate-standard", 40000));

        assertPrice(rules, "STANDARD", 1, 5000);
        assertPrice(rules, "STANDARD", 7, 35000);
        assertPrice(rules, "STANDARD", 8, 40000);
        assertPrice(rules, "STANDARD", 12, 40000);
        assertPrice(rules, "STANDARD", 24, 40000);
        assertPrice(rules, "STANDARD", 26, 50000);
        assertPrice(rules, "STANDARD", 72, 120000);
    }

    @Test
    void xlUsesItsOwnRates() {
        OrderRules rules = TestOrderRules.of(Map.of(
                "app.order.rental-rate-xl", 10000,
                "app.order.rental-daily-rate-xl", 80000));

        assertPrice(rules, "XL", 3, 30000);
        assertPrice(rules, "XL", 48, 160000);
    }

    @Test
    void zeroDailyRateKeepsPureHourlyPricing() {
        OrderRules rules = TestOrderRules.of(Map.of(
                "app.order.rental-rate-standard", 1000,
                "app.order.rental-daily-rate-standard", 0));

        assertPrice(rules, "STANDARD", 30, 30000);
    }

    @Test
    void dailyRateAboveTwentyFourHourlyHoursFallsBackToTwentyFourHours() {
        OrderRules rules = TestOrderRules.of(Map.of(
                "app.order.rental-rate-standard", 1000,
                "app.order.rental-daily-rate-standard", 40000));

        assertPrice(rules, "STANDARD", 23, 23000);
        assertPrice(rules, "STANDARD", 24, 24000);
        assertPrice(rules, "STANDARD", 50, 50000);
    }
}
