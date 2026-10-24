package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.dto.AcceptDroneOrderRequest;
import com.huynqb.laundrylocker.order.dto.ConfirmDroneLoadingRequest;
import com.huynqb.laundrylocker.order.dto.DroneDeliveryOrderResponse;
import com.huynqb.laundrylocker.order.dto.GuestNotification;
import com.huynqb.laundrylocker.order.dto.OpenReportDto;
import com.huynqb.laundrylocker.order.model.DroneFailureReasons;
import com.huynqb.laundrylocker.order.model.DronePickup;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.service.DroneDeliveryBlockService;
import com.huynqb.laundrylocker.order.service.DroneDeliveryBlockService.Verdict;
import com.huynqb.laundrylocker.order.service.DroneDeliveryFailureService;
import com.huynqb.laundrylocker.order.service.DroneDeliveryFailureService.Actor;
import com.huynqb.laundrylocker.order.service.DroneDispatchSweeper;
import com.huynqb.laundrylocker.order.service.DroneOrderMaintenanceService;
import com.huynqb.laundrylocker.order.service.DroneOrderTimeoutSweeper;
import com.huynqb.laundrylocker.order.service.DroneParcelService;
import com.huynqb.laundrylocker.order.service.DroneRefundService;
import com.huynqb.laundrylocker.order.settings.OrderSettingsCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/// Luồng 3 trên Postgres thật: đơn đã thanh toán gặp sự cố (thời tiết, phiếu sự cố trên drone/tủ/bãi
/// đáp, không có KTV, không lấy được hàng) ⇒ bị chặn, được gỡ khi hết sự cố, hoặc kết thúc "giao không
/// hoàn tất" kèm hoàn tiền, trả kiện và mối nối tới phiếu sự cố.
class DroneDeliveryFailureFlowPostgresTest extends DroneFlowPostgresTestBase {

    @Autowired
    private DroneOrderMaintenanceService maintenanceService;
    @Autowired
    private DroneDeliveryBlockService blockService;
    @Autowired
    private DroneDeliveryFailureService failureService;
    @Autowired
    private DroneDispatchSweeper dispatchSweeper;
    @Autowired
    private DroneOrderTimeoutSweeper timeoutSweeper;
    @Autowired
    private DroneRefundService refundService;
    @Autowired
    private DroneParcelService parcelService;

    private Verdict review(long orderId) {
        return blockService.review(orderId, LocalDateTime.now(), new DroneDeliveryBlockService.Snapshot()).verdict();
    }

    private long acceptedWithDrone(long droneId) {
        long orderId = bookedAndPickedUpBy(TECH_NEAR);
        maintenanceService.accept(orderId, TECH_NEAR, "acc-" + orderId, new AcceptDroneOrderRequest(droneId));
        return orderId;
    }

    @Test
    void sweeperAutomaticallyTriesAnIdleDroneAtTheSameLockerBeforeClosingTheOrder() {
        bothTechniciansOnDuty();
        long orderId = acceptedWithDrone(DRONE_A1);
        setDroneStatus(DRONE_A1, "FAULT");
        openReports.add(new OpenReportDto(77L, LOCKER_A, null, DRONE_A1, "Motor rung", "OPEN", "DRONE", false));
        assertEquals(0, dispatchSweeper.reviewBlocksAt(LocalDateTime.now()));
        assertEquals(DRONE_A2, mission(orderId).getDroneUnitId());
        assertEquals("RESERVED", drones.get(DRONE_A2).status());
        assertEquals("AWAITING_LOADING", mission(orderId).getStatus());
        assertNull(order(orderId).getDeliveryBlockReason());
        assertEquals("AWAITING_DISPATCH", order(orderId).getStatus());
        assertEquals("PAID", order(orderId).getPaymentStatus());
    }

