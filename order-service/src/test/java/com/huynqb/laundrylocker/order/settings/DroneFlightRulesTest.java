package com.huynqb.laundrylocker.order.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneFlightRulesTest {

    @Test
    void flightsAreUnrestrictedByDefault() {
        OrderRules rules = TestOrderRules.defaults();

        assertTrue(rules.droneFlightAllowedAt(0));
        assertTrue(rules.droneFlightAllowedAt(23));
        assertFalse(rules.droneFlightsSuspended());
    }

    @Test
    void configuredWindowIsStartInclusiveEndExclusive() {
        OrderRules rules = TestOrderRules.of(Map.of(
                OrderSettingsCatalog.DRONE_FLIGHT_START_HOUR, 6,
                OrderSettingsCatalog.DRONE_FLIGHT_END_HOUR, 18));

        assertFalse(rules.droneFlightAllowedAt(5));
        assertTrue(rules.droneFlightAllowedAt(6));
        assertTrue(rules.droneFlightAllowedAt(17));
        assertFalse(rules.droneFlightAllowedAt(18));
    }

    @Test
    void anInvertedWindowIsTreatedAsUnrestrictedInsteadOfGroundingTheFleet() {
        OrderRules rules = TestOrderRules.of(Map.of(
                OrderSettingsCatalog.DRONE_FLIGHT_START_HOUR, 20,
                OrderSettingsCatalog.DRONE_FLIGHT_END_HOUR, 6));

        assertTrue(rules.droneFlightAllowedAt(12));
    }
}
