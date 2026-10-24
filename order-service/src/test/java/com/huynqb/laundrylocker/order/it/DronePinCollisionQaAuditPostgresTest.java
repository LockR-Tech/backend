package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QA audit QA-06 (Postgres thật): PIN mở ô 6 số sinh ngẫu nhiên toàn hệ thống, không kiểm trùng,
 * không gắn tủ; màn hình tủ tra bằng findByPinCode (mong đúng 1 kết quả). Test khẳng định hành vi
 * mong đợi — FAIL nghĩa là lỗi đã tái hiện. Chỉ chạy khi -DqaAudit=true.
 */
@Tag("qa-audit")
@EnabledIfSystemProperty(named = "qaAudit", matches = "true")
class DronePinCollisionQaAuditPostgresTest extends OrderServicePostgresTestBase {

    @Autowired
    private LockerOrderRepository orderRepository;
    @Autowired
    private OrderService orderService;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAllById(created);
    }

    @Test
    @DisplayName("QA-06 Hai đơn drone đang chờ nhận ở hai tủ khác nhau trùng PIN: người nhận vẫn phải mở được ô")
    void twoReadyDroneOrdersWithTheSamePinMustStillBeOpenable() {
        LockerOrder atLockerA = readyDroneOrder(3L, 9101L, "482913");
        LockerOrder atLockerB = readyDroneOrder(5L, 9201L, "482913");

        // Kiosk tủ B gọi /internal/orders/by-access?code=482913 qua iot-service.
        var found = assertDoesNotThrow(() -> orderService.getByAccess("482913"),
                "tra PIN trùng ném lỗi ⇒ cả hai người nhận đều không mở được ô");
        assertEquals(atLockerB.getId(), found.id(), "PIN trùng trả về đơn của tủ khác");
        assertEquals(atLockerA.getLockerId(), 3L);
    }

    private LockerOrder readyDroneOrder(Long lockerId, Long boxId, String pin) {
        LockerOrder order = new LockerOrder();
        order.setOrderCode("ORD-QA-" + System.nanoTime());
        order.setUserId(44L);
        order.setType("DRONE_DELIVERY");
        order.setServiceCategory("DRONE_DELIVERY");
        order.setStatus("STORING");
        order.setDeliveryStage("READY_FOR_PICKUP");
        order.setPaymentStatus("PAID");
        order.setTotalPrice(new BigDecimal("20000"));
        order.setLockerId(lockerId);
        order.setDestinationLockerId(lockerId);
        order.setReservedBoxId(boxId);
        order.setSendBoxId(boxId);
        order.setPinCode(pin);
        order.setPinCodeIssuedAt(LocalDateTime.now());
        order.setPickupDeadline(LocalDateTime.now().plusHours(24));
        LockerOrder saved = orderRepository.saveAndFlush(order);
        created.add(saved.getId());
        return saved;
    }
}