    @Test
    void aDroneBusyWithAnotherOrderDoesNotHoldBackANewPaidOrder() {
        // Chỉ một KTV trong ca; đơn mới đặt khi drone tủ A còn rảnh, chưa trả tiền.
        technicianOnDuty(TECH_NEAR, 10.7001, 106.7001);
        long busyOrder = bookedAndPickedUpBy(TECH_NEAR);
        boxStatus.put(DEST_BOX, "AVAILABLE");
        long newOrder = book().orderId();
        assertEquals(LOCKER_A, order(newOrder).getSourceLockerId());

        // KTV kia gán drone A1 cho đơn của mình, drone A2 đang bay chuyến khác; tủ C không thay được.
        maintenanceService.accept(busyOrder, TECH_NEAR, "acc-busy", new AcceptDroneOrderRequest(DRONE_A1));
        setDroneStatus(DRONE_A2, "IN_FLIGHT");
        landingPadStatus.put(LOCKER_C, "BLOCKED");

        // KTV rảnh vào ca, khách trả tiền ⇒ KTV rảnh được mời; lượt soát sự cố không chặn đơn.
        technicianOnDuty(TECH_FAR, 10.7050, 106.7000);
        pay(newOrder);
        assertEquals(TECH_FAR, pickup(newOrder).getTechnicianId());
        assertEquals(0, dispatchSweeper.reviewBlocksAt(LocalDateTime.now()));
        assertNull(order(newOrder).getDeliveryBlockReason());
        assertEquals(LOCKER_A, order(newOrder).getSourceLockerId());
        assertTrue(queryService.operations(null, TECH_FAR, false).stream()
                .anyMatch(o -> o.orderId().equals(newOrder)), "KTV được mời thấy đơn trong danh sách");
        pickupService.accept(newOrder, TECH_FAR);
        assertEquals("PICKUP_ASSIGNED", order(newOrder).getDeliveryStage());

        // Drone ở tủ gửi hỏng thật (không còn chiếc nào bay được) thì vẫn chặn như cũ.
        setDroneStatus(DRONE_A1, "FAULT");
        setDroneStatus(DRONE_A2, "MAINTENANCE");
        assertEquals(Verdict.BLOCKED, review(newOrder));
        assertEquals(DroneFailureReasons.NO_DRONE_AVAILABLE, order(newOrder).getDeliveryBlockReason());
    }

    // ------------------------------------------------------------------ phiếu sự cố trên drone

    @Test
    void droneFaultTicketBlocksTheOrderLinksTheTicketAndClearsWhenTheDroneIsChanged() {
        bothTechniciansOnDuty();
        long orderId = acceptedWithDrone(DRONE_A1);
        assertEquals(Verdict.UNCHANGED, review(orderId));

        // KTV sự cố mở phiếu #77 cho drone A1 ⇒ locker-service chuyển drone sang FAULT.
        setDroneStatus(DRONE_A1, "FAULT");
        openReports.add(new OpenReportDto(77L, LOCKER_A, null, DRONE_A1, "Motor rung", "OPEN", "DRONE", false));

        assertEquals(Verdict.BLOCKED, review(orderId));
        LockerOrder blocked = order(orderId);
        assertEquals(DroneFailureReasons.DRONE_FAULT, blocked.getDeliveryBlockReason());
        assertEquals(77L, blocked.getDeliveryBlockTicketId());
        assertNotNull(blocked.getDeliveryBlockedAt());
        assertEquals("ACCEPTED", blocked.getDeliveryStage(), "bị chặn không đổi chặng");
        // Lượt soát kế tiếp không báo lại.
        assertEquals(Verdict.UNCHANGED, review(orderId));

        // Ba bên cùng thấy mối nối: người gửi thấy lý do + phiếu, phiếu thấy đơn bị ảnh hưởng.
        DroneDeliveryOrderResponse forSender = queryService.get(orderId, SENDER);
        assertEquals(DroneFailureReasons.DRONE_FAULT, forSender.block().reason());
        assertEquals(77L, forSender.block().ticketId());
        assertNotNull(forSender.block().autoCloseAt());
        assertEquals(List.of(orderId), queryService.byTicket(77L).stream().map(DroneDeliveryOrderResponse::orderId).toList());

        // Không nạp hàng lên drone đang lỗi được: phải đổi drone hoặc đóng đơn.
        assertEquals("DRONE_RESERVATION_LOST", assertThrows(BusinessException.class,
                () -> maintenanceService.confirmLoading(orderId, TECH_NEAR, "load-1",
                        new ConfirmDroneLoadingRequest(1200, null, true, true, true, null))).getCode());

        // Còn drone rảnh khác tại tủ gửi ⇒ đổi drone, đơn hết bị chặn và phải nạp lại.
        maintenanceService.changeDrone(orderId, TECH_NEAR, new AcceptDroneOrderRequest(DRONE_A2));
        assertEquals(DRONE_A2, mission(orderId).getDroneUnitId());
        assertEquals("AWAITING_LOADING", mission(orderId).getStatus());
        assertEquals("RESERVED", drones.get(DRONE_A2).status());
        assertEquals("FAULT", drones.get(DRONE_A1).status(), "drone lỗi giữ nguyên trạng thái lỗi");
        assertEquals(Verdict.CLEARED, review(orderId));
        assertNull(order(orderId).getDeliveryBlockReason());
        assertNull(queryService.get(orderId, SENDER).block());
    }

