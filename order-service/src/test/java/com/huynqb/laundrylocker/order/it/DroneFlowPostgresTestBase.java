package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.LockerBoxSummary;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.event.DomainEvent;
import com.huynqb.laundrylocker.common.event.DomainEventNames;
import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.dto.CreateDroneDeliveryOrderRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.dto.DroneStatusTransitionRequest;
import com.huynqb.laundrylocker.order.dto.DroneUnitDto;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.LockerLayoutDto;
import com.huynqb.laundrylocker.order.dto.OpenReportDto;
import com.huynqb.laundrylocker.order.dto.OrderRefundResult;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupOfferRepository;
import com.huynqb.laundrylocker.order.repository.DronePickupRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.service.DroneDeliveryQueryService;
import com.huynqb.laundrylocker.order.service.DroneOrderBookingService;
import com.huynqb.laundrylocker.order.service.DronePickupService;
import com.huynqb.laundrylocker.order.service.OrderPaymentEventListener;
import com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

/// Một "thế giới" tủ/drone/người dùng giả cho các test vòng đời đơn drone trên Postgres thật. Mọi thứ
/// của order-service (service, giao dịch, khoá dòng, truy vấn, migration) là thật; chỉ locker-service,
/// user-service, notification-service và payment-service được thay bằng trạng thái trong bộ nhớ mà
/// test chỉnh được (cho drone lỗi, tủ bảo trì, mở phiếu sự cố…).
public abstract class DroneFlowPostgresTestBase extends OrderServicePostgresTestBase {

    /// Người gửi và điểm lấy hàng.
    protected static final long SENDER = 44L;
    protected static final double PICKUP_LAT = 10.7000;
    protected static final double PICKUP_LNG = 106.7000;

    /// Tủ A: gần điểm lấy hàng nhất (~110 m). Tủ C: phương án dự phòng (~1,1 km). Tủ B: tủ nhận.
    protected static final long LOCKER_A = 3L;
    protected static final long LOCKER_C = 7L;
    protected static final long LOCKER_B = 5L;
    protected static final long DEST_BOX = 9001L;

    /// KTV drone: NEAR cách điểm lấy hàng ~15 m, FAR cách ~550 m.
    protected static final long TECH_NEAR = 99L;
    protected static final long TECH_FAR = 98L;
    protected static final long ADMIN = 1L;

    protected static final long DRONE_A1 = 9L;
    protected static final long DRONE_A2 = 10L;
    protected static final long DRONE_C1 = 11L;

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected BusinessSettings settings;
    @Autowired
    protected LockerOrderRepository orderRepository;
    @Autowired
    protected DroneMissionRepository missionRepository;
    @Autowired
    protected DronePickupRepository pickupRepository;
    @Autowired
    protected DronePickupOfferRepository offerRepository;
    @Autowired
    protected DroneOrderBookingService bookingService;
    @Autowired
    protected DronePickupService pickupService;
    @Autowired
    protected DroneDeliveryQueryService queryService;
    @Autowired
    protected OrderPaymentEventListener paymentListener;

    /// Trạng thái giả của locker-service.
    protected final Map<Long, String> lockerStatus = new HashMap<>();
    protected final Map<Long, String> landingPadStatus = new HashMap<>();
    protected final Map<Long, DroneUnitDto> drones = new HashMap<>();
    protected final Map<Long, String> boxStatus = new HashMap<>();
    protected final List<OpenReportDto> openReports = new ArrayList<>();
    /// Thư đã "gửi" qua notification-service.
    protected final List<GuestNotification.Request> emails = new ArrayList<>();
    protected final List<Long> refundRequests = new ArrayList<>();
    private final AtomicLong paymentIds = new AtomicLong(500);
    private final AtomicLong idempotency = new AtomicLong();

