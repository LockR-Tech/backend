package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.order.dto.AcceptDroneOrderRequest;
import com.huynqb.laundrylocker.order.dto.ConfirmDroneLoadingRequest;
import com.huynqb.laundrylocker.order.dto.DroneFlightTrackPointResponse;
import com.huynqb.laundrylocker.order.service.DroneDeliverySimulator;
import com.huynqb.laundrylocker.order.service.DroneFlightTrackRecorder;
import com.huynqb.laundrylocker.order.service.DroneOrderMaintenanceService;
import com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Đường bay của đơn DEMO được lưu thật vào Postgres (Flyway V39) trong lúc bộ giả lập đẩy chặng, và đọc lại
/// được theo thứ tự thời gian — đúng dữ liệu admin xem ở "Nhật ký vị trí bay".
class DroneFlightTrackPostgresTest extends DroneFlowPostgresTestBase {

    @Autowired
    private DroneOrderMaintenanceService maintenanceService;
    @Autowired
    private DroneDeliverySimulator simulator;
    @Autowired
    private DroneFlightTrackRecorder recorder;

    @BeforeEach
    void clearTrack() {
        jdbc.execute("TRUNCATE order_schema.drone_flight_track_points RESTART IDENTITY");
    }

    @Test
    void simulatedFlightPositionsAreSavedAndReadBackInOrder() throws InterruptedException {
        settings.update(Map.of(
                OrderSettingsCatalog.DRONE_DEMO_ENABLED, true,
                OrderSettingsCatalog.DRONE_DEMO_STAGE_DELAY_MS, 60_000), null);
        bothTechniciansOnDuty();
        long orderId = bookedAndPickedUpBy(TECH_NEAR);
        maintenanceService.accept(orderId, TECH_NEAR, "acc-" + orderId, new AcceptDroneOrderRequest(DRONE_A1));
        jdbc.update("update order_schema.orders set fulfillment_mode = 'DEMO' where id = ?", orderId);
        maintenanceService.confirmLoading(orderId, TECH_NEAR, "load-1",
                new ConfirmDroneLoadingRequest(1200, null, true, true, true, null));
        maintenanceService.launch(orderId, TECH_NEAR, "launch-1");

        simulator.advanceScheduledMissions();
        // Tối đa một điểm mỗi 2 giây cho một đơn (DroneFlightTrackRecorder.MIN_INTERVAL_MS).
        Thread.sleep(2_100);
        simulator.advanceScheduledMissions();

        List<DroneFlightTrackPointResponse> track = recorder.track(orderId);
        assertTrue(track.size() >= 2, "expected two saved points, got " + track.size());
        DroneFlightTrackPointResponse first = track.get(0);
        assertEquals("LAUNCHING", first.stage());
        assertEquals(DroneFlightTrackRecorder.DRONE_SIMULATED, first.source());
        assertTrue(first.latitude() != 0 && first.longitude() != 0);
        assertTrue(!track.get(1).recordedAt().isBefore(first.recordedAt()));
        Integer rows = jdbc.queryForObject(
                "select count(*) from order_schema.drone_flight_track_points where order_id = ?", Integer.class, orderId);
        assertEquals(track.size(), rows);
    }
}