    @Test
    void blockedOrderIsClosedAsNotCompletedAfterTheDeadlineAndRefunded() {
        bothTechniciansOnDuty();
        long orderId = acceptedWithDrone(DRONE_A1);
        setDroneStatus(DRONE_A1, "FAULT");
        setDroneStatus(DRONE_A2, "MAINTENANCE");
        openReports.add(new OpenReportDto(77L, LOCKER_A, null, DRONE_A1, "Motor rung", "IN_PROGRESS", "DRONE", false));
        assertEquals(Verdict.BLOCKED, review(orderId));
        emails.clear();

        // Chưa quá hạn 30 phút: giữ nguyên.
        assertEquals(0, dispatchSweeper.reviewBlocksAt(LocalDateTime.now().plusMinutes(29)));
        assertEquals("AWAITING_DISPATCH", order(orderId).getStatus());

        assertEquals(1, dispatchSweeper.reviewBlocksAt(LocalDateTime.now().plusMinutes(31)));

        LockerOrder failed = order(orderId);
        assertEquals("CANCELED", failed.getStatus());
        assertEquals("FAILED", failed.getDeliveryStage());
        assertEquals(DroneFailureReasons.DRONE_FAULT, failed.getFailureReason());
        assertEquals(77L, failed.getFailureTicketId());
        assertNull(failed.getDeliveryBlockReason());
        assertNotNull(failed.getFailedAt());
        assertEquals("FAILED", mission(orderId).getStatus());
        assertEquals("AWAITING_LOADING", mission(orderId).getFailedStage());
        assertEquals("AVAILABLE", boxStatus.get(DEST_BOX), "ô nhận được nhả");
        assertEquals("FAULT", drones.get(DRONE_A1).status());
        // Đã thu tiền ⇒ có yêu cầu hoàn tiền.
        assertEquals(List.of(orderId), refundRequests);
        assertEquals("REFUND_PENDING", failed.getPaymentStatus());

        // Người gửi nhận thư; người nhận chưa từng được báo có đơn (drone chưa phóng) nên không nhận.
        assertEquals(1, emails.size());
        GuestNotification.Request mail = emails.getFirst();
        assertEquals("gui@gmail.com", mail.email());
        assertTrue(mail.subject().contains("giao không hoàn tất"));
        assertTrue(mail.emailBody().contains("Drone gặp sự cố"));
        assertTrue(mail.emailBody().contains("phiếu sự cố #77"));
        assertTrue(mail.emailBody().contains("hoàn"));
        assertTrue(mail.emailBody().contains("mang kiện trả lại"));

        // Kiện đã rời tay người gửi ⇒ chờ trả; KTV đã lấy kiện xác nhận trả thì khép lại.
        DroneDeliveryOrderResponse view = queryService.get(orderId, SENDER);
        assertEquals(DroneFailureReasons.DRONE_FAULT, view.failure().reason());
        assertEquals(77L, view.failure().ticketId());
        assertTrue(view.parcelReturnPending());
        assertEquals("FLIGHT_TEAM", view.parcelHeldAt());
        assertTrue(queryService.operations(null, TECH_NEAR, false).stream().anyMatch(o -> o.orderId().equals(orderId)),
                "đơn chờ trả kiện vẫn nằm trong việc của KTV");
        assertEquals("DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                DroneHomePickupFlowPostgresTest.code(() -> parcelService.confirmReturn(orderId, TECH_FAR, false, null)));
        parcelService.confirmReturn(orderId, TECH_NEAR, false, "Trả tận nhà");
        assertFalse(queryService.get(orderId, SENDER).parcelReturnPending());
        assertEquals(List.of(orderId), queryService.byTicket(77L).stream().map(DroneDeliveryOrderResponse::orderId).toList());
    }

    // ------------------------------------------------------------------ thời tiết

    @Test
    void flightSuspensionBlocksPaidOrdersAndLiftsWhenFlightsResume() {
        bothTechniciansOnDuty();
        long paid = book().orderId();
        pay(paid);
        boxStatus.put(DEST_BOX, "AVAILABLE");
        long unpaid = book().orderId();

        settings.update(Map.of(OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, true), null);

        assertEquals(Verdict.BLOCKED, review(paid));
        assertEquals(DroneFailureReasons.WEATHER, order(paid).getDeliveryBlockReason());
        assertNull(order(paid).getDeliveryBlockTicketId());
        // Đơn chưa trả tiền chưa vào guồng: không đánh dấu.
        assertEquals(Verdict.UNCHANGED, review(unpaid));
        assertNull(order(unpaid).getDeliveryBlockReason());

        settings.update(Map.of(OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, false), null);
        assertEquals(Verdict.CLEARED, review(paid));
        assertNull(order(paid).getDeliveryBlockReason());
        assertEquals("AWAITING_DISPATCH", order(paid).getStatus());
    }

    @Test
    void noTechnicianIsSentToPickUpWhileTheOrderIsOnHold() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        assertEquals(TECH_NEAR, pickup(orderId).getTechnicianId());

        // Trời xấu: admin tạm dừng bay khi KTV đang được mời.
        settings.update(Map.of(OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, true), null);
        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals("DRONE_ORDER_BLOCKED",
                DroneHomePickupFlowPostgresTest.code(() -> pickupService.accept(orderId, TECH_NEAR)));

        // Lời mời hết hạn nhưng không mời người kế tiếp khi đơn còn bị chặn.
        pickupService.sweep(LocalDateTime.now().plusSeconds(181));
        assertEquals(DronePickup.SEARCHING, pickup(orderId).getStatus());

        // Bay lại: gỡ chặn rồi lượt quét mời người còn vị trí hiệu lực.
        settings.update(Map.of(OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, false), null);
        assertEquals(Verdict.CLEARED, review(orderId));
        technicianOnDuty(TECH_FAR, 10.7050, 106.7000);
        assertEquals(TECH_FAR, pickup(orderId).getTechnicianId());
    }