    @BeforeEach
    void resetWorld() {
        jdbc.execute("TRUNCATE order_schema.drone_pickup_offers, order_schema.drone_pickups, "
                + "order_schema.drone_technician_presence, order_schema.drone_missions, "
                + "order_schema.order_status_history, order_schema.orders RESTART IDENTITY CASCADE");
        settings.update(Map.of(
                OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, false,
                OrderSettingsCatalog.DRONE_DEMO_ENABLED, false), null);

        lockerStatus.clear();
        landingPadStatus.clear();
        drones.clear();
        boxStatus.clear();
        openReports.clear();
        emails.clear();
        refundRequests.clear();
        for (long locker : List.of(LOCKER_A, LOCKER_B, LOCKER_C)) {
            lockerStatus.put(locker, "ACTIVE");
            landingPadStatus.put(locker, "OK");
        }
        drones.put(DRONE_A1, new DroneUnitDto(DRONE_A1, LOCKER_A, "DRONE-A1", "IDLE", 90, true));
        drones.put(DRONE_A2, new DroneUnitDto(DRONE_A2, LOCKER_A, "DRONE-A2", "IDLE", 80, true));
        drones.put(DRONE_C1, new DroneUnitDto(DRONE_C1, LOCKER_C, "DRONE-C1", "IDLE", 85, true));
        boxStatus.put(DEST_BOX, "AVAILABLE");

        // ---- locker-service
        lenient().when(lockerLookupClient.getLockers(any())).thenAnswer(call -> ApiResponse.ok(lockers()));
        lenient().when(lockerLookupClient.getLockers(isNull())).thenAnswer(call -> ApiResponse.ok(lockers()));
        lenient().when(lockerLookupClient.getBoxes(any())).thenReturn(ApiResponse.ok(List.of()));
        lenient().when(lockerDroneClient.getLockerLayout(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return ApiResponse.ok(new LockerLayoutDto(
                    id, "LK-" + id, "Locker " + id, lockerStatus.get(id), true, landingPadStatus.get(id)));
        });
        lenient().when(lockerDroneClient.listDroneUnits(any())).thenAnswer(call -> {
            Long lockerId = call.getArgument(0);
            return ApiResponse.ok(drones.values().stream()
                    .filter(drone -> lockerId == null || Objects.equals(drone.lockerId(), lockerId))
                    .toList());
        });
        lenient().when(lockerDroneClient.getDroneUnit(anyLong()))
                .thenAnswer(call -> ApiResponse.ok(drones.get(call.<Long>getArgument(0))));
        lenient().when(lockerDroneClient.transitionDroneStatus(anyLong(), any())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            DroneStatusTransitionRequest request = call.getArgument(1);
            DroneUnitDto drone = drones.get(id);
            if (!request.expectedStatus().equals(drone.status())) {
                throw new IllegalStateException(
                        "DRONE_STATUS_CONFLICT expected " + request.expectedStatus() + " but was " + drone.status());
            }
            DroneUnitDto next = new DroneUnitDto(
                    id, drone.lockerId(), drone.code(), request.status(), drone.batteryPercent(), drone.active());
            drones.put(id, next);
            return ApiResponse.ok(next);
        });
        lenient().when(lockerDroneClient.openReports()).thenAnswer(call -> ApiResponse.ok(List.copyOf(openReports)));
        lenient().when(lockerCellClient.findAvailable(anyLong(), any(), any())).thenAnswer(call ->
                !java.util.Objects.equals(call.getArgument(0), LOCKER_B) || "AVAILABLE".equals(boxStatus.get(DEST_BOX))
                        ? ApiResponse.ok(new CellDto(DEST_BOX, 1, "M", "DRONE", 0, 0, "AVAILABLE", null, LOCKER_B))
                        : ApiResponse.ok(null));
        lenient().when(lockerCellClient.getCell(anyLong())).thenAnswer(call ->
                ApiResponse.ok(new CellDto(DEST_BOX, 1, "M", "DRONE", 0, 0, "AVAILABLE", null, LOCKER_B)));
        lenient().when(lockerClient.reserveBox(anyLong(), any())).thenAnswer(call -> setBox(call.getArgument(0), "RESERVED"));
        lenient().when(lockerClient.occupyBox(anyLong())).thenAnswer(call -> setBox(call.getArgument(0), "OCCUPIED"));
        lenient().when(lockerClient.releaseBox(anyLong())).thenAnswer(call -> setBox(call.getArgument(0), "AVAILABLE"));
        lenient().when(lockerClient.getBox(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return ApiResponse.ok(new LockerBoxSummary(LOCKER_B, id, "LK-5", 1, boxStatus.get(id)));
        });

        // ---- user-service
        lenient().when(userClient.getUser(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return ApiResponse.ok(new UserSummary(id, "user" + id + "@lockr.test", "09000000" + id, "User " + id, "ACTIVE"));
        });
        lenient().when(userClient.getUserByPhone(any())).thenReturn(ApiResponse.ok(null));
        lenient().when(userClient.getUsersByRole(any())).thenReturn(ApiResponse.ok(List.of()));

