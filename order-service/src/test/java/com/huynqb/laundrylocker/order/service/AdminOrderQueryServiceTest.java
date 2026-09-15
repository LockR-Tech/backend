package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.dto.admin.AdminOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.dto.admin.StoreInfo;
import com.huynqb.laundrylocker.order.model.DroneMission;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.DroneMissionRepository;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderDetailRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderQueryServiceTest {

    @Mock private LockerOrderRepository orderRepository;
    @Mock private OrderDetailRepository detailRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private DroneMissionRepository missionRepository;
    @Mock private AdminReferenceResolver resolver;
    @Mock private QrTokenService qrTokenService;

    private AdminOrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrderQueryService(
                orderRepository, detailRepository, historyRepository, missionRepository, resolver, qrTokenService);
        service.setTime(new BusinessTime(ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE,
                Clock.fixed(Instant.parse("2026-09-15T05:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchEnrichesWholePageWithOneBatchCallPerSource() {
        LockerOrder send = order(1L, "SEND", 44L);
        send.setReceiverUserId(45L);
        send.setReceiverName("Trần Bình");
        send.setReceiverPhone("0907654321");
        send.setSendBoxId(701L);
        send.setExtraFee(BigDecimal.valueOf(2000));
        send.setTotalPrice(BigDecimal.valueOf(17000));
        send.setPickupDeadline(LocalDateTime.of(2026, 9, 15, 4, 0));
        LockerOrder rental = order(2L, "RENTAL", 46L);
        rental.setSendBoxId(702L);

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(send, rental), PageRequest.of(0, 20), 2));
        when(detailRepository.findByOrderIdIn(List.of(1L, 2L))).thenReturn(List.of());
        when(resolver.users(anyCollection())).thenReturn(lookup(Map.of(
                44L, user(44L, "Nguyễn An"), 45L, user(45L, "Trần Bình"), 46L, user(46L, "Lê Chi"))));
        when(resolver.lockers(anyCollection())).thenReturn(lookup(Map.of(7L, locker(7L, 3L))));
        when(resolver.boxes(anyCollection())).thenReturn(lookup(Map.of(
                701L, new BoxInfo(701L, 7L, 12, "M", "STANDARD", "OCCUPIED", true))));
        when(resolver.stores(anyCollection())).thenReturn(lookup(Map.of(
                3L, new StoreInfo(3L, "Chi nhánh Q1", "028", "1 Lê Lợi", null, null, true, "ACTIVE"))));
        when(resolver.paymentSummaries(anyCollection())).thenReturn(lookup(Map.of(1L, new OrderPaymentSummary(
                1L, 1, 90L, "WALLET", "COMPLETED", BigDecimal.valueOf(15000), LocalDateTime.of(2026, 9, 14, 1, 0),
                BigDecimal.valueOf(15000), "WALLET", LocalDateTime.of(2026, 9, 14, 1, 0), BigDecimal.ZERO))));

        PageResponse<AdminOrderResponse> page = service.search(criteria(0, 20, null));

        assertEquals(2, page.totalElements());
        AdminOrderResponse first = page.content().get(0);
        assertEquals("Nguyễn An", first.customer().fullName());
        assertEquals("Trần Bình", first.receiver().name());
        assertEquals("Trần Bình", first.receiver().accountFullName());
        assertEquals("LK-7", first.locker().code());
        assertEquals("Chi nhánh Q1", first.store().name());
        assertEquals(12, first.sendBoxNumber());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(first.fees().overtimeFee()));
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(first.fees().basePrice()));
        assertEquals("WALLET", first.payment().lastPaidMethod());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(first.payment().outstandingAmount()));
        assertTrue(first.overtime());
        assertNull(first.timeline());
        assertNull(first.drone());

        AdminOrderResponse second = page.content().get(1);
        assertEquals(0, second.payment().paymentCount());
        assertNull(second.sendBoxNumber());

        verify(resolver, times(1)).users(anyCollection());
        verify(resolver, times(1)).paymentSummaries(anyCollection());
        verify(missionRepository, never()).findByOrderIdIn(anyCollection());
        verify(historyRepository, never()).findByOrderIdOrderByCreatedAtAsc(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchStillReturnsOrdersWhenLookupsAreUnavailable() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order(1L, "SEND", 44L)), PageRequest.of(0, 20), 1));
        when(resolver.users(anyCollection())).thenReturn(unavailable());
        when(resolver.lockers(anyCollection())).thenReturn(unavailable());
        when(resolver.boxes(anyCollection())).thenReturn(unavailable());
        when(resolver.stores(anyCollection())).thenReturn(unavailable());
        when(resolver.paymentSummaries(anyCollection())).thenReturn(unavailable());

        AdminOrderResponse result = service.search(criteria(0, 20, null)).content().get(0);

        assertEquals("ORD-1", result.orderCode());
        assertNull(result.customer());
        assertNull(result.locker());
        assertNull(result.payment());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchUsesCreatedAtDescendingByDefaultAndValidatesPaging() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 50), 0));

        service.search(criteria(1, 50, null));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")),
                pageable.getValue().getSort());
        assertEquals(50, pageable.getValue().getPageSize());

        assertEquals("INVALID_PAGE_SIZE",
                assertThrows(BusinessException.class, () -> service.search(criteria(0, 101, null))).getCode());
        assertEquals("INVALID_SORT",
                assertThrows(BusinessException.class, () -> service.search(criteria(0, 10, "pinCode,asc"))).getCode());
        assertEquals(Sort.by(Sort.Direction.ASC, "totalPrice").and(Sort.by(Sort.Direction.DESC, "id")),
                service.sort("totalPrice,asc"));
    }

    @Test
    void detailIncludesTimelineWithActorNamesAndDroneMission() {
        LockerOrder drone = order(5L, "DRONE_DELIVERY", 44L);
        drone.setDestinationLockerId(8L);
        drone.setDeliveryStage("EN_ROUTE");
        DroneMission mission = new DroneMission();
        mission.setId(31L);
        mission.setOrderId(5L);
        mission.setDroneCode("DR-01");
        mission.setSourceLockerId(1L);
        mission.setDestinationLockerId(8L);
        mission.setStatus("LAUNCHING");
        OrderStatusHistory created = new OrderStatusHistory();
        created.setOrderId(5L);
        created.setNewStatus("AWAITING_DISPATCH");
        created.setChangedByUserId(99L);
        created.setNote("Order created");

        when(orderRepository.findById(5L)).thenReturn(Optional.of(drone));
        when(missionRepository.findByOrderIdIn(List.of(5L))).thenReturn(List.of(mission));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(created));
        when(resolver.users(anyCollection())).thenReturn(lookup(Map.of(99L, user(99L, "Admin Kho"))));
        when(resolver.lockers(anyCollection())).thenReturn(lookup(Map.of(1L, locker(1L, null), 8L, locker(8L, null))));
        when(resolver.boxes(anyCollection())).thenReturn(lookup(Map.of()));
        when(resolver.stores(anyCollection())).thenReturn(lookup(Map.of()));
        when(resolver.paymentSummaries(anyCollection())).thenReturn(lookup(Map.of()));

        AdminOrderResponse result = service.detail(5L);

        assertNotNull(result.drone());
        assertEquals("DR-01", result.drone().droneCode());
        assertEquals("LK-1", result.drone().sourceLocker().code());
        assertEquals("LK-8", result.destinationLocker().code());
        assertEquals(1, result.timeline().size());
        assertEquals("Admin Kho", result.timeline().get(0).changedByName());
    }

    private AdminOrderQueryService.SearchCriteria criteria(int page, int size, String sort) {
        return new AdminOrderQueryService.SearchCriteria(
                page, size, null, null, null, null, null, null, null, null, null, sort);
    }

    private LockerOrder order(Long id, String type, Long userId) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setOrderCode("ORD-" + id);
        order.setUserId(userId);
        order.setType(type);
        order.setStatus("STORING");
        order.setLockerId(7L);
        order.setTotalPrice(BigDecimal.valueOf(15000));
        order.setOriginalPrice(BigDecimal.valueOf(15000));
        order.setCreatedAt(LocalDateTime.of(2026, 9, 14, 0, 0));
        return order;
    }

    private UserSummary user(Long id, String name) {
        return new UserSummary(id, "u" + id + "@example.com", "09000000" + id, name, "ACTIVE");
    }

    private LockerInfo locker(Long id, Long storeId) {
        return new LockerInfo(id, storeId, "LK-" + id, "Tủ " + id, "ACTIVE", "Địa chỉ " + id, null, null, false, null, 10, 4);
    }

    private static <T> Lookup<T> lookup(Map<Long, T> values) {
        return new Lookup<>(values, true);
    }

    private static <T> Lookup<T> unavailable() {
        return new Lookup<>(Map.of(), false);
    }
}