    @Test
    void simulatorNeverRevivesAnOrderThatWasClosedDuringTheFlight() {
        settings.update(Map.of(
                OrderSettingsCatalog.DRONE_DEMO_ENABLED, true,
                OrderSettingsCatalog.DRONE_DEMO_STAGE_DELAY_MS, 1000), null);
        bothTechniciansOnDuty();
        long orderId = acceptedWithDrone(DRONE_A1);
        jdbc.update("update order_schema.orders set fulfillment_mode = 'DEMO' where id = ?", orderId);
        maintenanceService.confirmLoading(orderId, TECH_NEAR, "load-1",
                new ConfirmDroneLoadingRequest(1200, null, true, true, true, null));
        maintenanceService.launch(orderId, TECH_NEAR, "launch-1");

        // Bộ giả lập đẩy được chặng khi đơn còn bay…
        jdbc.update("update order_schema.drone_missions set updated_at = updated_at - interval '5 seconds' "
                + "where order_id = ?", orderId);
        simulator.advanceScheduledMissions();
        assertEquals("DEPARTED", order(orderId).getDeliveryStage());

        // …nhưng nhiệm vụ nó nhìn thấy đã cũ so với đơn (đơn vừa bị đóng) thì phải bỏ qua.
        failureService.fail(orderId, TECH_NEAR, Actor.TECHNICIAN, DroneFailureReasons.FLIGHT_INCIDENT, "Mất tín hiệu", null);
        jdbc.update("update order_schema.drone_missions set status = 'DEPARTED', ended_at = null, "
                + "updated_at = updated_at - interval '5 seconds' where order_id = ?", orderId);
        simulator.advanceScheduledMissions();

        assertEquals("CANCELED", order(orderId).getStatus());
        assertEquals("FAILED", order(orderId).getDeliveryStage());
    }

