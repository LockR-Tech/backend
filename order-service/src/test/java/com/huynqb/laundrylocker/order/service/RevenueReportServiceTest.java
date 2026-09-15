package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments.CollectedPayment;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments.CollectedRefund;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments.OrderPaidTotal;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.*;
import com.huynqb.laundrylocker.order.dto.admin.StoreInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevenueReportServiceTest {

    private static final String FROM = "2026-09-01";
    private static final String TO = "2026-09-15";

    @Mock private LockerOrderRepository orderRepository;
    @Mock private AdminReferenceResolver resolver;

    private RevenueReportService service;
    private LockerOrder send;
    private LockerOrder rental;
    private LockerOrder drone;
    private LockerOrder canceled;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new RevenueReportService(orderRepository, resolver);
        // Hôm nay: 2026-09-15 (thứ Ba) 10:00 giờ Việt Nam; DB lưu UTC.
        service.setTime(new BusinessTime(ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE,
                Clock.fixed(Instant.parse("2026-09-15T03:00:00Z"), ZoneOffset.UTC)));

        send = order(1L, "SEND", "COMPLETED", 44L, 7L, 17000, 2000, LocalDateTime.of(2026, 9, 10, 2, 0));
        send.setCompletedAt(LocalDateTime.of(2026, 9, 12, 3, 0));
        rental = order(2L, "RENTAL", "COMPLETED", 45L, 8L, 10000, 0, LocalDateTime.of(2026, 9, 11, 2, 0));
        drone = order(3L, "DRONE_DELIVERY", "COMPLETED", 44L, 9L, 15000, 0, LocalDateTime.of(2026, 8, 20, 2, 0));
        canceled = order(4L, "SEND", "CANCELED", 46L, 7L, 15000, 0, LocalDateTime.of(2026, 9, 12, 1, 0));

        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of(send, rental, canceled));
        when(orderRepository.findAllById(any())).thenReturn(List.of(drone));
        when(resolver.collected(anyString(), anyString(), isNull())).thenReturn(new CollectedPayments(
                List.of(
                        payment(11L, 1L, 44L, "WALLET", 15000, LocalDateTime.of(2026, 9, 10, 3, 0)),
                        payment(12L, 1L, 44L, "CASH", 2000, LocalDateTime.of(2026, 9, 12, 3, 0)),
                        // 17:30 UTC ngày 11 = 00:30 ngày 12 giờ VN
                        payment(13L, 2L, 45L, "VNPAY", 10000, LocalDateTime.of(2026, 9, 11, 17, 30)),
                        payment(14L, 3L, 44L, "MOMO", 15000, LocalDateTime.of(2026, 9, 1, 0, 0))),
                List.of(new CollectedRefund(21L, 13L, 2L, 45L, BigDecimal.valueOf(3000), "VNPAY",
                        LocalDateTime.of(2026, 9, 13, 3, 0))),
                List.of(new OrderPaidTotal(1L, BigDecimal.valueOf(17000)),
                        new OrderPaidTotal(2L, BigDecimal.valueOf(10000)),
                        new OrderPaidTotal(3L, BigDecimal.valueOf(15000)))));
        when(resolver.allLockers()).thenReturn(lookup(List.of(
                locker(7L, 3L, 10), locker(8L, 3L, 5), locker(9L, null, 4), locker(10L, 4L, 2)), LockerInfo::id));
        when(resolver.lockers(anyCollection())).thenReturn(lookup(List.of(locker(7L, 3L, 10), locker(9L, null, 4)), LockerInfo::id));
        when(resolver.allStores()).thenReturn(lookup(List.of(
                new StoreInfo(3L, "Chi nhánh Q1", "028", "1 Lê Lợi", null, null, true, "ACTIVE"),
                new StoreInfo(4L, "Chi nhánh Q7", "028", "7 Nguyễn Văn Linh", null, null, true, "ACTIVE")), StoreInfo::id));
        when(resolver.users(anyCollection())).thenReturn(lookup(List.of(
                new UserSummary(44L, "an@example.com", "0901111111", "Nguyễn An", "ACTIVE"),
                new UserSummary(45L, "binh@example.com", "0902222222", "Trần Bình", "ACTIVE"),
                new UserSummary(46L, "chi@example.com", "0903333333", "Lê Chi", "ACTIVE")), UserSummary::id));
    }

    @Test
    void summaryUsesCollectedMoneyAndLoadsPaymentsOnceWhenWindowsOverlap() {
        RevenueSummaryResponse summary = service.summary(FROM, TO);

        RevenuePeriod current = summary.current();
        assertEquals(0, BigDecimal.valueOf(42000).compareTo(current.totalRevenue()));
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(current.refundAmount()));
        assertEquals(0, BigDecimal.valueOf(39000).compareTo(current.netRevenue()));
        assertEquals(3, current.orderCount());
        assertEquals(3, current.paidOrderCount());
        assertEquals(4, current.paymentCount());
        assertEquals(1, current.completedOrderCount());
        assertEquals(1, current.canceledOrderCount());
        assertEquals(0, BigDecimal.valueOf(14000).compareTo(current.averageOrderValue()));
        assertEquals(LocalDate.of(2026, 8, 17), summary.previousFrom());
        assertNull(summary.changes().totalRevenuePct());
        assertEquals(LocalDate.of(2026, 9, 14), summary.thisWeek().from());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.today().totalRevenue()));
        assertEquals(0, BigDecimal.valueOf(42000).compareTo(summary.thisMonth().totalRevenue()));
        verify(resolver, times(1)).collected(anyString(), anyString(), isNull());
        verify(resolver).collected(eq("2026-08-16T17:00:00"), eq("2026-09-15T17:00:00"), isNull());
    }

    @Test
    void dailySeriesBucketsByVietnamDateAndFillsEmptyDays() {
        DailyRevenueResponse daily = service.daily(FROM, TO);

        assertEquals(15, daily.days().size());
        Map<LocalDate, DailyRevenue> byDate = daily.days().stream()
                .collect(Collectors.toMap(DailyRevenue::date, d -> d));
        DailyRevenue sept12 = byDate.get(LocalDate.of(2026, 9, 12));
        assertEquals(0, BigDecimal.valueOf(12000).compareTo(sept12.revenue()));
        assertEquals(2, sept12.paymentCount());
        assertEquals(1, sept12.orderCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(byDate.get(LocalDate.of(2026, 9, 11)).revenue()));
        assertEquals(1, byDate.get(LocalDate.of(2026, 9, 11)).orderCount());
        assertEquals(0, BigDecimal.valueOf(-3000).compareTo(byDate.get(LocalDate.of(2026, 9, 13)).netRevenue()));
    }

    @Test
    void byServiceSeparatesOvertimeFeeFromOrderType() {
        Map<String, ServiceRevenue> rows = service.byService(FROM, TO).items().stream()
                .collect(Collectors.toMap(ServiceRevenue::serviceType, r -> r));

        assertEquals(0, BigDecimal.valueOf(15000).compareTo(rows.get("SEND").revenue()));
        assertEquals(2, rows.get("SEND").orderCount());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(rows.get(RevenueReportService.OVERTIME_FEE).revenue()));
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(rows.get("RENTAL").refundAmount()));
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(rows.get("DRONE_DELIVERY").revenue()));
        assertEquals(0, rows.get("DRONE_DELIVERY").orderCount());
        assertEquals(new BigDecimal("4.76"), rows.get(RevenueReportService.OVERTIME_FEE).sharePct());
    }

    @Test
    void byMethodAttributesRefundToOriginalMethod() {
        List<MethodRevenue> items = service.byMethod(FROM, TO).items();

        MethodRevenue vnpay = items.stream().filter(i -> i.method().equals("VNPAY")).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(7000).compareTo(vnpay.netRevenue()));
        assertEquals("CASH", items.get(items.size() - 1).method());
    }

    @Test
    void byLockerAndByStoreIncludeIdleLockersBoxCountsAndUnassigned() {
        LockerRevenueResponse lockers = service.byLocker(FROM, TO, null);
        Map<Long, LockerRevenue> lockerRows = lockers.items().stream()
                .filter(r -> r.lockerId() != null)
                .collect(Collectors.toMap(LockerRevenue::lockerId, r -> r));
        assertEquals(0, BigDecimal.valueOf(17000).compareTo(lockerRows.get(7L).revenue()));
        assertEquals(2, lockerRows.get(7L).orderCount());
        assertEquals(0, BigDecimal.valueOf(1700).compareTo(lockerRows.get(7L).revenuePerBox()));
        assertEquals("Chi nhánh Q1", lockerRows.get(7L).storeName());
        assertEquals(0, BigDecimal.ZERO.compareTo(lockerRows.get(10L).revenue()));
        assertEquals(2, service.byLocker(FROM, TO, 3L).items().size());

        Map<Long, StoreRevenue> stores = service.byStore(FROM, TO).items().stream()
                .filter(r -> r.storeId() != null)
                .collect(Collectors.toMap(StoreRevenue::storeId, r -> r));
        assertEquals(0, BigDecimal.valueOf(27000).compareTo(stores.get(3L).revenue()));
        assertEquals(2, stores.get(3L).lockerCount());
        assertEquals(15, stores.get(3L).boxCount());
        StoreRevenue unassigned = service.byStore(FROM, TO).items().stream()
                .filter(r -> r.storeId() == null).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(unassigned.revenue()));
    }

    @Test
    void byCustomerSortsPagesAndFiltersByName() {
        PageResponse<CustomerRevenue> page = service.byCustomer(FROM, TO, 0, 2, null, null);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(44L, page.content().get(0).userId());
        assertEquals(0, BigDecimal.valueOf(32000).compareTo(page.content().get(0).totalSpent()));
        assertEquals(0, BigDecimal.valueOf(16000).compareTo(page.content().get(0).averageOrderValue()));
        assertEquals(0, BigDecimal.valueOf(7000).compareTo(page.content().get(1).netSpent()));

        PageResponse<CustomerRevenue> filtered = service.byCustomer(FROM, TO, 0, 20, "orderCount,asc", "bình");
        assertEquals(1, filtered.totalElements());
        assertEquals("Trần Bình", filtered.content().get(0).fullName());

        assertEquals("INVALID_SORT",
                assertThrows(BusinessException.class, () -> service.byCustomer(FROM, TO, 0, 20, "pin", null)).getCode());
    }

    @Test
    void customerDetailListsOrdersWithAmountsMethodsAndAllTimePaid() {
        when(resolver.collected(anyString(), anyString(), eq(44L))).thenReturn(new CollectedPayments(
                List.of(
                        payment(11L, 1L, 44L, "WALLET", 15000, LocalDateTime.of(2026, 9, 10, 3, 0)),
                        payment(12L, 1L, 44L, "CASH", 2000, LocalDateTime.of(2026, 9, 12, 3, 0)),
                        payment(14L, 3L, 44L, "MOMO", 15000, LocalDateTime.of(2026, 9, 1, 0, 0))),
                List.of(),
                List.of(new OrderPaidTotal(1L, BigDecimal.valueOf(17000)), new OrderPaidTotal(3L, BigDecimal.valueOf(15000)))));
        when(orderRepository.findAll(any(Specification.class))).thenReturn(List.of(send));
        when(resolver.paymentSummaries(anyCollection())).thenReturn(lookup(List.of(new OrderPaymentSummary(
                1L, 2, 12L, "CASH", "COMPLETED", BigDecimal.valueOf(2000), null, BigDecimal.valueOf(17000), "CASH",
                LocalDateTime.of(2026, 9, 12, 3, 0), BigDecimal.ZERO)), OrderPaymentSummary::orderId));

        CustomerRevenueDetailResponse detail = service.customerDetail(44L, FROM, TO);

        assertEquals("Nguyễn An", detail.fullName());
        assertEquals(0, BigDecimal.valueOf(32000).compareTo(detail.totalSpent()));
        assertEquals(2, detail.orders().size());
        CustomerDetailOrder first = detail.orders().get(0);
        assertEquals(1L, first.orderId());
        assertEquals(List.of("WALLET", "CASH"), first.paymentMethods());
        assertEquals(0, BigDecimal.valueOf(17000).compareTo(first.paidAllTime()));
        assertEquals("LK-7", first.lockerCode());
        assertEquals(0, BigDecimal.ZERO.compareTo(detail.orders().get(1).paidAllTime()));
    }

    @Test
    void paymentServiceOutageFailsReportInsteadOfShowingZeroRevenue() {
        when(resolver.collected(anyString(), anyString(), isNull())).thenThrow(new RuntimeException("connect timed out"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.daily(FROM, TO));

        assertEquals("PAYMENT_DATA_UNAVAILABLE", error.getCode());
        assertEquals(503, error.getStatus().value());
    }

    @Test
    void overtimePortionOnlyCountsMoneyBeyondBasePrice() {
        LockerOrder order = order(9L, "RENTAL", "COMPLETED", 1L, 1L, 12000, 2000, LocalDateTime.now());
        // Đã trả 10000 giá gốc, phí quá hạn chưa thu
        assertEquals(0, BigDecimal.ZERO.compareTo(
                RevenueReportService.overtimePortion(order, BigDecimal.valueOf(10000), BigDecimal.valueOf(10000))));
        // Kỳ này chỉ thu 2000 phí quá hạn, giá gốc thu kỳ trước
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(
                RevenueReportService.overtimePortion(order, BigDecimal.valueOf(2000), BigDecimal.valueOf(12000))));
        // Thu thiếu một phần phí quá hạn
        assertEquals(0, BigDecimal.valueOf(500).compareTo(
                RevenueReportService.overtimePortion(order, BigDecimal.valueOf(10500), BigDecimal.valueOf(10500))));
    }

    private LockerOrder order(Long id, String type, String status, Long userId, Long lockerId, long total, long extra,
                              LocalDateTime createdAt) {
        LockerOrder order = new LockerOrder();
        order.setId(id);
        order.setOrderCode("ORD-" + id);
        order.setType(type);
        order.setStatus(status);
        order.setUserId(userId);
        order.setLockerId(lockerId);
        order.setTotalPrice(BigDecimal.valueOf(total));
        order.setExtraFee(BigDecimal.valueOf(extra));
        order.setCreatedAt(createdAt);
        return order;
    }

    private CollectedPayment payment(Long id, Long orderId, Long userId, String method, long amount, LocalDateTime paidAt) {
        return new CollectedPayment(id, orderId, userId, BigDecimal.valueOf(amount), method, paidAt);
    }

    private LockerInfo locker(Long id, Long storeId, int boxes) {
        return new LockerInfo(id, storeId, "LK-" + id, "Tủ " + id, "ACTIVE", "Địa chỉ " + id, null, null, false, null, boxes, 0);
    }

    private static <T> Lookup<T> lookup(List<T> values, Function<T, Long> key) {
        Map<Long, T> map = new LinkedHashMap<>();
        values.forEach(v -> map.put(key.apply(v), v));
        return new Lookup<>(map, true);
    }
}