        // ---- notification-service: kênh email "thật" đã cấu hình
        lenient().when(notificationClient.notifyGuest(any())).thenAnswer(call -> {
            emails.add(call.getArgument(0));
            return ApiResponse.ok(new GuestNotification.Result(false, true, false, true));
        });

        // ---- payment-service: ghi nhận yêu cầu hoàn đúng số tiền đơn đã thu
        lenient().when(paymentRefundClient.refundOrder(anyLong(), any(), any())).thenAnswer(call -> {
            Long orderId = call.getArgument(0);
            refundRequests.add(orderId);
            BigDecimal paid = orderRepository.findById(orderId).map(LockerOrder::getPaidAmount).orElse(BigDecimal.ZERO);
            return ApiResponse.ok(new OrderRefundResult(orderId, paid, 1));
        });
    }

    private ApiResponse<LockerBoxSummary> setBox(Long id, String status) {
        boxStatus.put(id, status);
        return ApiResponse.ok(new LockerBoxSummary(LOCKER_B, id, "LK-5", 1, status));
    }

    protected List<LockerInfo> lockers() {
        return List.of(
                locker(LOCKER_A, 10.7010, 106.7000),
                locker(LOCKER_C, 10.7100, 106.7000),
                locker(LOCKER_B, 10.7200, 106.7100));
    }

    private LockerInfo locker(long id, double latitude, double longitude) {
        return new LockerInfo(
                id, 1L, "LK-" + id, "Locker " + id, lockerStatus.get(id), "Địa chỉ tủ " + id,
                latitude, longitude, true, null, 8, 4);
    }

    // ------------------------------------------------------------------ steps

    protected void technicianOnDuty(long technicianId, double latitude, double longitude) {
        pickupService.updatePresence(technicianId, true, latitude, longitude);
    }

    protected void bothTechniciansOnDuty() {
        technicianOnDuty(TECH_NEAR, 10.7001, 106.7001);
        technicianOnDuty(TECH_FAR, 10.7050, 106.7000);
    }

    protected CreateDroneDeliveryOrderRequest request() {
        return request(PICKUP_LAT, PICKUP_LNG, 1200);
    }

    protected CreateDroneDeliveryOrderRequest request(double latitude, double longitude, int weightGrams) {
        return new CreateDroneDeliveryOrderRequest(
                LOCKER_B, null,
                "12 Nguyễn Huệ, Quận 1", latitude, longitude, "Gọi trước khi tới",
                "Nguyễn Văn Gửi", "0901000111", "gui@gmail.com",
                "Trần Thị Nhận", "0902000222", "nhan@gmail.com",
                weightGrams, "DOCUMENT", "Hồ sơ", null, null, null, null, false, true,
                "STANDARD", null, null);
    }

    protected DroneDeliveryOrderResponse book() {
        return bookingService.create(request(), SENDER, "it-" + idempotency.incrementAndGet());
    }

    /// payment-service báo đã thu đủ phần còn thiếu của đơn.
    protected void pay(long orderId) {
        LockerOrder order = orderRepository.findById(orderId).orElseThrow();
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        BigDecimal due = order.getTotalPrice().subtract(paid);
        paymentListener.onPaymentEvent(DomainEvent.of(
                DomainEventNames.PAYMENT_COMPLETED,
                "payment-service",
                Map.of("orderId", orderId, "paymentId", paymentIds.incrementAndGet(), "amount", due)));
    }

    /// Đặt → trả tiền → KTV gần nhất nhận → lấy hàng. Trả về id đơn ở chặng PICKED_UP.
    protected long bookedAndPickedUpBy(long technicianId) {
        long orderId = book().orderId();
        pay(orderId);
        pickupService.accept(orderId, technicianId);
        pickupService.confirmPickedUp(orderId, technicianId, pickup(orderId).getPickupCode(), true, null);
        return orderId;
    }

    protected LockerOrder order(long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    protected DronePickup pickup(long orderId) {
        return pickupRepository.findByOrderId(orderId).orElseThrow();
    }

    protected DroneMission mission(long orderId) {
        return missionRepository.findByOrderId(orderId).orElseThrow();
    }

    protected void setDroneStatus(long droneId, String status) {
        DroneUnitDto drone = drones.get(droneId);
        drones.put(droneId, new DroneUnitDto(
                droneId, drone.lockerId(), drone.code(), status, drone.batteryPercent(), drone.active()));
    }

    protected List<String> journeyNotes(long orderId) {
        return jdbc.queryForList(
                "select note from order_schema.order_status_history where order_id = ? order by id", String.class, orderId);
    }
}
