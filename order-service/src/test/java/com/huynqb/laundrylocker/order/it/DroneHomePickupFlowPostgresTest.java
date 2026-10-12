package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.AcceptDroneOrderRequest;
import com.huynqb.laundrylocker.order.dto.ConfirmDroneLoadingRequest;
import com.huynqb.laundrylocker.order.dto.CreateDroneDeliveryOrderRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryQuoteRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryQuoteResponse;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.service.DroneDispatchSweeper;
import com.huynqb.laundrylocker.order.service.DroneMissionProgressService;
import com.huynqb.laundrylocker.order.service.DroneOrderMaintenanceService;
import com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// Luồng 1 + 2 trên Postgres thật: đặt đơn (trường bắt buộc, chọn tủ gửi), thanh toán, mời KTV drone
/// rảnh gần người gửi nhất, lấy hàng tận nhà, mang về tủ, phóng drone và gửi thư cho hai đầu.
class DroneHomePickupFlowPostgresTest extends DroneFlowPostgresTestBase {

    @Autowired
    private DroneOrderMaintenanceService maintenanceService;
    @Autowired
    private DroneMissionProgressService progressService;
    @Autowired
    private DroneDispatchSweeper dispatchSweeper;

    // ------------------------------------------------------------------ đặt đơn

    @Test
    void quoteAndBookingPickTheLockerNearestToTheSender() {
        DroneDeliveryQuoteResponse quote = bookingService.quote(
                new DroneDeliveryQuoteRequest(LOCKER_B, PICKUP_LAT, PICKUP_LNG, 1200));

        assertEquals(LOCKER_A, quote.sourceLocker().lockerId());
        assertTrue(quote.pickupDistanceMeters() > 90 && quote.pickupDistanceMeters() < 130,
                "pickup → locker A should be ~110 m, was " + quote.pickupDistanceMeters());
        assertTrue(quote.routeDistanceMeters() > 2000 && quote.routeDistanceMeters() < 3000);
        // 1.200 g = phí cơ bản 15.000 đ (500 g) + 3 nấc 250 g × 3.000 đ.
        assertEquals(0, new BigDecimal("24000").compareTo(quote.fee()));

        DroneDeliveryOrderResponse booked = book();
        LockerOrder order = order(booked.orderId());

        assertEquals("AWAITING_DISPATCH", order.getStatus());
        assertEquals("AWAITING_DISPATCH", order.getDeliveryStage());
        assertEquals("UNPAID", order.getPaymentStatus());
        assertEquals(LOCKER_A, order.getSourceLockerId());
        assertEquals(LOCKER_B, order.getDestinationLockerId());
        assertEquals(DEST_BOX, order.getReservedBoxId());
        assertEquals("RESERVED", boxStatus.get(DEST_BOX));
        // Không còn giữ ô ở tủ gửi: kiện đi thẳng từ tay KTV lên drone.
        assertNull(order.getSourceBoxId());
        assertEquals("12 Nguyễn Huệ, Quận 1", order.getPickupAddress());
        assertEquals(PICKUP_LAT, order.getPickupLatitude());
        assertEquals("gui@gmail.com", order.getSenderEmail());
        assertEquals("nhan@gmail.com", order.getReceiverEmail());
        assertEquals("0902000222", order.getReceiverPhone());
        assertEquals(0, new BigDecimal("24000").compareTo(order.getTotalPrice()));
        // Chưa trả tiền thì chưa mời ai.
        assertTrue(pickupRepository.findByOrderId(order.getId()).isEmpty());
        assertEquals("gui@gmail.com", booked.sender().email());
        assertNotNull(booked.pickup());
        assertNull(booked.pickup().status());
    }

    @Test
    void bookingFallsBackToTheNextLockerWhenTheNearestCannotServe() {
        // Tủ A hết drone dùng được ⇒ chọn tủ C (xa hơn nhưng trong bán kính).
        setDroneStatus(DRONE_A1, "FAULT");
        setDroneStatus(DRONE_A2, "MAINTENANCE");
        assertEquals(LOCKER_C, order(book().orderId()).getSourceLockerId());

        // Bãi đáp tủ C cũng hỏng ⇒ không tủ nào gần người gửi phục vụ được.
        landingPadStatus.put(LOCKER_C, "BLOCKED");
        boxStatus.put(DEST_BOX, "AVAILABLE");
        assertRejected("DRONE_NO_SOURCE_LOCKER", request());
    }

