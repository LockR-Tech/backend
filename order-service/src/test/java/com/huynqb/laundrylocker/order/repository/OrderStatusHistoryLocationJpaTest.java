package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.service.OrderHistoryLocator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/// `save` của lịch sử đơn đi qua fragment gắn toạ độ (không phải `SimpleJpaRepository.save`).
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:history-location;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;INIT=CREATE SCHEMA IF NOT EXISTS order_schema",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class OrderStatusHistoryLocationJpaTest {

    @Autowired private OrderStatusHistoryRepository histories;
    @Autowired private TestEntityManager em;
    @MockitoBean private OrderHistoryLocator locator;

    @Test
    void newRowsAreLocatedOnceAndUpdatesAreNotRelocated() {
        doAnswer(inv -> {
            OrderStatusHistory h = inv.getArgument(0);
            h.setLatitude(10.78);
            h.setLongitude(106.66);
            h.setLocationSource(OrderHistoryLocator.DESTINATION_LOCKER);
            return null;
        }).when(locator).fill(any());

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(9L);
        history.setOldStatus("ARRIVED");
        history.setNewStatus("READY_FOR_PICKUP");
        Long id = histories.save(history).getId();
        em.flush();
        em.clear();

        OrderStatusHistory saved = histories.findById(id).orElseThrow();
        assertEquals(10.78, saved.getLatitude());
        assertEquals(106.66, saved.getLongitude());
        assertEquals(OrderHistoryLocator.DESTINATION_LOCKER, saved.getLocationSource());

        saved.setNote("sửa ghi chú");
        histories.save(saved);
        em.flush();
        verify(locator, times(1)).fill(any());
        assertEquals("sửa ghi chú", histories.findById(id).orElseThrow().getNote());
    }
}
