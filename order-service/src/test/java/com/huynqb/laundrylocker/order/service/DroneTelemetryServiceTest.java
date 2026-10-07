package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.DronePositionClient;
import com.huynqb.laundrylocker.order.client.LockerDroneClient;
import com.huynqb.laundrylocker.order.dto.DronePositionUpdate;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryFrame;
import com.huynqb.laundrylocker.order.dto.DroneTelemetryReport;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DroneTelemetryServiceTest {

    private static final String DRONE = "DRONE-S550-01";
    /// Tủ gửi và tủ nhận cách nhau khoảng 1,1 km theo hướng bắc.
    private static final double[] SOURCE = {10.8400, 106.8100};
    private static final double[] DESTINATION = {10.8500, 106.8100};

    @Mock
    private DroneMissionRepository missionRepository;
    @Mock
    private LockerOrderRepository orderRepository;
    @Mock
    private DroneMissionProgressService progressService;
    @Mock
    private AdminReferenceResolver references;
    @Mock
    private DronePositionClient positionClient;
    @Mock
    private LockerDroneClient lockerDroneClient;

    private DroneTelemetryService service;
    private LockerOrder order;
    private DroneMission mission;
    private long clock = Instant.now().toEpochMilli();

    @BeforeEach
    void setUp() {
        service = new DroneTelemetryService(
                missionRepository, orderRepository, progressService, references, positionClient, lockerDroneClient,
                new DroneTelemetryRegistry(15), new TransactionTemplate(mock(PlatformTransactionManager.class)),
                30, 150, 30);
        order = new LockerOrder();
        order.setId(21L);
        order.setType("DRONE_DELIVERY");
        order.setFulfillmentMode("STANDARD");
        order.setSourceLockerId(1L);
        order.setDestinationLockerId(5L);
        mission = new DroneMission();
        mission.setOrderId(21L);
        mission.setDroneCode(DRONE);
        mission.setSourceLockerId(1L);

        lenient().when(missionRepository.findFirstByDroneCodeAndStatusInOrderByIdDesc(eq(DRONE), anyList()))
                .thenAnswer(call -> DroneMissionProgressService.IN_FLIGHT_STAGES.contains(mission.getStatus())
                        ? Optional.of(mission) : Optional.empty());
        lenient().when(missionRepository.findByOrderId(21L)).thenReturn(Optional.of(mission));
        lenient().when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        lenient().when(orderRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(order));
        lenient().when(references.lockers(any())).thenAnswer(call -> Lookup.of(Map.of(
                1L, locker(1L, SOURCE), 5L, locker(5L, DESTINATION))));
        // Bản thật đổi trạng thái mission; ở đây chỉ cần chặng nhích lên để vòng lặp dừng đúng chỗ.
        lenient().doAnswer(call -> {
            DroneMission target = call.getArgument(1);
            target.setStatus(DroneMissionProgressService.nextStage(target.getStatus()));
            return null;
        }).when(progressService).advance(any(), any(), any(), any());
    }

    @Test
    void takeoffMovesAStandardMissionFromLaunchingToDeparted() {
        mission.setStatus("LAUNCHING");

        service.ingest(DRONE, frame("IN_AIR", SOURCE[0], SOURCE[1], 3, 0.5, 14.8));

        assertEquals("DEPARTED", mission.getStatus());
        verify(progressService).advance(eq(order), eq(mission), isNull(), any());
    }

    @Test
    void sittingOnThePadArmedDoesNotCountAsDeparted() {
        mission.setStatus("LAUNCHING");

        service.ingest(DRONE, frame("ON_GROUND", SOURCE[0], SOURCE[1], 3, 0.0, 14.8));

        assertEquals("LAUNCHING", mission.getStatus());
        verify(progressService, never()).advance(any(), any(), any(), any());
    }

    @Test
    void stagesFollowThePositionAlongTheRoute() {
        mission.setStatus("DEPARTED");

        service.ingest(DRONE, frame("IN_AIR", 10.8405, 106.8100, 3, 8.0, 14.8));   // ~55 m khỏi tủ gửi
        assertEquals("EN_ROUTE", mission.getStatus());

        service.ingest(DRONE, frame("IN_AIR", 10.8450, 106.8100, 3, 8.0, 14.8));   // giữa đường
        assertEquals("EN_ROUTE", mission.getStatus());

        service.ingest(DRONE, frame("IN_AIR", 10.8490, 106.8100, 3, 8.0, 14.8));   // ~110 m tới tủ nhận
        assertEquals("APPROACHING", mission.getStatus());

        service.ingest(DRONE, frame("LANDING", 10.8499, 106.8100, 3, 0.5, 14.8));  // đang hạ, chưa chạm đất
        assertEquals("APPROACHING", mission.getStatus());

        service.ingest(DRONE, frame("ON_GROUND", 10.8499, 106.8100, 3, 0.0, 14.8));
        assertEquals("ARRIVED", mission.getStatus());
    }

    @Test
    void depositIsNeverConfirmedByTelemetry() {
        mission.setStatus("ARRIVED");

        service.ingest(DRONE, frame("ON_GROUND", DESTINATION[0], DESTINATION[1], 3, 0.0, 14.8));

        assertEquals("ARRIVED", mission.getStatus());
        verify(progressService, never()).advance(any(), any(), any(), any());
    }

    @Test
    void landingAwayFromTheDestinationDoesNotArrive() {
        mission.setStatus("APPROACHING");

        service.ingest(DRONE, frame("ON_GROUND", 10.8450, 106.8100, 3, 0.0, 14.8));

        assertEquals("APPROACHING", mission.getStatus());
    }

    @Test
    void withoutAGpsFixOnlyTakeoffIsDetected() {
        mission.setStatus("DEPARTED");

        service.ingest(DRONE, frame("IN_AIR", 10.8490, 106.8100, 1, 8.0, 14.8));

        assertEquals("DEPARTED", mission.getStatus());
        verify(positionClient, never()).publish(any(), any());
    }

    @Test
    void demoMissionsAreLeftToTheSimulator() {
        mission.setStatus("LAUNCHING");
        order.setFulfillmentMode("DEMO");

        service.ingest(DRONE, frame("IN_AIR", SOURCE[0], SOURCE[1], 3, 0.5, 14.8));

        assertEquals("LAUNCHING", mission.getStatus());
        verify(positionClient, never()).publish(any(), any());
    }

    @Test
    void publishesTheRealPositionWithAnEtaFromDistanceAndSpeed() {
        mission.setStatus("EN_ROUTE");

        service.ingest(DRONE, frame("IN_AIR", 10.8450, 106.8100, 3, 5.0, 14.8));

        ArgumentCaptor<DronePositionUpdate> position = ArgumentCaptor.forClass(DronePositionUpdate.class);
        verify(positionClient).publish(eq(21L), position.capture());
        assertEquals("en_route", position.getValue().status());
        assertEquals(10.8450, position.getValue().lat());
        assertEquals(76, position.getValue().battery());
        // ~556 m còn lại ở 5 m/s ≈ 111 giây ⇒ làm tròn lên 2 phút.
        assertEquals(2, position.getValue().etaMinutes());
    }

    @Test
    void realBatteryIsSyncedToTheFleetButUsbPowerIsIgnored() {
        mission.setStatus("READY_TO_LAUNCH");

        service.ingest(DRONE, frame("ON_GROUND", SOURCE[0], SOURCE[1], 3, 0.0, 0.0));
        verify(lockerDroneClient, never()).reportTelemetry(any());

        service.ingest(DRONE, frame("ON_GROUND", SOURCE[0], SOURCE[1], 3, 0.0, 14.8));
        verify(lockerDroneClient, times(1)).reportTelemetry(new DroneTelemetryReport(DRONE, 76));
    }

    @Test
    void anOlderFrameArrivingLateIsDropped() {
        mission.setStatus("LAUNCHING");
        DroneTelemetryFrame airborne = frame("IN_AIR", SOURCE[0], SOURCE[1], 3, 0.5, 14.8);
        clock -= 5_000;
        DroneTelemetryFrame older = frame("ON_GROUND", SOURCE[0], SOURCE[1], 3, 0.0, 14.8);

        service.ingest(DRONE, airborne);
        service.ingest(DRONE, older);

        // Bản tin cũ bị bỏ trước khi đụng tới database.
        verify(missionRepository, times(1)).findFirstByDroneCodeAndStatusInOrderByIdDesc(eq(DRONE), anyList());
        assertEquals("DEPARTED", mission.getStatus());
    }

    private DroneTelemetryFrame frame(
            String landedState, double lat, double lng, int gpsFix, double speed, double voltage) {
        clock += 1_000;
        return new DroneTelemetryFrame(
                1, DRONE, clock, Instant.ofEpochMilli(clock).toString(),
                new DroneTelemetryFrame.Link("connected", 300L),
                new DroneTelemetryFrame.Position(lat, lng, 20.0, 0.0, 100L, false),
                new DroneTelemetryFrame.Gps(gpsFix, 14, 100L, false),
                new DroneTelemetryFrame.Battery(76, voltage, 12.5, 100L, false),
                new DroneTelemetryFrame.Velocity(speed, 0.0, 100L, false),
                new DroneTelemetryFrame.Flight("AUTO", true, "ACTIVE", 300L, false),
                new DroneTelemetryFrame.Landed(landedState, 100L, false));
    }

    private static LockerInfo locker(Long id, double[] at) {
        return new LockerInfo(id, 1L, "LK-" + id, "Locker " + id, "ACTIVE", "addr", at[0], at[1], true, null, 8, 4);
    }
}