    @Test
    void bookingEnforcesRequiredFieldsRangeAndWeightLimit() {
        // Điểm lấy hàng cách mọi tủ hơn 5 km.
        assertRejected("DRONE_PICKUP_OUT_OF_RANGE", request(10.9000, 106.9000, 1200));
        // Tối đa 5 kg một đơn.
        assertRejected("DRONE_PAYLOAD_TOO_HEAVY", request(PICKUP_LAT, PICKUP_LNG, 5001));
        assertRejected("DRONE_SENDER_EMAIL_INVALID", with("senderEmail", "khong-phai-email"));
        assertRejected("DRONE_RECEIVER_EMAIL_INVALID", with("receiverEmail", " "));
        assertRejected("DRONE_RECEIVER_PHONE_INVALID", with("receiverPhone", "12ab"));
        assertRejected("DRONE_SENDER_NAME_REQUIRED", with("senderName", ""));
        assertRejected("DRONE_PICKUP_ADDRESS_REQUIRED", with("pickupAddress", "  "));
        assertRejected("DRONE_PARCEL_CATEGORY_INVALID", with("parcelCategory", "WEAPON"));
        assertRejected("DRONE_PROHIBITED_ITEMS_NOT_DECLARED", with("prohibitedItemsDeclared", false));

        settings.update(Map.of(OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, true), null);
        assertRejected("DRONE_FLIGHTS_SUSPENDED", request());

        // Không lỗi nào được để lại ô nhận bị giữ hoặc đơn rác.
        assertEquals("AVAILABLE", boxStatus.get(DEST_BOX));
        assertEquals(0, orderRepository.count());
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameOrderWithoutReservingAgain() {
        DroneDeliveryOrderResponse first = bookingService.create(request(), SENDER, "same-key");
        DroneDeliveryOrderResponse again = bookingService.create(request(), SENDER, "same-key");

        assertEquals(first.orderId(), again.orderId());
        assertEquals(1, orderRepository.count());
    }

    // ------------------------------------------------------------------ điều phối KTV

    @Test
    void paymentOffersThePickupToTheNearestIdleTechnicianOnDuty() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();

        pay(orderId);

        LockerOrder order = order(orderId);
        DronePickup pickup = pickup(orderId);
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("AWAITING_DISPATCH", order.getDeliveryStage());
        assertEquals(DronePickup.OFFERED, pickup.getStatus());
        assertEquals(TECH_NEAR, pickup.getTechnicianId());
        assertTrue(pickup.getTechnicianDistanceMeters() < 50);
        assertNotNull(pickup.getOfferExpiresAt());
        assertTrue(pickup.getPickupCode().matches("\\d{6}"));

        // Người được mời mới nhận được; người kia thì không.
        assertEquals("DRONE_PICKUP_NOT_OFFERED", code(() -> pickupService.accept(orderId, TECH_FAR)));
        pickupService.accept(orderId, TECH_NEAR);

        assertEquals("PICKUP_ASSIGNED", order(orderId).getDeliveryStage());
        assertEquals(DronePickup.ACCEPTED, pickup(orderId).getStatus());
        assertTrue(pickupService.busy(TECH_NEAR));
        assertFalse(pickupService.busy(TECH_FAR));

        // Mã lấy hàng chỉ người gửi thấy; KTV phải được người gửi đọc cho.
        assertEquals(pickup.getPickupCode(), queryService.get(orderId, SENDER).pickup().pickupCode());
        assertNull(queryService.getForTechnician(orderId, TECH_NEAR).pickup().pickupCode());
        // KTV không liên quan không xem được đơn.
        assertEquals("DRONE_MISSION_NOT_ASSIGNED_TO_USER", code(() -> queryService.getForTechnician(orderId, TECH_FAR)));
    }

