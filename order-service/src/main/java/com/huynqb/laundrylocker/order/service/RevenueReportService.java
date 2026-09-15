package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.PageResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.common.util.BusinessTime.DateRange;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments.CollectedPayment;
import com.huynqb.laundrylocker.order.dto.admin.CollectedPayments.CollectedRefund;
import com.huynqb.laundrylocker.order.dto.admin.LockerInfo;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.dto.admin.RevenueDtos.*;
import com.huynqb.laundrylocker.order.dto.admin.StoreInfo;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.OrderSpecifications;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Báo cáo doanh thu cho trang /admin/revenue, dựa trên tiền thực thu từ payment-service
 * (xem {@link com.huynqb.laundrylocker.order.dto.admin.RevenueDtos}). Mỗi báo cáo: một lời gọi
 * /internal/payments/collected cho cả khoảng + một truy vấn đơn + tra cứu tên theo lô.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevenueReportService {

    public static final String OVERTIME_FEE = "OVERTIME_FEE";
    static final List<String> MAIN_SERVICE_TYPES = List.of("SEND", "RENTAL", "DRONE_DELIVERY");
    static final Set<String> CUSTOMER_SORTS = Set.of("totalSpent", "netSpent", "orderCount", "paidOrderCount",
            "averageOrderValue", "lastOrderAt", "lastPaidAt");

    private final LockerOrderRepository orderRepository;
    private final AdminReferenceResolver resolver;

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    // ------------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public RevenueSummaryResponse summary(String from, String to) {
        DateRange current = time.dateRange(from, to);
        DateRange previous = current.previous();
        LocalDate today = time.today();
        DateRange todayRange = new DateRange(today, today);
        DateRange week = new DateRange(time.startOfWeek(today), today);
        DateRange month = new DateRange(time.startOfMonth(today), today);

        Dataset selected = load(new DateRange(previous.from(), current.to()), null);
        DateRange recentWindow = new DateRange(min(week.from(), month.from()), today);
        Dataset recent = covers(selected.window, recentWindow) ? selected : load(recentWindow, null);

        RevenuePeriod currentPeriod = period(selected, current);
        RevenuePeriod previousPeriod = period(selected, previous);
        return new RevenueSummaryResponse(
                current.from(), current.to(), previous.from(), previous.to(),
                currentPeriod, previousPeriod, changes(currentPeriod, previousPeriod),
                period(recent, todayRange), period(recent, week), period(recent, month));
    }

    // ------------------------------------------------------------------ daily

    @Transactional(readOnly = true)
    public DailyRevenueResponse daily(String from, String to) {
        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, null);
        Map<LocalDate, List<CollectedPayment>> paymentsByDay = data.payments.stream()
                .collect(Collectors.groupingBy(p -> time.businessDate(p.paidAt())));
        Map<LocalDate, List<CollectedRefund>> refundsByDay = data.refunds.stream()
                .collect(Collectors.groupingBy(r -> time.businessDate(r.refundedAt())));
        Map<LocalDate, Long> ordersByDay = data.ordersCreatedIn(range).stream()
                .collect(Collectors.groupingBy(o -> time.businessDate(o.getCreatedAt()), Collectors.counting()));

        List<DailyRevenue> days = new ArrayList<>();
        for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
            List<CollectedPayment> payments = paymentsByDay.getOrDefault(d, List.of());
            BigDecimal revenue = sumPayments(payments);
            BigDecimal refunds = sumRefunds(refundsByDay.getOrDefault(d, List.of()));
            days.add(new DailyRevenue(d, revenue, refunds, revenue.subtract(refunds),
                    distinctOrders(payments), payments.size(), ordersByDay.getOrDefault(d, 0L)));
        }
        return new DailyRevenueResponse(range.from(), range.to(), sumPayments(data.payments), days);
    }

    // ------------------------------------------------------------------ by service type

    @Transactional(readOnly = true)
    public ServiceRevenueResponse byService(String from, String to) {
        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, null);
        Map<String, Accumulator> rows = new LinkedHashMap<>();
        MAIN_SERVICE_TYPES.forEach(type -> rows.put(type, new Accumulator()));
        Accumulator overtimeRow = new Accumulator();

        data.paymentsByOrder().forEach((orderId, payments) -> {
            LockerOrder order = data.orders.get(orderId);
            BigDecimal inRange = sumPayments(payments);
            BigDecimal overtime = overtimePortion(order, inRange, data.paidTotals.get(orderId));
            Accumulator row = rows.computeIfAbsent(serviceType(order), k -> new Accumulator());
            row.revenue = row.revenue.add(inRange.subtract(overtime));
            row.paidOrders.add(orderId);
            row.paymentCount += payments.size();
            if (overtime.signum() > 0) {
                overtimeRow.revenue = overtimeRow.revenue.add(overtime);
                overtimeRow.paidOrders.add(orderId);
            }
        });
        data.refunds.forEach(r -> {
            Accumulator row = rows.computeIfAbsent(serviceType(data.orders.get(r.orderId())), k -> new Accumulator());
            row.refund = row.refund.add(nz(r.amount()));
        });
        data.ordersCreatedIn(range).forEach(o -> rows.computeIfAbsent(serviceType(o), k -> new Accumulator()).orderCount++);
        rows.put(OVERTIME_FEE, overtimeRow);

        BigDecimal total = sumPayments(data.payments);
        List<ServiceRevenue> items = rows.entrySet().stream()
                .filter(e -> MAIN_SERVICE_TYPES.contains(e.getKey()) || OVERTIME_FEE.equals(e.getKey()) || !e.getValue().isEmpty())
                .map(e -> new ServiceRevenue(e.getKey(), e.getValue().revenue, e.getValue().refund,
                        e.getValue().revenue.subtract(e.getValue().refund), share(e.getValue().revenue, total),
                        e.getValue().orderCount, e.getValue().paidOrders.size(), e.getValue().paymentCount))
                .toList();
        return new ServiceRevenueResponse(range.from(), range.to(), total, items);
    }

    // ------------------------------------------------------------------ by payment method

    @Transactional(readOnly = true)
    public MethodRevenueResponse byMethod(String from, String to) {
        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, null);
        Map<String, Accumulator> rows = new TreeMap<>();
        data.payments.forEach(p -> {
            Accumulator row = rows.computeIfAbsent(upper(p.method()), k -> new Accumulator());
            row.revenue = row.revenue.add(nz(p.amount()));
            row.paymentCount++;
            row.paidOrders.add(p.orderId());
        });
        data.refunds.forEach(r -> {
            Accumulator row = rows.computeIfAbsent(upper(r.method()), k -> new Accumulator());
            row.refund = row.refund.add(nz(r.amount()));
        });
        BigDecimal total = sumPayments(data.payments);
        List<MethodRevenue> items = rows.entrySet().stream()
                .map(e -> new MethodRevenue(e.getKey(), e.getValue().revenue, e.getValue().refund,
                        e.getValue().revenue.subtract(e.getValue().refund), share(e.getValue().revenue, total),
                        e.getValue().paymentCount, e.getValue().paidOrders.size()))
                .sorted(Comparator.comparing(MethodRevenue::revenue).reversed())
                .toList();
        return new MethodRevenueResponse(range.from(), range.to(), total, items);
    }

    // ------------------------------------------------------------------ by locker / store

    @Transactional(readOnly = true)
    public LockerRevenueResponse byLocker(String from, String to, Long storeId) {
        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, null);
        Lookup<LockerInfo> lockers = resolver.allLockers();
        Lookup<StoreInfo> stores = resolver.allStores();

        Map<Long, Accumulator> rows = new LinkedHashMap<>();
        lockers.values().keySet().forEach(id -> rows.put(id, new Accumulator()));
        Accumulator unassigned = new Accumulator();
        Function<LockerOrder, Accumulator> rowOf = order -> {
            Long lockerId = lockerOf(order);
            return lockerId == null ? unassigned : rows.computeIfAbsent(lockerId, k -> new Accumulator());
        };
        accumulate(data, range, rowOf);

        BigDecimal total = sumPayments(data.payments);
        List<LockerRevenue> items = new ArrayList<>();
        rows.forEach((lockerId, acc) -> {
            LockerInfo locker = lockers.get(lockerId);
            Long lockerStoreId = locker == null ? null : locker.storeId();
            if (storeId != null && !storeId.equals(lockerStoreId)) {
                return;
            }
            StoreInfo store = stores.get(lockerStoreId);
            long boxes = locker == null || locker.totalBoxes() == null ? 0 : locker.totalBoxes();
            items.add(new LockerRevenue(
                    lockerId,
                    locker == null ? null : locker.code(),
                    locker == null ? null : locker.name(),
                    locker == null ? null : locker.address(),
                    locker == null ? null : locker.status(),
                    lockerStoreId,
                    store == null ? null : store.name(),
                    boxes,
                    acc.orderCount,
                    acc.paidOrders.size(),
                    acc.revenue,
                    acc.refund,
                    acc.revenue.subtract(acc.refund),
                    boxes == 0 ? null : acc.revenue.divide(BigDecimal.valueOf(boxes), 0, RoundingMode.HALF_UP),
                    share(acc.revenue, total)));
        });
        if (storeId == null && !unassigned.isEmpty()) {
            items.add(new LockerRevenue(null, null, null, null, null, null, null, 0, unassigned.orderCount,
                    unassigned.paidOrders.size(), unassigned.revenue, unassigned.refund,
                    unassigned.revenue.subtract(unassigned.refund), null, share(unassigned.revenue, total)));
        }
        items.sort(Comparator.comparing(LockerRevenue::revenue).reversed()
                .thenComparing(LockerRevenue::orderCount, Comparator.reverseOrder()));
        return new LockerRevenueResponse(range.from(), range.to(), total,
                lockers.available() && stores.available(), items);
    }

    @Transactional(readOnly = true)
    public StoreRevenueResponse byStore(String from, String to) {
        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, null);
        Lookup<LockerInfo> lockers = resolver.allLockers();
        Lookup<StoreInfo> stores = resolver.allStores();

        Map<Long, Accumulator> rows = new LinkedHashMap<>();
        stores.values().keySet().forEach(id -> rows.put(id, new Accumulator()));
        Accumulator unassigned = new Accumulator();
        Function<LockerOrder, Accumulator> rowOf = order -> {
            Long storeId = order == null ? null : order.getStoreId();
            if (storeId == null && order != null) {
                LockerInfo locker = lockers.get(lockerOf(order));
                storeId = locker == null ? null : locker.storeId();
            }
            return storeId == null ? unassigned : rows.computeIfAbsent(storeId, k -> new Accumulator());
        };
        accumulate(data, range, rowOf);

        Map<Long, List<LockerInfo>> lockersByStore = lockers.values().values().stream()
                .filter(l -> l.storeId() != null)
                .collect(Collectors.groupingBy(LockerInfo::storeId));
        BigDecimal total = sumPayments(data.payments);
        List<StoreRevenue> items = new ArrayList<>();
        rows.forEach((storeId, acc) -> {
            StoreInfo store = stores.get(storeId);
            List<LockerInfo> storeLockers = lockersByStore.getOrDefault(storeId, List.of());
            items.add(new StoreRevenue(
                    storeId,
                    store == null ? null : store.name(),
                    store == null ? null : store.address(),
                    store == null ? null : store.contactPhone(),
                    storeLockers.size(),
                    storeLockers.stream().mapToLong(l -> l.totalBoxes() == null ? 0 : l.totalBoxes()).sum(),
                    acc.orderCount,
                    acc.paidOrders.size(),
                    acc.revenue,
                    acc.refund,
                    acc.revenue.subtract(acc.refund),
                    share(acc.revenue, total)));
        });
        if (!unassigned.isEmpty()) {
            long unassignedLockers = lockers.values().values().stream().filter(l -> l.storeId() == null).count();
            long unassignedBoxes = lockers.values().values().stream().filter(l -> l.storeId() == null)
                    .mapToLong(l -> l.totalBoxes() == null ? 0 : l.totalBoxes()).sum();
            items.add(new StoreRevenue(null, null, null, null, unassignedLockers, unassignedBoxes,
                    unassigned.orderCount, unassigned.paidOrders.size(), unassigned.revenue, unassigned.refund,
                    unassigned.revenue.subtract(unassigned.refund), share(unassigned.revenue, total)));
        }
        items.sort(Comparator.comparing(StoreRevenue::revenue).reversed()
                .thenComparing(StoreRevenue::orderCount, Comparator.reverseOrder()));
        return new StoreRevenueResponse(range.from(), range.to(), total,
                lockers.available() && stores.available(), items);
    }

    // ------------------------------------------------------------------ by customer

    @Transactional(readOnly = true)
    public PageResponse<CustomerRevenue> byCustomer(String from, String to, int page, int size, String sort, String q) {
        if (page < 0) {
            throw new BusinessException("INVALID_PAGE", "page must be >= 0");
        }
        if (size < 1 || size > AdminOrderQueryService.MAX_PAGE_SIZE) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and " + AdminOrderQueryService.MAX_PAGE_SIZE);
        }
        String[] sortParts = sort == null || sort.isBlank() ? new String[]{"totalSpent"} : sort.split(",");
        String sortField = sortParts[0].trim();
        if (!CUSTOMER_SORTS.contains(sortField)) {
            throw new BusinessException("INVALID_SORT", "sort must be one of " + CUSTOMER_SORTS);
        }
        boolean ascending = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim());

        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, null);
        Map<Long, CustomerAccumulator> rows = customerRows(data, range);
        Lookup<UserSummary> users = resolver.users(rows.keySet());

        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        List<CustomerRevenue> all = rows.entrySet().stream()
                .map(e -> toCustomerRevenue(e.getKey(), e.getValue(), users.get(e.getKey())))
                .filter(c -> needle == null || matches(c, needle))
                .sorted(customerComparator(sortField, ascending))
                .toList();
        int fromIndex = (int) Math.min((long) page * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil(all.size() / (double) size);
        return new PageResponse<>(all.subList(fromIndex, toIndex), page, size, all.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public CustomerRevenueDetailResponse customerDetail(Long userId, String from, String to) {
        DateRange range = time.dateRange(from, to);
        Dataset data = load(range, userId);
        CustomerAccumulator acc = customerRows(data, range).getOrDefault(userId, new CustomerAccumulator());

        Set<Long> orderIds = new LinkedHashSet<>();
        data.ordersCreatedIn(range).stream().filter(o -> userId.equals(o.getUserId())).forEach(o -> orderIds.add(o.getId()));
        data.payments.forEach(p -> orderIds.add(p.orderId()));
        List<LockerOrder> orders = orderIds.stream()
                .map(data.orders::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LockerOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Lookup<UserSummary> users = resolver.users(List.of(userId));
        Lookup<LockerInfo> lockers = resolver.lockers(orders.stream().map(this::lockerOf).toList());
        Lookup<OrderPaymentSummary> summaries = resolver.paymentSummaries(orders.stream().map(LockerOrder::getId).toList());
        Map<Long, List<CollectedPayment>> paymentsByOrder = data.paymentsByOrder();
        Map<Long, BigDecimal> refundsByOrder = data.refunds.stream()
                .collect(Collectors.groupingBy(CollectedRefund::orderId,
                        Collectors.reducing(BigDecimal.ZERO, r -> nz(r.amount()), BigDecimal::add)));

        List<CustomerDetailOrder> rows = orders.stream()
                .map(o -> {
                    List<CollectedPayment> payments = paymentsByOrder.getOrDefault(o.getId(), List.of());
                    LockerInfo locker = lockers.get(lockerOf(o));
                    OrderPaymentSummary summary = summaries.get(o.getId());
                    return new CustomerDetailOrder(
                            o.getId(), o.getOrderCode(), o.getType(), o.getStatus(), o.getPaymentStatus(),
                            lockerOf(o), locker == null ? null : locker.code(), locker == null ? null : locker.name(),
                            o.getTotalPrice(), o.getExtraFee(), o.getDiscount(),
                            sumPayments(payments), refundsByOrder.getOrDefault(o.getId(), BigDecimal.ZERO),
                            summary == null ? (summaries.available() ? BigDecimal.ZERO : null) : summary.paidAmount(),
                            payments.stream().map(p -> upper(p.method())).distinct().toList(),
                            summary == null ? null : summary.lastPaidMethod(),
                            summary == null ? null : summary.lastPaidAt(),
                            o.getCreatedAt(), o.getPaidAt(), o.getCompletedAt());
                })
                .toList();

        UserSummary user = users.get(userId);
        CustomerRevenue totals = toCustomerRevenue(userId, acc, user);
        return new CustomerRevenueDetailResponse(
                range.from(), range.to(), userId,
                user == null ? null : user.fullName(),
                user == null ? null : user.phoneNumber(),
                user == null ? null : user.email(),
                user == null ? null : user.status(),
                totals.orderCount(), totals.paidOrderCount(), totals.totalSpent(), totals.refundAmount(),
                totals.netSpent(), totals.averageOrderValue(),
                orders.stream().map(LockerOrder::getCreatedAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null),
                orders.stream().map(LockerOrder::getCreatedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null),
                rows);
    }

    // ------------------------------------------------------------------ rules

    /**
     * Phần tiền thu trong kỳ bù cho phí quá hạn. Phí quá hạn được cộng vào extraFee/totalPrice
     * lúc hoàn tất đơn, nên coi tiền thu vượt giá gốc (totalPrice − extraFee) là phí quá hạn:
     * overtimeAllTime = clamp(paidAllTime − base, 0, extraFee); phần trong kỳ = min(overtimeAllTime, inRange).
     */
    static BigDecimal overtimePortion(LockerOrder order, BigDecimal inRange, BigDecimal paidAllTime) {
        if (order == null || order.getExtraFee() == null || order.getExtraFee().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal extra = order.getExtraFee();
        BigDecimal base = nz(order.getTotalPrice()).subtract(extra).max(BigDecimal.ZERO);
        BigDecimal paid = paidAllTime == null ? inRange : paidAllTime.max(inRange);
        BigDecimal overtimeAllTime = paid.subtract(base).max(BigDecimal.ZERO).min(extra);
        return overtimeAllTime.min(inRange);
    }

    static String serviceType(LockerOrder order) {
        if (order == null) {
            return "UNKNOWN";
        }
        String type = upper(order.getType());
        return switch (type) {
            case "SEND", "RENTAL", "DRONE_DELIVERY", "STORAGE" -> type;
            default -> "OTHER";
        };
    }

    // ------------------------------------------------------------------ internals

    private RevenuePeriod period(Dataset data, DateRange range) {
        List<CollectedPayment> payments = data.paymentsIn(range);
        BigDecimal revenue = sumPayments(payments);
        BigDecimal refunds = sumRefunds(data.refundsIn(range));
        long paidOrders = distinctOrders(payments);
        List<LockerOrder> created = data.ordersCreatedIn(range);
        long completed = data.ordersLoaded.stream()
                .filter(o -> "COMPLETED".equalsIgnoreCase(o.getStatus()))
                .filter(o -> within(range, time.businessDate(o.getCompletedAt())))
                .count();
        long canceled = created.stream().filter(o -> "CANCELED".equalsIgnoreCase(o.getStatus())).count();
        return new RevenuePeriod(range.from(), range.to(), revenue, refunds, revenue.subtract(refunds),
                created.size(), paidOrders, payments.size(), completed, canceled, average(revenue, paidOrders));
    }

    private RevenueChanges changes(RevenuePeriod cur, RevenuePeriod prev) {
        return new RevenueChanges(
                pct(cur.totalRevenue(), prev.totalRevenue()),
                pct(cur.refundAmount(), prev.refundAmount()),
                pct(cur.netRevenue(), prev.netRevenue()),
                pct(BigDecimal.valueOf(cur.orderCount()), BigDecimal.valueOf(prev.orderCount())),
                pct(BigDecimal.valueOf(cur.paidOrderCount()), BigDecimal.valueOf(prev.paidOrderCount())),
                cur.averageOrderValue() == null || prev.averageOrderValue() == null
                        ? null : pct(cur.averageOrderValue(), prev.averageOrderValue()));
    }

    private void accumulate(Dataset data, DateRange range, Function<LockerOrder, Accumulator> rowOf) {
        data.paymentsByOrder().forEach((orderId, payments) -> {
            Accumulator row = rowOf.apply(data.orders.get(orderId));
            row.revenue = row.revenue.add(sumPayments(payments));
            row.paidOrders.add(orderId);
            row.paymentCount += payments.size();
        });
        data.refunds.forEach(r -> {
            Accumulator row = rowOf.apply(data.orders.get(r.orderId()));
            row.refund = row.refund.add(nz(r.amount()));
        });
        data.ordersCreatedIn(range).forEach(o -> rowOf.apply(o).orderCount++);
    }

    private Map<Long, CustomerAccumulator> customerRows(Dataset data, DateRange range) {
        Map<Long, CustomerAccumulator> rows = new LinkedHashMap<>();
        data.payments.forEach(p -> {
            LockerOrder order = data.orders.get(p.orderId());
            Long customerId = order != null ? order.getUserId() : p.userId();
            if (customerId == null) {
                return;
            }
            CustomerAccumulator row = rows.computeIfAbsent(customerId, k -> new CustomerAccumulator());
            row.revenue = row.revenue.add(nz(p.amount()));
            row.paidOrders.add(p.orderId());
            row.lastPaidAt = maxTime(row.lastPaidAt, p.paidAt());
        });
        data.refunds.forEach(r -> {
            LockerOrder order = data.orders.get(r.orderId());
            Long customerId = order != null ? order.getUserId() : r.userId();
            if (customerId == null) {
                return;
            }
            CustomerAccumulator row = rows.computeIfAbsent(customerId, k -> new CustomerAccumulator());
            row.refund = row.refund.add(nz(r.amount()));
        });
        data.ordersCreatedIn(range).forEach(o -> {
            if (o.getUserId() == null) {
                return;
            }
            CustomerAccumulator row = rows.computeIfAbsent(o.getUserId(), k -> new CustomerAccumulator());
            row.orderCount++;
            row.lastOrderAt = maxTime(row.lastOrderAt, o.getCreatedAt());
        });
        return rows;
    }

    private CustomerRevenue toCustomerRevenue(Long userId, CustomerAccumulator acc, UserSummary user) {
        return new CustomerRevenue(
                userId,
                user == null ? null : user.fullName(),
                user == null ? null : user.phoneNumber(),
                user == null ? null : user.email(),
                acc.orderCount,
                acc.paidOrders.size(),
                acc.revenue,
                acc.refund,
                acc.revenue.subtract(acc.refund),
                average(acc.revenue, acc.paidOrders.size()),
                acc.lastOrderAt,
                acc.lastPaidAt);
    }

    private static Comparator<CustomerRevenue> customerComparator(String field, boolean ascending) {
        Comparator<CustomerRevenue> comparator = switch (field) {
            case "netSpent" -> Comparator.comparing(CustomerRevenue::netSpent);
            case "orderCount" -> Comparator.comparingLong(CustomerRevenue::orderCount);
            case "paidOrderCount" -> Comparator.comparingLong(CustomerRevenue::paidOrderCount);
            case "averageOrderValue" -> Comparator.comparing(CustomerRevenue::averageOrderValue,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            case "lastOrderAt" -> Comparator.comparing(CustomerRevenue::lastOrderAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            case "lastPaidAt" -> Comparator.comparing(CustomerRevenue::lastPaidAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            default -> Comparator.comparing(CustomerRevenue::totalSpent);
        };
        comparator = ascending ? comparator : comparator.reversed();
        return comparator.thenComparing(CustomerRevenue::userId);
    }

    private static boolean matches(CustomerRevenue c, String needle) {
        return String.valueOf(c.userId()).equals(needle)
                || (c.fullName() != null && c.fullName().toLowerCase(Locale.ROOT).contains(needle))
                || (c.phoneNumber() != null && c.phoneNumber().contains(needle))
                || (c.email() != null && c.email().toLowerCase(Locale.ROOT).contains(needle));
    }

    private Dataset load(DateRange window, Long userId) {
        LocalDateTime start = time.startOfDay(window.from());
        LocalDateTime end = time.startOfDay(window.to().plusDays(1));
        CollectedPayments collected;
        try {
            collected = resolver.collected(
                    start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), userId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Revenue report cannot load collected payments: {}", ex.getMessage());
            throw new BusinessException("PAYMENT_DATA_UNAVAILABLE",
                    "Không lấy được dữ liệu thanh toán, thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);
        }
        List<LockerOrder> loaded = new ArrayList<>(orderRepository.findAll(OrderSpecifications.all(
                OrderSpecifications.createdOrCompletedBetween(start, end), OrderSpecifications.user(userId))));
        Map<Long, LockerOrder> orders = loaded.stream().collect(Collectors.toMap(LockerOrder::getId, o -> o, (a, b) -> a, HashMap::new));
        Set<Long> missing = new HashSet<>();
        safe(collected.payments()).forEach(p -> addIfMissing(missing, orders, p.orderId()));
        safe(collected.refunds()).forEach(r -> addIfMissing(missing, orders, r.orderId()));
        if (!missing.isEmpty()) {
            orderRepository.findAllById(missing).forEach(o -> orders.put(o.getId(), o));
        }
        Map<Long, BigDecimal> paidTotals = new HashMap<>();
        safe(collected.orderPaidTotals()).forEach(t -> paidTotals.put(t.orderId(), nz(t.paidAmount())));
        return new Dataset(window, safe(collected.payments()).stream().filter(p -> p.paidAt() != null).toList(),
                safe(collected.refunds()).stream().filter(r -> r.refundedAt() != null).toList(),
                paidTotals, orders, loaded);
    }

    private static void addIfMissing(Set<Long> missing, Map<Long, LockerOrder> orders, Long orderId) {
        if (orderId != null && !orders.containsKey(orderId)) {
            missing.add(orderId);
        }
    }

    private Long lockerOf(LockerOrder order) {
        if (order == null) {
            return null;
        }
        return order.getLockerId() != null ? order.getLockerId() : order.getDestinationLockerId();
    }

    private static boolean covers(DateRange outer, DateRange inner) {
        return !inner.from().isBefore(outer.from()) && !inner.to().isAfter(outer.to());
    }

    private static boolean within(DateRange range, LocalDate date) {
        return date != null && !date.isBefore(range.from()) && !date.isAfter(range.to());
    }

    private static BigDecimal sumPayments(Collection<CollectedPayment> payments) {
        return payments.stream().map(p -> nz(p.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumRefunds(Collection<CollectedRefund> refunds) {
        return refunds.stream().map(r -> nz(r.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long distinctOrders(Collection<CollectedPayment> payments) {
        return payments.stream().map(CollectedPayment::orderId).distinct().count();
    }

    static BigDecimal average(BigDecimal revenue, long orders) {
        return orders == 0 ? null : revenue.divide(BigDecimal.valueOf(orders), 0, RoundingMode.HALF_UP);
    }

    static BigDecimal share(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal pct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String upper(String value) {
        return value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime maxTime(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    private static class Accumulator {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        Set<Long> paidOrders = new HashSet<>();
        long paymentCount;
        long orderCount;

        boolean isEmpty() {
            return revenue.signum() == 0 && refund.signum() == 0 && orderCount == 0 && paidOrders.isEmpty();
        }
    }

    private static class CustomerAccumulator extends Accumulator {
        LocalDateTime lastOrderAt;
        LocalDateTime lastPaidAt;
    }

    private final class Dataset {
        final DateRange window;
        final List<CollectedPayment> payments;
        final List<CollectedRefund> refunds;
        final Map<Long, BigDecimal> paidTotals;
        final Map<Long, LockerOrder> orders;
        final List<LockerOrder> ordersLoaded;

        Dataset(DateRange window, List<CollectedPayment> payments, List<CollectedRefund> refunds,
                Map<Long, BigDecimal> paidTotals, Map<Long, LockerOrder> orders, List<LockerOrder> ordersLoaded) {
            this.window = window;
            this.payments = payments;
            this.refunds = refunds;
            this.paidTotals = paidTotals;
            this.orders = orders;
            this.ordersLoaded = ordersLoaded;
        }

        List<CollectedPayment> paymentsIn(DateRange range) {
            return payments.stream().filter(p -> within(range, time.businessDate(p.paidAt()))).toList();
        }

        List<CollectedRefund> refundsIn(DateRange range) {
            return refunds.stream().filter(r -> within(range, time.businessDate(r.refundedAt()))).toList();
        }

        List<LockerOrder> ordersCreatedIn(DateRange range) {
            return ordersLoaded.stream().filter(o -> within(range, time.businessDate(o.getCreatedAt()))).toList();
        }

        Map<Long, List<CollectedPayment>> paymentsByOrder() {
            return payments.stream().collect(Collectors.groupingBy(CollectedPayment::orderId, LinkedHashMap::new, Collectors.toList()));
        }
    }
}