    @Autowired
    private com.huynqb.laundrylocker.order.service.DroneDeliverySimulator simulator;

    @Test
    void technicianConfirmsABlockedOrderAsNotCompletedBeforeTheDeadline() {
        bothTechniciansOnDuty();
        long orderId = bookedAndPickedUpBy(TECH_NEAR);
        settings.update(Map.of(OrderSettingsCatalog.DRONE_FLIGHTS_SUSPENDED, true), null);
        assertEquals(Verdict.BLOCKED, review(orderId));

        // KTV khác không được đóng đơn của người đang giữ kiện.
        assertEquals("DRONE_MISSION_NOT_ASSIGNED_TO_USER",
                DroneHomePickupFlowPostgresTest.code(() -> failureService.fail(orderId, TECH_FAR, Actor.TECHNICIAN, null, null, null)));

        // Không chọn lý do ⇒ lấy lý do đơn đang bị chặn.
        failureService.fail(orderId, TECH_NEAR, Actor.TECHNICIAN, null, null, null);
        refundService.refundCanceledOrder(orderId, TECH_NEAR);

        LockerOrder failed = order(orderId);
        assertEquals("FAILED", failed.getDeliveryStage());
        assertEquals(DroneFailureReasons.WEATHER, failed.getFailureReason());
        assertEquals(1, failed.getCancelReason(), "mã số cũ cho client cũ: 1 = thời tiết");
        assertEquals("REFUND_PENDING", failed.getPaymentStatus());
        assertFalse(pickupService.busy(TECH_NEAR), "đơn đóng thì KTV không còn bị coi là đang cầm kiện");
        // Đóng rồi thì không đóng lại được.
        assertEquals("DRONE_ORDER_STATUS_INVALID",
                DroneHomePickupFlowPostgresTest.code(() -> failureService.fail(orderId, ADMIN, Actor.ADMIN, "OTHER", "x", null)));
    }

    // ------------------------------------------------------------------ tủ, bãi đáp

    @Test
    void sourceLockerFaultBeforeTheMissionSwitchesToAnotherNearbyLocker() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        pickupService.accept(orderId, TECH_NEAR);
        assertEquals(LOCKER_A, order(orderId).getSourceLockerId());

        // Tủ A bị phiếu chặn cả tủ ⇒ đổi sang tủ C, đơn không bị chặn.
        lockerStatus.put(LOCKER_A, "MAINTENANCE");
        openReports.add(new OpenReportDto(80L, LOCKER_A, null, null, "Mất điện", "OPEN", "LOCKER", true));

        assertEquals(Verdict.UNCHANGED, review(orderId));
        LockerOrder switched = order(orderId);
        assertEquals(LOCKER_C, switched.getSourceLockerId());
        assertNull(switched.getDeliveryBlockReason());
        assertTrue(switched.getPickupDistanceMeters() > 1000);
        assertTrue(journeyNotes(orderId).stream().anyMatch(note -> note.contains("đổi sang tủ gửi")));