    @Test
    void decliningOrIgnoringAnOfferPassesItToTheNextNearestTechnician() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);

        pickupService.decline(orderId, TECH_NEAR);
        assertEquals(TECH_FAR, pickup(orderId).getTechnicianId());
        assertEquals(DronePickup.OFFERED, pickup(orderId).getStatus());

        // Người thứ hai để lời mời quá hạn: cả hai vừa bỏ qua nên chưa mời lại ai.
        LocalDateTime afterExpiry = LocalDateTime.now().plusSeconds(181);
        pickupService.sweep(afterExpiry);
        assertEquals(DronePickup.SEARCHING, pickup(orderId).getStatus());
        assertNull(pickup(orderId).getTechnicianId());

        // Hết thời gian chờ mời lại ⇒ lại mời người gần nhất (vị trí còn hiệu lực).
        technicianOnDuty(TECH_NEAR, 10.7001, 106.7001);
        jdbc.update("update order_schema.drone_pickup_offers set responded_at = responded_at - interval '11 minutes'");
        pickupService.sweep(LocalDateTime.now());
        assertEquals(TECH_NEAR, pickup(orderId).getTechnicianId());
    }

    @Test
    void aBusyTechnicianIsSkippedAndAnOrderWaitsWhenNobodyIsFree() {
        technicianOnDuty(TECH_NEAR, 10.7001, 106.7001);
        long first = book().orderId();
        pay(first);
        pickupService.accept(first, TECH_NEAR);

        // Đơn thứ hai: người gần nhất đang bận, chưa ai khác trong ca ⇒ chờ.
        boxStatus.put(DEST_BOX, "AVAILABLE");
        long second = book().orderId();
        pay(second);
        assertEquals(DronePickup.SEARCHING, pickup(second).getStatus());

        // KTV thứ hai vào ca ⇒ được mời ngay.
        technicianOnDuty(TECH_FAR, 10.7050, 106.7000);
        assertEquals(TECH_FAR, pickup(second).getTechnicianId());
        assertEquals(DronePickup.OFFERED, pickup(second).getStatus());
    }

    @Test
    void technicianWithStaleLocationOrOffDutyIsNotOffered() {
        technicianOnDuty(TECH_NEAR, 10.7001, 106.7001);
        technicianOnDuty(TECH_FAR, 10.7050, 106.7000);
        // App của người gần nhất đã 11 phút không gửi vị trí.
        jdbc.update("update order_schema.drone_technician_presence set location_at = location_at - interval '11 minutes' "
                + "where user_id = ?", TECH_NEAR);
        long orderId = book().orderId();
        pay(orderId);
        assertEquals(TECH_FAR, pickup(orderId).getTechnicianId());

        // Ra ca khi đang được mời = từ chối; không còn ai ⇒ chờ.
        pickupService.updatePresence(TECH_FAR, false, null, null);
        assertEquals(DronePickup.SEARCHING, pickup(orderId).getStatus());
    }

    @Test
    void acceptedTechnicianCannotGoOffDutyAndIsReplacedWhenTheyNeverShowUp() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        pickupService.accept(orderId, TECH_NEAR);

        assertEquals("DRONE_TECHNICIAN_HAS_ACTIVE_PICKUP",
                code(() -> pickupService.updatePresence(TECH_NEAR, false, null, null)));

        // Quá 60 phút chưa lấy hàng ⇒ thu hồi, đơn về chờ điều phối và mời người kế tiếp.
        pickupService.sweep(LocalDateTime.now().plusMinutes(61));
        // Vị trí của người còn lại đã "cũ" theo mốc thời gian giả lập, nên chưa mời được ai.
        assertEquals("AWAITING_DISPATCH", order(orderId).getDeliveryStage());
        assertFalse(pickupService.busy(TECH_NEAR));
        technicianOnDuty(TECH_FAR, 10.7050, 106.7000);
        assertEquals(TECH_FAR, pickup(orderId).getTechnicianId());
    }

    @Test
    void missedPaymentHookIsRepairedByTheDispatchSweep() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        // Đơn đã PAID nhưng bước mở lượt lấy hàng sau thanh toán bị lỡ (service khởi động lại…).
        jdbc.update("update order_schema.orders set payment_status = 'PAID', paid_at = now(), paid_amount = total_price "
                + "where id = ?", orderId);

        dispatchSweeper.dispatchScheduled();

        assertEquals(TECH_NEAR, pickup(orderId).getTechnicianId());
    }

    @Test
    void adminAssignsATechnicianByHand() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        assertEquals(TECH_NEAR, pickup(orderId).getTechnicianId());

        var candidates = pickupService.candidates(orderId);
        assertEquals(2, candidates.size());
        // Người đang được mời cho chính đơn này tính là bận; người rảnh lên đầu.
        assertEquals(TECH_FAR, candidates.getFirst().technicianId());

        pickupService.assignByAdmin(orderId, TECH_FAR, ADMIN);

        DronePickup pickup = pickup(orderId);
        assertEquals(DronePickup.ACCEPTED, pickup.getStatus());
        assertEquals(TECH_FAR, pickup.getTechnicianId());
        assertEquals(ADMIN, pickup.getAssignedByUserId());
        assertEquals("PICKUP_ASSIGNED", order(orderId).getDeliveryStage());
        assertFalse(pickupService.busy(TECH_NEAR));
    }

    // ------------------------------------------------------------------ lấy hàng → phóng

    @Test
    void fullJourneyFromHomePickupToTheReceiverLocker() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        pickupService.accept(orderId, TECH_NEAR);
        String pickupCode = pickup(orderId).getPickupCode();

        // Tới nhà người gửi: sai mã thì không lấy được.
        assertEquals("DRONE_PICKUP_CODE_INVALID",
                code(() -> pickupService.confirmPickedUp(orderId, TECH_NEAR, "000000".equals(pickupCode) ? "111111" : "000000", true, null)));
        assertEquals("DRONE_PICKUP_NOT_ASSIGNED_TO_USER",
                code(() -> pickupService.confirmPickedUp(orderId, TECH_FAR, pickupCode, true, null)));
        // Chưa lấy hàng thì chưa gán drone được.
        assertEquals("DRONE_PARCEL_NOT_PICKED_UP",
                code(() -> maintenanceService.accept(orderId, TECH_NEAR, "acc-0", new AcceptDroneOrderRequest(DRONE_A1))));

        pickupService.confirmPickedUp(orderId, TECH_NEAR, pickupCode, true, "Kiện nguyên vẹn");
        assertEquals("PICKED_UP", order(orderId).getDeliveryStage());
        assertNotNull(order(orderId).getParcelDroppedAt());
        assertTrue(pickupService.busy(TECH_NEAR), "đang cầm kiện thì chưa rảnh");
        // Đã cầm kiện: người gửi không tự huỷ được nữa.
        assertEquals("DRONE_PICKUP_NOT_ASSIGNED_TO_USER",
                code(() -> maintenanceService.accept(orderId, TECH_FAR, "acc-x", new AcceptDroneOrderRequest(DRONE_A1))));

        // Mang về tủ gửi A: drone phải đỗ đúng tủ đó.
        assertEquals("DRONE_WRONG_SOURCE_LOCKER",
                code(() -> maintenanceService.accept(orderId, TECH_NEAR, "acc-c", new AcceptDroneOrderRequest(DRONE_C1))));
        maintenanceService.accept(orderId, TECH_NEAR, "acc-1", new AcceptDroneOrderRequest(DRONE_A1));
        assertEquals("ACCEPTED", order(orderId).getDeliveryStage());
        assertEquals("RESERVED", drones.get(DRONE_A1).status());
        assertEquals("AWAITING_LOADING", mission(orderId).getStatus());

        maintenanceService.confirmLoading(orderId, TECH_NEAR, "load-1",
                new ConfirmDroneLoadingRequest(1200, null, true, true, true, null));
        assertEquals("READY_TO_LAUNCH", mission(orderId).getStatus());

        emails.clear();
        maintenanceService.launch(orderId, TECH_NEAR, "launch-1");
        assertEquals("LAUNCHING", order(orderId).getDeliveryStage());
        assertEquals("IN_FLIGHT", drones.get(DRONE_A1).status());
        assertEquals(90, mission(orderId).getBatteryPercentAtLaunch());
        assertTrue(pickupService.busy(TECH_NEAR), "nhiệm vụ bay chưa kết thúc thì KTV vẫn bận");

        // Thư thật cho hai đầu ngay khi phóng.
        assertEquals(2, emails.size());
        GuestNotification.Request toSender = emailTo("gui@gmail.com");
        GuestNotification.Request toReceiver = emailTo("nhan@gmail.com");
        assertTrue(toSender.emailBody().contains("Trần Thị Nhận"));
        assertTrue(toSender.emailBody().contains("0902000222"));
        assertTrue(toSender.emailBody().contains("nhan@gmail.com"));
        assertTrue(toSender.emailBody().contains("Locker " + LOCKER_B));
        assertTrue(toReceiver.emailBody().contains("Nguyễn Văn Gửi"));
        assertTrue(toReceiver.emailBody().contains("0901000111"));
        assertTrue(toReceiver.emailBody().contains("gui@gmail.com"));
        // notification-service từ chối yêu cầu thiếu smsMessage, kể cả khi chỉ gửi email.
        assertFalse(toSender.smsMessage().isBlank());
        assertNull(toSender.phone());
        assertTrue(journeyNotes(orderId).stream().anyMatch(note -> note.contains("Đã gửi email báo đơn đã phóng")));

        // Bay tới tủ nhận, hàng vào ô.
        for (int i = 0; i < 5; i++) {
            progressService.advanceByOperator(orderId, TECH_NEAR);
        }
        LockerOrder delivered = order(orderId);
        assertEquals("STORING", delivered.getStatus());
        assertEquals("READY_FOR_PICKUP", delivered.getDeliveryStage());
        assertNotNull(delivered.getPinCode());
        assertEquals("OCCUPIED", boxStatus.get(DEST_BOX));
        assertEquals("IDLE", drones.get(DRONE_A1).status());
        assertNotNull(mission(orderId).getLandedAt());
        assertEquals(TECH_NEAR, mission(orderId).getDepositedByUserId());
        // Mã mở ô tới email người nhận.
        assertTrue(emailTo("nhan@gmail.com", "Mã mở tủ").emailBody().contains(delivered.getPinCode()));
    }

    @Test
    void senderMayCancelUntilTheParcelIsPickedUpAndTheTechnicianIsFreed() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        pickupService.accept(orderId, TECH_NEAR);

        // Đường huỷ của khách: OrderService.cancel rồi nhả lượt lấy hàng (đúng thứ tự controller gọi).
        orderService.cancel(orderId, null, SENDER);
        pickupService.closeForOrder(orderId);

        assertEquals("CANCELED", order(orderId).getStatus());
        assertEquals(DronePickup.CANCELED, pickup(orderId).getStatus());
        assertFalse(pickupService.busy(TECH_NEAR));
        assertEquals("AVAILABLE", boxStatus.get(DEST_BOX));

        // Sau khi KTV đã cầm kiện thì khách không tự huỷ được.
        long second = bookedAndPickedUpBy(TECH_NEAR);
        assertEquals("DRONE_ORDER_STATUS_INVALID", code(() -> orderService.cancel(second, null, SENDER)));
    }

    @Autowired
    private com.huynqb.laundrylocker.order.service.OrderService orderService;

    // ------------------------------------------------------------------ helpers

    private GuestNotification.Request emailTo(String address) {
        return emails.stream().filter(mail -> address.equals(mail.email())).findFirst()
                .orElseThrow(() -> new AssertionError("no email sent to " + address));
    }

    private GuestNotification.Request emailTo(String address, String subjectPart) {
        return emails.stream()
                .filter(mail -> address.equals(mail.email()) && mail.subject().contains(subjectPart))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no '" + subjectPart + "' email sent to " + address));
    }

    private void assertRejected(String expectedCode, CreateDroneDeliveryOrderRequest request) {
        assertEquals(expectedCode, code(() -> bookingService.create(request, SENDER, "rej-" + System.nanoTime())));
    }

    /// Bản sao đơn mẫu với một trường bị đổi — để kiểm từng trường bắt buộc.
    private CreateDroneDeliveryOrderRequest with(String field, Object value) {
        CreateDroneDeliveryOrderRequest r = request();
        return new CreateDroneDeliveryOrderRequest(
                r.destinationLockerId(), r.preferredBoxId(),
                "pickupAddress".equals(field) ? (String) value : r.pickupAddress(),
                r.pickupLatitude(), r.pickupLongitude(), r.pickupNote(),
                "senderName".equals(field) ? (String) value : r.senderName(),
                r.senderPhone(),
                "senderEmail".equals(field) ? (String) value : r.senderEmail(),
                r.receiverName(),
                "receiverPhone".equals(field) ? (String) value : r.receiverPhone(),
                "receiverEmail".equals(field) ? (String) value : r.receiverEmail(),
                r.parcelWeightGrams(),
                "parcelCategory".equals(field) ? (String) value : r.parcelCategory(),
                r.description(), r.parcelLengthCm(), r.parcelWidthCm(), r.parcelHeightCm(), r.declaredValue(),
                r.fragile(),
                "prohibitedItemsDeclared".equals(field) ? (Boolean) value : r.prohibitedItemsDeclared(),
                r.fulfillmentMode());
    }

    static String code(Runnable call) {
        return assertThrows(BusinessException.class, call::run).getCode();
    }
}
