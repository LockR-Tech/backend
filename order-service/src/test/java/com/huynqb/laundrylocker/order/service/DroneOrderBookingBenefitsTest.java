package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.client.LockerCellClient;
import com.huynqb.laundrylocker.order.client.LockerClient;
import com.huynqb.laundrylocker.order.client.NotificationClient;
import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.dto.CellDto;
import com.huynqb.laundrylocker.order.dto.CreateDroneDeliveryOrderRequest;
import com.huynqb.laundrylocker.order.dto.DroneWeatherAssessment;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderStatusHistoryRepository;
import com.huynqb.laundrylocker.order.settings.TestOrderRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đặt đơn drone với mã giảm giá + điểm: mã theo tủ kiểm theo TỦ GỬI, phí gốc rồi mã rồi điểm, đơn 0đ
/// được ghi đã thanh toán (mở lượt tìm KTV sau commit).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DroneOrderBookingBenefitsTest {

    @Mock private LockerOrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private UserClient userClient;
    @Mock private LockerClient lockerClient;
    @Mock private LockerCellClient lockerCellClient;
    @Mock private NotificationClient notificationClient;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private DroneSourceLockerSelector sourceLockerSelector;
    @Mock private DroneDeliveryQueryService queryService;
    @Mock private DroneWeatherService weatherService;
    @Mock private OrderBenefitService benefits;
    @Mock private PaidOrderEffects paidEffects;
    @Mock private DroneFlightControl flightControl;

    private DroneOrderBookingService service;

    @BeforeEach
    void setUp() {
        service = new DroneOrderBookingService(
                orderRepository, historyRepository, userClient, lockerClient, lockerCellClient, notificationClient,
                rabbitTemplate, TestOrderRules.of(Map.of()), sourceLockerSelector, queryService, weatherService,
                benefits, paidEffects, flightControl);
        // Thời tiết xét cả chặng từ nơi gửi (điểm lấy hàng) tới tủ nhận.
        when(weatherService.assessJourney(org.mockito.ArgumentMatchers.eq(10.77), org.mockito.ArgumentMatchers.eq(106.70), any(), any())).thenReturn(new DroneWeatherAssessment(
                true, "SAFE", "Dự báo tuyến bay an toàn trong 30 phút tới.",
                java.time.OffsetDateTime.now(), 30, 3, 10, 15, 0, 0));
        when(sourceLockerSelector.select(10.77, 106.70, 5L, null)).thenReturn(new DroneSourceLockerSelector.Selection(
                OrderBenefitServiceTest.locker(7L, "TU07", "Tủ Thư viện"),
                OrderBenefitServiceTest.locker(5L, "TU05", "Tủ Ký túc xá"), 300, 2000));
        when(lockerCellClient.findAvailable(5L, null, "DRONE"))
                .thenReturn(ApiResponse.ok(new CellDto(9001L, 1, "M", "DRONE", 0, 0, "AVAILABLE", null)));
        when(orderRepository.saveAndFlush(any(LockerOrder.class))).thenAnswer(inv -> {
            LockerOrder order = inv.getArgument(0);
            if (order.getId() == null) {
                order.setId(21L);
            }
            return order;
        });
    }

    @Test
    void voucherIsCheckedAtTheSourceLockerThenPointsThenFreeCheck() {
        service.create(request(" tutv ", 40), 44L, "idem-1");

        ArgumentCaptor<LockerOrder> order = ArgumentCaptor.forClass(LockerOrder.class);
        InOrder steps = inOrder(benefits, paidEffects);
        // Tủ gửi (7) — không phải tủ nhận (5, lockerId của đơn drone).
        steps.verify(benefits).applyVouchers(order.capture(), eq(List.of("TUTV")), eq(7L));
        steps.verify(benefits).redeemPoints(any(LockerOrder.class), eq(40));
        steps.verify(paidEffects).settleIfFree(any(LockerOrder.class));
        assertEquals(5L, order.getValue().getLockerId());
        assertEquals(7L, order.getValue().getSourceLockerId());
        // Drone chỉ giao vào tủ: ô 9001 khách chọn là ô NHẬN, đơn không có ô gửi.
        assertEquals(9001L, order.getValue().getReceiveBoxId());
        assertEquals(9001L, order.getValue().getReservedBoxId());
        assertNull(order.getValue().getSendBoxId());
        assertEquals(0, new BigDecimal("15000").compareTo(order.getValue().getOriginalPrice()));
    }

    @Test
    void fullyDiscountedDroneOrderTellsTheSenderNoPaymentIsNeeded() {
        when(paidEffects.settleIfFree(any(LockerOrder.class))).thenReturn(true);

        service.create(request("FREE", null), 44L, "idem-2");

        verify(notificationClient).requestNotification(org.mockito.ArgumentMatchers.argThat(
                n -> n.message().contains("giảm giá toàn bộ")));
    }

    @Test
    void linksReceiverAccountByRegisteredEmail() {
        when(userClient.getUserByEmail("nhan@gmail.com"))
                .thenReturn(ApiResponse.ok(new UserSummary(
                        88L, "nhan@gmail.com", "0902000222", "Tran Thi Nhan", "ACTIVE")));

        service.create(request(null, null), 44L, "idem-receiver-email");

        ArgumentCaptor<LockerOrder> order = ArgumentCaptor.forClass(LockerOrder.class);
        verify(orderRepository, times(2)).saveAndFlush(order.capture());
        assertEquals(88L, order.getAllValues().getLast().getReceiverUserId());
        assertEquals(88L, order.getAllValues().getLast().getReceiverId());
        verify(userClient, never()).getUserByPhone("0902000222");
        verify(notificationClient).requestNotification(org.mockito.ArgumentMatchers.argThat(
                notification -> notification.userId().equals(88L)
                        && notification.type().equals("ORDER_CREATED")));
    }

    @Test
    void unsafeWeatherBlocksBookingBeforeDestinationCellIsReserved() {
        when(weatherService.assessJourney(any(), any(), any(), any())).thenReturn(new DroneWeatherAssessment(
                false, "UNSAFE", "Không thể đặt chuyến bay: có mưa hoặc dông trên tuyến trong 30 phút tới.",
                java.time.OffsetDateTime.now(), 30, 3, 12, 18, 0.4, 95));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(null, null), 44L, "idem-weather-unsafe"));

        assertEquals("DRONE_WEATHER_UNSAFE", error.getCode());
        verify(lockerClient, never()).reserveBox(any(), any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidVoucherReleasesTheReservedCell() {
        doThrow(new BusinessException("PROMOTION_INVALID", "Mã TUTV: chỉ áp dụng tại Tủ Thư viện"))
                .when(benefits).applyVouchers(any(), any(), any());

        assertThrows(BusinessException.class, () -> service.create(request("TUTV", null), 44L, "idem-3"));

        verify(lockerClient).releaseBox(9001L);
        verify(paidEffects, never()).settleIfFree(any());
    }

    @Test
    void rejectsDestinationCellAlreadyHeldByActiveOrder() {
        LockerOrder existing = new LockerOrder();
        existing.setId(99L);
        existing.setReservedBoxId(9001L);
        existing.setStatus("AWAITING_DISPATCH");
        when(orderRepository.findActiveByBoxId(9001L)).thenReturn(List.of(existing));

        BusinessException err = assertThrows(
                BusinessException.class, () -> service.create(request(null, null), 44L, "idem-dup-box"));

        assertEquals("BOX_NOT_AVAILABLE", err.getCode());
        verify(lockerClient, never()).reserveBox(any(), any());
    }

    private static CreateDroneDeliveryOrderRequest request(String promotionCode, Integer pointsToUse) {
        return new CreateDroneDeliveryOrderRequest(
                5L, null,
                "12 Nguyễn Huệ, Quận 1", 10.77, 106.70, null,
                "Nguyễn Văn Gửi", "0901000111", "gui@gmail.com",
                "Trần Thị Nhận", "0902000222", "nhan@gmail.com",
                500, "DOCUMENT", null, null, null, null, null, null, true,
                "STANDARD", promotionCode, pointsToUse);
    }
}
