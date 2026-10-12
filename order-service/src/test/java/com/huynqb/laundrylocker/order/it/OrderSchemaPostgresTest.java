package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Schema thật: mọi migration chạy được trên Postgres, entity khớp cột (Hibernate validate lúc
/// khởi động context), và các truy vấn suy từ tên hàm của luồng drone chạy được.
class OrderSchemaPostgresTest extends OrderServicePostgresTestBase {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LockerOrderRepository orderRepository;
    @Autowired
    private DroneMissionRepository missionRepository;

    @Test
    void everyMigrationIsAppliedOnRealPostgres() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from order_schema.flyway_schema_history where success = false", Integer.class);
        String latest = jdbc.queryForObject(
                "select max(cast(version as integer)) from order_schema.flyway_schema_history "
                        + "where version is not null",
                String.class);

        assertEquals(0, failed);
        assertNotNull(latest);
        assertTrue(Integer.parseInt(latest) >= 22, "expected at least V22, got V" + latest);
    }

    @Test
    void droneOrderRoundTripsThroughRealColumnsAndDerivedQueries() {
        LockerOrder order = new LockerOrder();
        order.setOrderCode("ORD-IT-" + System.nanoTime());
        order.setUserId(4401L);
        order.setType("DRONE_DELIVERY");
        order.setServiceCategory("DRONE_DELIVERY");
        order.setStatus("CANCELED");
        order.setDeliveryStage("CANCELED");
        order.setPaymentStatus("PAID");
        order.setTotalPrice(new BigDecimal("15000"));
        order.setParcelWeightGrams(1200);
        order.setParcelCategory("FOOD");
        order.setParcelFragile(true);
        order.setParcelDroppedAt(LocalDateTime.now());
        order = orderRepository.saveAndFlush(order);

        DroneMission mission = new DroneMission();
        mission.setOrderId(order.getId());
        mission.setDestinationLockerId(5L);
        mission.setStatus("CANCELED");
        mission.setEndedAt(LocalDateTime.now());
        mission.setEndReason(2);
        missionRepository.saveAndFlush(mission);

        List<LockerOrder> waiting = orderRepository
                .findByTypeAndStatusAndParcelDroppedAtIsNotNullAndParcelReturnedAtIsNull("DRONE_DELIVERY", "CANCELED");
        Long id = order.getId();
        assertTrue(waiting.stream().anyMatch(o -> o.getId().equals(id)));
        assertEquals(0, orderRepository.countByUserIdAndTypeAndStatus(4401L, "DRONE_DELIVERY", "AWAITING_DISPATCH"));
        assertEquals("CANCELED", missionRepository.findByOrderId(id).orElseThrow().getStatus());

        missionRepository.deleteAll(missionRepository.findByOrderIdIn(List.of(id)));
        orderRepository.deleteById(id);
    }
}
