package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.LockerOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.huynqb.laundrylocker.order.repository.OrderSpecifications.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Chạy thật Specification tìm kiếm đơn trên H2 (tên field, OR/IN, phân trang).
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class OrderSpecificationsJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private LockerOrderRepository repository;

    private LockerOrder send;
    private LockerOrder rental;
    private LockerOrder drone;

    @BeforeEach
    void seed() {
        send = persist("ORD-20260901-AAA", "SEND", "STORING", "PAID", 44L, 7L, null, LocalDateTime.of(2026, 9, 1, 3, 0));
        send.setReceiverPhone("0907654321");
        send.setReceiverName("Trần Bình");
        rental = persist("ORD-20260905-BBB", "RENTAL", "COMPLETED", "PAID", 45L, 8L, null, LocalDateTime.of(2026, 9, 5, 3, 0));
        drone = persist("ORD-20260910-CCC", "DRONE_DELIVERY", "AWAITING_DISPATCH", "UNPAID", 44L, 9L, 9L,
                LocalDateTime.of(2026, 9, 10, 3, 0));
        em.flush();
    }

    @Test
    void filtersCombineStatusTypePaymentUserAndDateRange() {
        assertEquals(List.of(send.getId()), ids(all(statusIn(List.of("storing")), typeIn(List.of("SEND", "RENTAL")))));
        assertEquals(2, repository.count(all(paymentStatusIn(List.of("PAID")))));
        assertEquals(2, repository.count(all(user(44L))));
        assertEquals(List.of(rental.getId()), ids(all(
                createdBetween(LocalDateTime.of(2026, 9, 2, 0, 0), LocalDateTime.of(2026, 9, 10, 0, 0)))));
    }

    @Test
    void createdOrCompletedBetweenAlsoCatchesOlderOrdersCompletedInRange() {
        em.getEntityManager()
                .createQuery("update LockerOrder o set o.completedAt = :at where o.id = :id")
                .setParameter("at", LocalDateTime.of(2026, 9, 11, 0, 0))
                .setParameter("id", send.getId())
                .executeUpdate();

        assertEquals(List.of(send.getId(), rental.getId()), ids(all(
                createdOrCompletedBetween(LocalDateTime.of(2026, 9, 5, 0, 0), LocalDateTime.of(2026, 9, 10, 0, 0))
                        .or(createdOrCompletedBetween(LocalDateTime.of(2026, 9, 11, 0, 0), LocalDateTime.of(2026, 9, 12, 0, 0))))));
        assertEquals(3, repository.count(all(
                createdOrCompletedBetween(LocalDateTime.of(2026, 9, 5, 0, 0), LocalDateTime.of(2026, 9, 12, 0, 0)))));
    }

    @Test
    void lockerAndStoreMatchDestinationLockerToo() {
        assertEquals(List.of(drone.getId()), ids(all(lockerIn(List.of(9L)))));
        assertEquals(2, repository.count(all(store(3L, List.of(7L, 9L)))));
    }

    @Test
    void keywordMatchesCodeReceiverPhoneNameIdAndMatchedCustomer() {
        assertEquals(List.of(rental.getId()), ids(all(keyword("bbb", null))));
        assertEquals(List.of(send.getId()), ids(all(keyword("0907654", null))));
        assertEquals(List.of(send.getId()), ids(all(keyword("trần", null))));
        // Số thuần: khớp đúng id đơn (ngoài ra vẫn khớp chuỗi con trong mã đơn/SĐT).
        org.junit.jupiter.api.Assertions.assertTrue(
                ids(all(keyword(String.valueOf(drone.getId() + 1000), null))).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                ids(all(keyword(String.valueOf(drone.getId()), null))).contains(drone.getId()));
        assertEquals(2, repository.count(all(keyword("0900000000", 44L))));
    }

    @Test
    void pagesSortedByCreatedAtDescending() {
        Page<LockerOrder> page = repository.findAll(all(), PageRequest.of(0, 2,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(List.of(drone.getId(), rental.getId()), page.getContent().stream().map(LockerOrder::getId).toList());
    }

    private List<Long> ids(org.springframework.data.jpa.domain.Specification<LockerOrder> spec) {
        return repository.findAll(spec, Sort.by("id")).stream().map(LockerOrder::getId).toList();
    }

    private LockerOrder persist(String code, String type, String status, String paymentStatus, Long userId,
                                Long lockerId, Long destinationLockerId, LocalDateTime createdAt) {
        LockerOrder order = new LockerOrder();
        order.setOrderCode(code);
        order.setType(type);
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        order.setUserId(userId);
        order.setLockerId(lockerId);
        order.setDestinationLockerId(destinationLockerId);
        order.setTotalPrice(BigDecimal.valueOf(15000));
        em.persist(order);
        em.flush();
        em.getEntityManager()
                .createQuery("update LockerOrder o set o.createdAt = :at where o.id = :id")
                .setParameter("at", createdAt)
                .setParameter("id", order.getId())
                .executeUpdate();
        return order;
    }
}
