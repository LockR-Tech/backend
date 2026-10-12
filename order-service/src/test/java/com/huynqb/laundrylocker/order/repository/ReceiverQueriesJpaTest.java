package com.huynqb.laundrylocker.order.repository;

import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.DroneParcelIncident;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderRating;
import com.huynqb.laundrylocker.order.service.PhoneNumbers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Truy vấn phía người nhận chạy trên H2 (chế độ PostgreSQL): "Hàng đến", "Được ủy quyền", đánh giá
/// theo cửa hàng và danh sách sự cố của KTV drone.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:receiver-queries;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;INIT=CREATE SCHEMA IF NOT EXISTS order_schema",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class ReceiverQueriesJpaTest {

    private static final List<String> ACTIVE = List.of("INITIALIZED", "AWAITING_DISPATCH", "STORING", "RETURNED");

    @Autowired private TestEntityManager em;
    @Autowired private LockerOrderRepository orders;
    @Autowired private OrderRatingRepository ratings;
    @Autowired private DroneParcelIncidentRepository incidents;

    @Test
    void incomingMatchesReceiverAccountOrAnyWritingOfTheAccountPhone() {
        LockerOrder byAccount = order("ORD-A", 10L, "STORING", 7L);
        byAccount.setReceiverUserId(55L);
        LockerOrder byLegacyReceiverId = order("ORD-B", 11L, "COMPLETED", 7L);
        byLegacyReceiverId.setReceiverId(55L);
        LockerOrder byPhoneWithSpaces = order("ORD-C", 12L, "STORING", 7L);
        byPhoneWithSpaces.setReceiverPhone("090 123 4567");
        LockerOrder byInternationalPhone = order("ORD-D", 13L, "INITIALIZED", 7L);
        byInternationalPhone.setReceiverPhone("+84901234567");
        LockerOrder ownOrder = order("ORD-E", 55L, "STORING", 7L);
        ownOrder.setReceiverPhone("0901234567");
        LockerOrder someoneElse = order("ORD-F", 14L, "STORING", 7L);
        someoneElse.setReceiverPhone("0907654321");
        em.flush();

        Set<String> phones = PhoneNumbers.variants("0901234567");
        List<String> all = codes(orders.findIncoming(55L, phones, true, ACTIVE, PageRequest.of(0, 50)));
        List<String> active = codes(orders.findIncoming(55L, phones, false, ACTIVE, PageRequest.of(0, 50)));

        assertEquals(Set.of("ORD-A", "ORD-B", "ORD-C", "ORD-D"), Set.copyOf(all));
        assertEquals(Set.of("ORD-A", "ORD-C", "ORD-D"), Set.copyOf(active));
        assertEquals(2, orders.findIncoming(55L, phones, true, ACTIVE, PageRequest.of(0, 2)).size());
    }

    @Test
    void delegatedToMatchesDelegatePhoneOnly() {
        LockerOrder delegated = order("ORD-G", 10L, "STORING", 7L);
        delegated.setReceiverPhone("84 901 234 567");
        delegated.setDelegatePhone("84 901 234 567");
        delegated.setDelegatedAt(LocalDateTime.now());
        LockerOrder receiverOnly = order("ORD-H", 11L, "STORING", 7L);
        receiverOnly.setReceiverPhone("0901234567");
        em.flush();

        List<LockerOrder> result = orders.findDelegatedTo(
                PhoneNumbers.variants("+84901234567"), false, List.of("STORING", "RETURNED"), PageRequest.of(0, 50));

        assertEquals(List.of("ORD-G"), codes(result));
    }

    @Test
    void storeRatingsIncludeOrdersWithoutStoreIdThroughTheirLocker() {
        LockerOrder storeOrder = order("ORD-S", 10L, "COMPLETED", 99L);
        storeOrder.setStoreId(3L);
        LockerOrder sendAtStoreLocker = order("ORD-T", 11L, "COMPLETED", 7L);
        LockerOrder otherStore = order("ORD-U", 12L, "COMPLETED", 8L);
        LockerOrder explicitOtherStore = order("ORD-V", 13L, "COMPLETED", 7L);
        explicitOtherStore.setStoreId(4L);
        em.flush();
        rating(storeOrder, 10L, 5);
        rating(sendAtStoreLocker, 11L, 4);
        rating(otherStore, 12L, 1);
        rating(explicitOtherStore, 13L, 2);
        em.flush();

        List<OrderRating> result = ratings.findForStore(3L, List.of(7L));

        assertEquals(Set.of(5, 4), Set.copyOf(result.stream().map(OrderRating::getRating).toList()));
    }

    @Test
    void droneTechnicianSeesIncidentsReportedAssignedOrOnTheirMission() {
        incident("DPI-1", mission(301L, 99L), 1L, null);  // admin báo trên chuyến của KTV 99
        incident("DPI-2", mission(302L, 77L), 99L, null); // KTV 99 tự báo
        incident("DPI-3", mission(303L, 77L), 77L, 99L);  // KTV 99 được giao thu hồi
        incident("DPI-4", mission(304L, 77L), 77L, 44L);  // không liên quan
        em.flush();

        List<String> visible = incidents.findVisibleToDroneTechnician(99L).stream()
                .map(DroneParcelIncident::getIncidentCode)
                .toList();

        assertEquals(Set.of("DPI-1", "DPI-2", "DPI-3"), Set.copyOf(visible));
    }

    private LockerOrder order(String code, Long userId, String status, Long lockerId) {
        LockerOrder order = new LockerOrder();
        order.setOrderCode(code);
        order.setUserId(userId);
        order.setType("SEND");
        order.setStatus(status);
        order.setLockerId(lockerId);
        em.persist(order);
        return order;
    }

    private void rating(LockerOrder order, Long userId, int stars) {
        OrderRating rating = new OrderRating();
        rating.setOrderId(order.getId());
        rating.setUserId(userId);
        rating.setRating(stars);
        em.persist(rating);
    }

    private DroneMission mission(Long orderId, Long technicianId) {
        DroneMission mission = new DroneMission();
        mission.setOrderId(orderId);
        mission.setDestinationLockerId(7L);
        mission.setStatus("DROP_REPORTED");
        mission.setAssignedByUserId(technicianId);
        em.persist(mission);
        em.flush();
        return mission;
    }

    private void incident(String code, DroneMission mission, Long reportedBy, Long recoveryAssignee) {
        DroneParcelIncident incident = new DroneParcelIncident();
        incident.setIncidentCode(code);
        incident.setOrderId(mission.getOrderId());
        incident.setMissionId(mission.getId());
        incident.setDroneUnitId(9L);
        incident.setDroneCode("DRONE-09");
        incident.setReportedByUserId(reportedBy);
        incident.setRecoveryAssignedToUserId(recoveryAssignee);
        incident.setIdempotencyKey("key-" + code);
        incident.setReason("Rơi kiện");
        incident.setStatus("INVESTIGATING");
        incident.setParcelStatus("RECOVERY_PENDING");
        incident.setRecoveryStatus("ASSIGNED");
        incident.setInspectionStatus("ASSIGNED");
        incident.setRedeliveryStatus("NOT_REQUIRED");
        incident.setCompensationStatus("NOT_REQUIRED");
        incident.setReturnFlightStatus("RETURN_REQUESTED");
        incident.setGpsSource("TELEMETRY");
        incident.setCameraStatus("UNAVAILABLE");
        em.persist(incident);
    }

    private static List<String> codes(List<LockerOrder> result) {
        return result.stream().map(LockerOrder::getOrderCode).toList();
    }
}
