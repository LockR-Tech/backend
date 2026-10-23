package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.dto.OrderMilestone;
import com.huynqb.laundrylocker.order.dto.OrderParcelView;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderDetailViewsTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 12, 8, 0);

    @Test
    void parcelIsNullWhenNothingWasDeclared() {
        LockerOrder order = new LockerOrder();
        order.setDescription("   ");

        assertNull(OrderDetailViews.parcel(order));
    }

    @Test
    void parcelKeepsWeightUnitOnlyAlongsideAWeight() {
        LockerOrder order = new LockerOrder();
        order.setActualWeight(new BigDecimal("1.50"));
        order.setDescription("  Áo khoác  ");

        OrderParcelView parcel = OrderDetailViews.parcel(order);

        assertEquals("Áo khoác", parcel.description());
        assertEquals("kg", parcel.weightUnit());
        assertNull(parcel.weightGrams());
    }

    @Test
    void milestonesAreOldestFirstAndUseFirstRealStatusChange() {
        LockerOrder order = new LockerOrder();
        order.setType("SEND");
        order.setCreatedAt(T0);
        order.setPaidAt(T0.plusMinutes(1));
        order.setCompletedAt(T0.plusHours(5));
        List<OrderStatusHistory> history = List.of(
                history(null, "INITIALIZED", T0),
                // Dòng ghi chú (cũ = mới) không phải lúc hàng vào ô.
                history("INITIALIZED", "INITIALIZED", T0.plusMinutes(2)),
                history("INITIALIZED", "STORING", T0.plusMinutes(10)),
                history("STORING", "STORING", T0.plusMinutes(30)),
                history("STORING", "COMPLETED", T0.plusHours(5)));

        List<OrderMilestone> milestones = OrderDetailViews.milestones(order, history);

        assertEquals(List.of("CREATED", "PAID", "STORED", "COMPLETED"),
                milestones.stream().map(OrderMilestone::code).toList());
        assertEquals(T0.plusMinutes(10), milestones.get(2).occurredAt());
        assertEquals("Đã lấy hàng, đơn hoàn tất", milestones.get(3).label());
    }

    @Test
    void blockedDroneOrderLabelsTheReasonAndCanceledComesFromHistory() {
        LockerOrder order = new LockerOrder();
        order.setType("DRONE_DELIVERY");
        order.setCreatedAt(T0);
        order.setDeliveryBlockReason("DRONE_FAULT");
        order.setDeliveryBlockedAt(T0.plusMinutes(20));
        List<OrderStatusHistory> history = List.of(history("AWAITING_DISPATCH", "CANCELED", T0.plusMinutes(40)));

        List<OrderMilestone> milestones = OrderDetailViews.milestones(order, history);

        assertEquals(List.of("CREATED", "BLOCKED", "CANCELED"),
                milestones.stream().map(OrderMilestone::code).toList());
        assertEquals("Tạm dừng: Drone gặp sự cố", milestones.get(1).label());
    }

    /// Ô báo sự cố → KTV chuyển ô → xử lý xong → ô báo lại: mỗi lần là một mốc.
    @Test
    void boxIncidentNotesBecomeMilestones() {
        LockerOrder order = new LockerOrder();
        order.setType("RENTAL");
        order.setCreatedAt(T0);
        List<OrderStatusHistory> history = List.of(
                history("INITIALIZED", "STORING", T0.plusMinutes(3)),
                note("STORING", "Customer reported a fault on the assigned box", T0.plusMinutes(5)),
                note("STORING", OrderDetailViews.NOTE_BOX_INCIDENT_OPENED, T0.plusMinutes(5)),
                note("STORING", "KTV điều chuyển hàng sang ô #3 do sự cố ô cũ", T0.plusMinutes(20)),
                note("STORING", "KTV điều chuyển hàng sang ô #3. Đã xử lý sự cố ô: bỏ phí quá giờ", T0.plusMinutes(20)),
                note("STORING", OrderDetailViews.NOTE_BOX_INCIDENT_OPENED, T0.plusMinutes(40)));

        List<OrderMilestone> milestones = OrderDetailViews.milestones(order, history);

        assertEquals(List.of("CREATED", "STORED", "BOX_INCIDENT_OPENED", "BOX_RELOCATED", "BOX_INCIDENT_RESOLVED",
                        "BOX_INCIDENT_OPENED"),
                milestones.stream().map(OrderMilestone::code).toList());
        assertEquals("KTV chuyển đồ sang ô #3", milestones.get(3).label());
    }

    @Test
    void withoutHistoryOnlyTimestampColumnsAreUsed() {
        LockerOrder order = new LockerOrder();
        order.setCreatedAt(T0);
        order.setDelegatedAt(T0.plusHours(1));

        assertEquals(List.of("CREATED", "DELEGATED"),
                OrderDetailViews.milestones(order, null).stream().map(OrderMilestone::code).toList());
    }

    private static OrderStatusHistory note(String status, String note, LocalDateTime at) {
        OrderStatusHistory h = history(status, status, at);
        h.setNote(note);
        return h;
    }

    private static OrderStatusHistory history(String from, String to, LocalDateTime at) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOldStatus(from);
        h.setNewStatus(to);
        h.setCreatedAt(at);
        return h;
    }
}
