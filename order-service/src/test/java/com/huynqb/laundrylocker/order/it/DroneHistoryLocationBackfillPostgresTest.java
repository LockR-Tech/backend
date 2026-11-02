package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.order.service.DroneHistoryLocationBackfill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Đơn drone cũ (mốc chưa có toạ độ) được bù trên Postgres thật; đơn đã có toạ độ thì không bị đụng, và chạy
/// lại không đổi gì.
class DroneHistoryLocationBackfillPostgresTest extends DroneFlowPostgresTestBase {

    @Autowired
    private DroneHistoryLocationBackfill backfill;

    private List<Map<String, Object>> rows(long orderId) {
        return jdbc.queryForList("select latitude, longitude, location_source from "
                + "order_schema.order_status_history where order_id = ? order by id", orderId);
    }

    @Test
    void oldOrdersGetLocationsOnceAndOrdersAlreadyLocatedAreLeftAlone() {
        bothTechniciansOnDuty();
        long orderId = bookedAndPickedUpBy(TECH_NEAR);
        List<Map<String, Object>> recordedLive = rows(orderId);

        // Đơn mới đã có toạ độ ghi lúc ghi mốc: không bị đụng.
        assertEquals(0, backfill.backfill());
        assertEquals(recordedLive, rows(orderId));

        // Giả lập đơn cũ: chưa mốc nào có toạ độ.
        jdbc.update("update order_schema.order_status_history set latitude = null, longitude = null, "
                + "location_source = null where order_id = ?", orderId);

        int located = backfill.backfill();

        List<Map<String, Object>> backfilled = rows(orderId);
        assertTrue(located > 0);
        assertEquals(backfilled.size(), located);
        backfilled.forEach(row -> {
            assertNotNull(row.get("latitude"));
            assertNotNull(row.get("location_source"));
        });
        assertEquals(0, backfill.backfill());
        assertEquals(backfilled, rows(orderId));
    }
}
