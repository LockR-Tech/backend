package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.DroneJourneyEventResponse;
import com.huynqb.laundrylocker.order.dto.OrderMilestone;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderDetailResponse;
import com.huynqb.laundrylocker.order.dto.ReceiverOrderResponse;
import com.huynqb.laundrylocker.order.dto.admin.BoxInfo;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.OrderStatusHistory;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock private OrderStatusHistoryRepository historyRepository;

    private ReceiverOrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReceiverOrderQueryService(
                orderRepository, userClient, references, qrTokenService, historyRepository);
        lenient().when(references.lockers(any())).thenReturn(Lookup.of(Map.of(
                7L, new LockerInfo(7L, 3L, "CAB-TU01", "Tủ thư viện", "ACTIVE", "Khu A", 10.8, 106.6, false, null, 8, 4),
                8L, new LockerInfo(8L, 3L, "CAB-NGUON", "Tủ nguồn", "ACTIVE", "Khu B", 10.9, 106.7, true, null, 8, 4))));
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

    // ---- Chi tiết đơn (GET /api/orders/incoming/{id}, /delegated-to-me/{id}) ----

    @Test
    void receiverSeesFullDetailWithPickupCodeBoxSizeSenderContactAndTimeline() {
        callerPhone("0901234567");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverName("Người Nhận");
        order.setReceiverEmail("nhan@lockr.vn");
        order.setCustomerNote("Hàng dễ vỡ, nhẹ tay");
        order.setDescription("Sách giáo trình");
        order.setStaffNote("ghi chú nội bộ");
        order.setCreatedAt(LocalDateTime.of(2026, 10, 12, 1, 0));
        order.setPaidAt(LocalDateTime.of(2026, 10, 12, 1, 5));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(21L)).thenReturn(List.of(
                history(null, "INITIALIZED", LocalDateTime.of(2026, 10, 12, 1, 0)),
                history("INITIALIZED", "STORING", LocalDateTime.of(2026, 10, 12, 1, 20))));

        ReceiverOrderDetailResponse detail = service.detail(55L, 21L);

        assertEquals(ReceiverOrderQueryService.VIEWER_RECEIVER, detail.viewerRole());
        assertTrue(detail.readyForPickup());
        assertEquals("123456", detail.pickupPin());
        assertEquals("LLQR.token-123456", detail.qrToken());
        assertEquals("Người Gửi", detail.senderName());
        // Đơn tủ không khai SĐT người gửi ⇒ SĐT tài khoản người gửi; email tài khoản không lộ ra.
        assertEquals("0900000010", detail.senderPhone());
        assertNull(detail.senderEmail());
        assertEquals("nhan@lockr.vn", detail.receiverEmail());
        assertEquals("CAB-TU01", detail.lockerCode());
        assertEquals("Khu A", detail.lockerAddress());
        assertEquals(5, detail.boxNumber());
        assertEquals("MEDIUM", detail.boxSize());
        assertEquals("STANDARD", detail.boxType());
        assertEquals("Hàng dễ vỡ, nhẹ tay", detail.customerNote());
        assertEquals("Sách giáo trình", detail.parcel().description());
        assertNull(detail.sourceLockerId());
        assertTrue(detail.journey().isEmpty());
        assertFalse(detail.droneTrackingAvailable());
        assertEquals(List.of("CREATED", "PAID", "STORED"),
                detail.timeline().stream().map(OrderMilestone::code).toList());
        assertEquals("Hàng đã vào ô, chờ lấy", detail.timeline().get(2).label());
    }

    @Test
    void receiverMatchedByAnyPhoneSpellingIsAllowed() {
        callerPhone("0901234567");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverPhone("+84 901-234.567");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        assertEquals(ReceiverOrderQueryService.VIEWER_RECEIVER, service.detail(55L, 21L).viewerRole());
    }

    @Test
    void pickupCodeStaysHiddenUntilTheParcelIsInTheCell() {
        callerPhone("0901234567");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(sendOrder("INITIALIZED")));

        ReceiverOrderDetailResponse detail = service.detail(55L, 21L);

        assertFalse(detail.readyForPickup());
        assertNull(detail.pickupPin());
        assertNull(detail.qrToken());
    }

    @Test
    void delegateSeesCodeAndDelegationButNotTheOriginalReceiversEmail() {
        callerPhone("0907654321");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverEmail("nhan-goc@lockr.vn");
        order.setReceiverPhone("0907654321");
        order.setDelegatePhone("0907 654 321");
        order.setDelegateName("Người Lấy Hộ");
        order.setDelegateNote("Lấy giúp trước 6h");
        order.setDelegatedAt(LocalDateTime.of(2026, 10, 11, 3, 0));
        order.setSenderEmail("gui@example.com");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        ReceiverOrderDetailResponse detail = service.detail(66L, 21L);

        assertEquals(ReceiverOrderQueryService.VIEWER_DELEGATE, detail.viewerRole());
        assertEquals("123456", detail.pickupPin());
        assertTrue(detail.delegated());
        assertEquals("Người Lấy Hộ", detail.delegateName());
        assertEquals("0907 654 321", detail.delegatePhone());
        assertEquals("Lấy giúp trước 6h", detail.delegateNote());
        assertNull(detail.receiverEmail());
        assertNull(detail.senderEmail());
        assertTrue(detail.timeline().stream().anyMatch(m -> m.code().equals("DELEGATED")));
    }

    @Test
    void originalReceiverOfADelegatedOrderSeesNoCodeAndNoDelegateContact() {
        callerPhone("0901234567");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverId(55L);
        order.setReceiverPhone("0907654321");
        order.setDelegatePhone("0907654321");
        order.setDelegateName("Người Lấy Hộ");
        order.setDelegateNote("Lấy giúp trước 6h");
        order.setDelegatedAt(LocalDateTime.of(2026, 10, 11, 3, 0));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        ReceiverOrderDetailResponse detail = service.detail(55L, 21L);

        assertEquals(ReceiverOrderQueryService.VIEWER_RECEIVER, detail.viewerRole());
        assertTrue(detail.readyForPickup());
        assertNull(detail.pickupPin());
        assertNull(detail.qrToken());
        assertTrue(detail.delegated());
        assertEquals(LocalDateTime.of(2026, 10, 11, 3, 0), detail.delegatedAt());
        assertNull(detail.delegateName());
        assertNull(detail.delegatePhone());
        assertNull(detail.delegateNote());
    }

    @Test
    void someoneElsesOrderIsNotFoundRatherThanForbidden() {
        callerPhone("0911111111");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(sendOrder("STORING")));

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.detail(77L, 21L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        verify(historyRepository, never()).findByOrderIdOrderByCreatedAtAscIdAsc(anyLong());
    }

    @Test
    void missingOrderLooksExactlyLikeSomeoneElsesOrder() {
        callerPhone("0911111111");
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());
        when(orderRepository.findById(21L)).thenReturn(Optional.of(sendOrder("STORING")));

        NotFoundException missing = assertThrows(NotFoundException.class, () -> service.detail(77L, 404L));
        NotFoundException foreign = assertThrows(NotFoundException.class, () -> service.detail(77L, 21L));

        assertEquals(missing.getCode(), foreign.getCode());
        assertEquals(missing.getStatus(), foreign.getStatus());
    }

    @Test
    void senderCannotOpenTheirOwnOrderThroughTheReceiverDetail() {
        callerPhone("0901234567");
        LockerOrder order = sendOrder("STORING");
        order.setUserId(55L);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        assertThrows(NotFoundException.class, () -> service.detail(55L, 21L));
    }

    @Test
    void orderReachingMeOnlyThroughDelegationOpensAsDelegate() {
        callerPhone("0907654321");
        LockerOrder order = sendOrder("STORING");
        order.setReceiverPhone("0907654321");
        order.setDelegatePhone("+84907654321");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        assertEquals(ReceiverOrderQueryService.VIEWER_DELEGATE, service.detail(66L, 21L).viewerRole());
    }

    @Test
    void droneOrderShowsSourceLockerStageChangesAndTracking() {
        callerPhone("0901234567");
        LockerOrder order = droneOrder("STORING", "READY_FOR_PICKUP");
        order.setSourceLockerId(8L);
        order.setRouteDistanceMeters(1200);
        order.setSenderName("Chị Gửi");
        order.setSenderPhone("0988000111");
        order.setSenderEmail("chi.gui@example.com");
        order.setParcelWeightGrams(800);
        order.setParcelLengthCm(30);
        order.setParcelWidthCm(20);
        order.setParcelHeightCm(10);
        order.setParcelCategory("DOCUMENT");
        order.setParcelDeclaredValue(new BigDecimal("250000"));
        order.setParcelFragile(true);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(21L)).thenReturn(List.of(
                history(null, "AWAITING_DISPATCH", LocalDateTime.of(2026, 10, 12, 1, 0)),
                history("AWAITING_DISPATCH", "EN_ROUTE", LocalDateTime.of(2026, 10, 12, 1, 30)),
                history("EN_ROUTE", "EN_ROUTE", LocalDateTime.of(2026, 10, 12, 1, 31)),
                history("EN_ROUTE", "READY_FOR_PICKUP", LocalDateTime.of(2026, 10, 12, 1, 40))));

        ReceiverOrderDetailResponse detail = service.detail(55L, 21L);

        assertEquals(8L, detail.sourceLockerId());
        assertEquals("Tủ nguồn", detail.sourceLockerName());
        assertEquals(1200, detail.routeDistanceMeters());
        assertEquals("Chị Gửi", detail.senderName());
        assertEquals("0988000111", detail.senderPhone());
        assertEquals("chi.gui@example.com", detail.senderEmail());
        assertEquals(800, detail.parcel().weightGrams());
        assertEquals("DOCUMENT", detail.parcel().category());
        assertEquals(new BigDecimal("250000"), detail.parcel().declaredValue());
        assertTrue(detail.parcel().fragile());
        assertEquals(0, detail.etaMinutes());
        assertTrue(detail.droneTrackingAvailable());
        // Chỉ các lần đổi chặng, mới nhất trước, không người thao tác/ghi chú.
        List<DroneJourneyEventResponse> journey = detail.journey();
        assertEquals(List.of("READY_FOR_PICKUP", "EN_ROUTE", "AWAITING_DISPATCH"),
                journey.stream().map(DroneJourneyEventResponse::toStage).toList());
        assertTrue(journey.stream().allMatch(e -> e.note() == null && e.actorName() == null && e.actorUserId() == null));
        assertEquals("Kiện đã vào ô tủ nhận", detail.timeline().stream()
                .filter(m -> m.code().equals("STORED")).findFirst().orElseThrow().label());
    }

    @Test
    void failedDroneOrderExplainsWhyInPlainVietnamese() {
        callerPhone("0901234567");
        LockerOrder order = droneOrder("CANCELED", "FAILED");
        order.setFailureReason("WEATHER");
        order.setFailedAt(LocalDateTime.of(2026, 10, 12, 2, 0));
        order.setParcelReturnedAt(LocalDateTime.of(2026, 10, 12, 3, 0));
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));

        ReceiverOrderDetailResponse detail = service.detail(55L, 21L);

        assertEquals("WEATHER", detail.failureReason());
        assertEquals("Tạm dừng bay do thời tiết hoặc lệnh điều hành", detail.failureReasonLabel());
        assertNull(detail.pickupPin());
        assertNull(detail.etaMinutes());
        assertFalse(detail.droneTrackingAvailable());
        assertEquals(List.of("FAILED", "PARCEL_RETURNED"),
                detail.timeline().stream().map(OrderMilestone::code).toList());
    }

    private static OrderStatusHistory history(String from, String to, LocalDateTime at) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(21L);
        h.setOldStatus(from);
        h.setNewStatus(to);
        h.setNote("ghi chú nội bộ");
        h.setChangedByUserId(900L);
        h.setCreatedAt(at);
        return h;
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