        // Tủ C cũng hỏng bãi đáp ⇒ hết phương án: bị chặn và nối với phiếu bãi đáp.
        landingPadStatus.put(LOCKER_C, "BLOCKED");
        openReports.add(new OpenReportDto(81L, LOCKER_C, null, null, "Bãi đáp drone bị che", "OPEN", "LANDING_PAD", false));
        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals(DroneFailureReasons.LANDING_PAD_FAULT, order(orderId).getDeliveryBlockReason());
        assertEquals(81L, order(orderId).getDeliveryBlockTicketId());
    }

    @Test
    void destinationProblemsBlockBecauseTheReceiverLockerCannotBeSwapped() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);

        landingPadStatus.put(LOCKER_B, "BLOCKED");
        openReports.add(new OpenReportDto(90L, LOCKER_B, null, null, "Bãi đáp drone hỏng đèn", "OPEN", "LANDING_PAD", false));
        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals(DroneFailureReasons.LANDING_PAD_FAULT, order(orderId).getDeliveryBlockReason());
        assertEquals(90L, order(orderId).getDeliveryBlockTicketId());

        // Bãi đáp sửa xong nhưng cả tủ nhận vào bảo trì: lý do và phiếu đổi theo.
        landingPadStatus.put(LOCKER_B, "OK");
        openReports.clear();
        lockerStatus.put(LOCKER_B, "MAINTENANCE");
        openReports.add(new OpenReportDto(91L, LOCKER_B, null, null, "Màn hình hỏng", "OPEN", "LOCKER", true));
        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals(DroneFailureReasons.DESTINATION_LOCKER_FAULT, order(orderId).getDeliveryBlockReason());
        assertEquals(91L, order(orderId).getDeliveryBlockTicketId());

        // Phiếu đóng, tủ hoạt động lại nhưng ô nhận đã giữ bị báo hỏng.
        lockerStatus.put(LOCKER_B, "ACTIVE");
        openReports.clear();
        boxStatus.put(DEST_BOX, "FAULT");
        openReports.add(new OpenReportDto(92L, LOCKER_B, DEST_BOX, null, "Ô kẹt cửa", "OPEN", "BOX", false));
        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals(92L, order(orderId).getDeliveryBlockTicketId());

        boxStatus.put(DEST_BOX, "RESERVED");
        openReports.clear();
        assertEquals(Verdict.CLEARED, review(orderId));
    }

    @Test
    void lockerServiceOutageNeitherBlocksNorUnblocksAnOrder() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        org.mockito.Mockito.when(lockerDroneClient.getLockerLayout(org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new IllegalStateException("locker-service down"));

        assertEquals(Verdict.UNCHANGED, review(orderId));
        assertNull(order(orderId).getDeliveryBlockReason());
    }

    // ------------------------------------------------------------------ lấy hàng không thành, không có KTV

    @Test
    void failedPickupEndsTheOrderRefundsAndLeavesTheParcelWithTheSender() {
        bothTechniciansOnDuty();
        long orderId = book().orderId();
        pay(orderId);
        pickupService.accept(orderId, TECH_NEAR);
        emails.clear();

        assertEquals("DRONE_FAILURE_REASON_INVALID",
                DroneHomePickupFlowPostgresTest.code(() -> pickupService.failPickup(orderId, TECH_NEAR, "WEATHER", null)));
        assertEquals("DRONE_PICKUP_NOT_ASSIGNED_TO_USER",
                DroneHomePickupFlowPostgresTest.code(() -> pickupService.failPickup(orderId, TECH_FAR, "PICKUP_CUSTOMER_ABSENT", null)));

        pickupService.failPickup(orderId, TECH_NEAR, "PICKUP_CUSTOMER_ABSENT", "Gọi 3 lần không nghe máy");
        refundService.refundCanceledOrder(orderId, TECH_NEAR);

        LockerOrder failed = order(orderId);
        assertEquals("CANCELED", failed.getStatus());
        assertEquals("FAILED", failed.getDeliveryStage());
        assertEquals(DroneFailureReasons.PICKUP_CUSTOMER_ABSENT, failed.getFailureReason());
        assertEquals("REFUND_PENDING", failed.getPaymentStatus());
        DronePickup pickup = pickup(orderId);
        assertEquals(DronePickup.FAILED, pickup.getStatus());
        assertEquals("Gọi 3 lần không nghe máy", pickup.getFailureNote());
        assertFalse(pickupService.busy(TECH_NEAR));
        assertEquals("AVAILABLE", boxStatus.get(DEST_BOX));
        // Kiện chưa rời tay người gửi nên không có gì để trả.
        assertFalse(queryService.get(orderId, SENDER).parcelReturnPending());
        assertTrue(emails.getFirst().emailBody().contains("chưa được lấy"));
    }

    @Test
    void paidOrderNobodyPicksUpEndsAsNoTechnicianAvailable() {
        // Không KTV nào trong ca.
        long orderId = book().orderId();
        pay(orderId);
        assertEquals(DronePickup.SEARCHING, pickup(orderId).getStatus());
        jdbc.update("update order_schema.orders set paid_at = paid_at - interval '121 minutes' where id = ?", orderId);

        timeoutSweeper.sweepScheduled();

        LockerOrder failed = order(orderId);
        assertEquals("FAILED", failed.getDeliveryStage());
        assertEquals(DroneFailureReasons.NO_TECHNICIAN_AVAILABLE, failed.getFailureReason());
        assertEquals(DronePickup.CANCELED, pickup(orderId).getStatus());
        assertEquals("REFUND_PENDING", failed.getPaymentStatus());
        assertEquals(List.of(orderId), refundRequests);
    }

    @Test
    void parcelPickedUpButNeverLaunchedIsFlaggedThenClosed() {
        bothTechniciansOnDuty();
        long orderId = bookedAndPickedUpBy(TECH_NEAR);
        jdbc.update("update order_schema.drone_pickups set picked_up_at = picked_up_at - interval '121 minutes' "
                + "where order_id = ?", orderId);

        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals(DroneFailureReasons.LAUNCH_OVERDUE, order(orderId).getDeliveryBlockReason());
    }

    // ------------------------------------------------------------------ sự cố lúc bay

    @Test
    void inFlightFaultIsFlaggedButOnlyAPersonClosesTheOrder() {
        bothTechniciansOnDuty();
        long orderId = acceptedWithDrone(DRONE_A1);
        maintenanceService.confirmLoading(orderId, TECH_NEAR, "load-1",
                new ConfirmDroneLoadingRequest(1200, null, true, true, true, null));
        maintenanceService.launch(orderId, TECH_NEAR, "launch-1");
        emails.clear();

        setDroneStatus(DRONE_A1, "FAULT");
        assertEquals(Verdict.BLOCKED, review(orderId));
        assertEquals(DroneFailureReasons.FLIGHT_INCIDENT, order(orderId).getDeliveryBlockReason());
        // Drone đang bay: quá hạn bao lâu hệ thống cũng không tự đóng.
        assertEquals(0, dispatchSweeper.reviewBlocksAt(LocalDateTime.now().plusHours(5)));
        assertNull(queryService.get(orderId, SENDER).block().autoCloseAt());

        failureService.fail(orderId, ADMIN, Actor.ADMIN, DroneFailureReasons.FLIGHT_INCIDENT, "Hạ cánh khẩn", null);
        refundService.refundCanceledOrder(orderId, ADMIN);

        assertEquals("FAILED", order(orderId).getDeliveryStage());
        assertEquals("LAUNCHING", mission(orderId).getFailedStage());
        // Người nhận đã được báo có đơn lúc phóng nên cũng được báo đơn không hoàn tất.
        assertTrue(emails.stream().anyMatch(mail -> "gui@gmail.com".equals(mail.email())));
        assertTrue(emails.stream().anyMatch(mail -> "nhan@gmail.com".equals(mail.email())
                && mail.subject().contains("không hoàn tất")));
    }
}
