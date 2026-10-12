package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiverOrderQueryServiceTest {

    @Mock private LockerOrderRepository orderRepository;
    @Mock private UserClient userClient;
    @Mock private AdminReferenceResolver references;
    @Mock private QrTokenService qrTokenService;

    private ReceiverOrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReceiverOrderQueryService(orderRepository, userClient, references, qrTokenService);
        lenient().when(references.lockers(any())).thenReturn(Lookup.of(Map.of(
                7L, new LockerInfo(7L, 3L, "CAB-TU01", "Tủ thư viện", "ACTIVE", "Khu A", 10.8, 106.6, false, null, 8, 4))));
        lenient().when(references.boxes(any())).thenReturn(Lookup.of(Map.of(
                70L, new BoxInfo(70L, 7L, 5, "MEDIUM", "STANDARD", "OCCUPIED", true))));
        lenient().when(references.users(any())).thenReturn(Lookup.of(Map.of(
                10L, new UserSummary(10L, "gui@lockr.vn", "0900000010", "Người Gửi", "ACTIVE"))));
        lenient().when(qrTokenService.issue(any(), any())).thenAnswer(i -> "LLQR.token-" + i.getArgument(1));
    }

    @Test
    void readyParcelShowsPickupCodeLockerBoxAndSender() {
        callerPhone("0901234567");
        LockerOrder order = sendOrder("STORING");
        when(orderRepository.findIncoming(eq(55L), anyCollection(), eq(true), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(order));

        ReceiverOrderResponse response = service.incoming(55L, false, null).getFirst();

        assertTrue(response.readyForPickup());
        assertEquals("123456", response.pickupPin());
        assertEquals("LLQR.token-123456", response.qrToken());
        assertEquals("Người Gửi", response.senderName());
        assertEquals("CAB-TU01", response.lockerCode());
        assertEquals(5, response.boxNumber());
        assertFalse(response.delegated());
    }

    @Test
    void parcelNotYetDroppedHidesTheSenderDropCode() {
        callerPhone("0901234567");
        when(orderRepository.findIncoming(eq(55L), anyCollection(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(sendOrder("INITIALIZED")));

        ReceiverOrderResponse response = service.incoming(55L, true, 10).getFirst();

        assertFalse(response.readyForPickup());
        assertNull(response.pickupPin());
        assertNull(response.qrToken());
    }

    @Test
    void droneOrderIsReadyOnlyOnceDepositedInTheCell() {
        callerPhone("0901234567");
        LockerOrder inFlight = droneOrder("AWAITING_DISPATCH", "EN_ROUTE");
        LockerOrder deposited = droneOrder("STORING", "READY_FOR_PICKUP");
        deposited.setId(22L);
        when(orderRepository.findIncoming(eq(55L), anyCollection(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(inFlight, deposited));

        List<ReceiverOrderResponse> result = service.incoming(55L, false, null);

        assertNull(result.get(0).pickupPin());
        assertEquals("123456", result.get(1).pickupPin());
        assertEquals(7L, result.get(1).lockerId());
        assertEquals(70L, result.get(1).boxId());
    }

    @Test
    void orderDelegatedToSomeoneElseHidesTheNewCodeFromTheOriginalReceiver() {
        callerPhone("0901234567");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverId(55L);
        order.setDelegatePhone("0907654321");
        order.setDelegateName("Người Lấy Hộ");
        when(orderRepository.findIncoming(eq(55L), anyCollection(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(order));

        ReceiverOrderResponse response = service.incoming(55L, false, null).getFirst();

        assertTrue(response.delegated());
        assertTrue(response.readyForPickup());
        assertNull(response.pickupPin());
        assertNull(response.delegateName());
    }

    @Test
    void orderReachingMeOnlyThroughDelegationStaysOutOfIncoming() {
        callerPhone("0907654321");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverPhone("0907654321");
        order.setDelegatePhone("+84907654321");
        when(orderRepository.findIncoming(eq(66L), anyCollection(), anyBoolean(), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(order));

        assertTrue(service.incoming(66L, false, null).isEmpty());
    }

    @Test
    void delegatedToMeShowsCodeAndDelegationDetails() {
        callerPhone("0907654321");
        LockerOrder order = sendOrder("STORING");
        order.setDelegatePhone("0907654321");
        order.setDelegateName("Người Lấy Hộ");
        order.setDelegateNote("Lấy giúp trước 6h");
        order.setDelegatedAt(LocalDateTime.of(2026, 10, 11, 3, 0));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> phones = ArgumentCaptor.forClass(Collection.class);
        when(orderRepository.findDelegatedTo(phones.capture(), eq(false), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(order));

        ReceiverOrderResponse response = service.delegatedToMe(66L, true, null).getFirst();

        assertTrue(phones.getValue().contains("+84907654321"));
        assertEquals("123456", response.pickupPin());
        assertEquals("Người Lấy Hộ", response.delegateName());
        assertEquals("Lấy giúp trước 6h", response.delegateNote());
        assertEquals("Người Gửi", response.senderName());
    }

    @Test
    void accountWithoutPhoneHasNoDelegatedOrders() {
        callerPhone(null);

        assertTrue(service.delegatedToMe(66L, false, null).isEmpty());
        verify(orderRepository, never()).findDelegatedTo(anyCollection(), anyBoolean(), anyCollection(), any());
    }

    @Test
    void limitIsCappedAtOneHundred() {
        callerPhone("0901234567");
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(orderRepository.findIncoming(eq(55L), anyCollection(), anyBoolean(), anyCollection(), page.capture()))
                .thenReturn(List.of());

        service.incoming(55L, false, 5000);

        assertEquals(ReceiverOrderQueryService.MAX_LIMIT, page.getValue().getPageSize());
    }

    @Test
    void userServiceOutageIsReportedInsteadOfAnEmptyList() {
        when(userClient.getUser(55L)).thenThrow(new IllegalStateException("user-service down"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.incoming(55L, false, null));

        assertEquals("USER_LOOKUP_UNAVAILABLE", error.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    private void callerPhone(String phone) {
        when(userClient.getUser(any())).thenReturn(ApiResponse.ok(
                new UserSummary(55L, "nhan@lockr.vn", phone, "Người Nhận", "ACTIVE")));
    }

    private static LockerOrder sendOrder(String status) {
        LockerOrder order = new LockerOrder();
        order.setId(21L);
        order.setOrderCode("ORD-21");
        order.setUserId(10L);
        order.setType("SEND");
        order.setStatus(status);
        order.setLockerId(7L);
        order.setSendBoxId(70L);
        order.setReceiverPhone("0901234567");
        order.setPinCode("123456");
        order.setPickupDeadline(LocalDateTime.now().plusHours(24));
        return order;
    }

    private static LockerOrder droneOrder(String status, String stage) {
        LockerOrder order = sendOrder(status);
        order.setType("DRONE_DELIVERY");
        order.setDeliveryStage(stage);
        order.setLockerId(null);
        order.setDestinationLockerId(7L);
        order.setSendBoxId(null);
        order.setReservedBoxId(70L);
        order.setReceiverUserId(55L);
        return order;
    }
}
