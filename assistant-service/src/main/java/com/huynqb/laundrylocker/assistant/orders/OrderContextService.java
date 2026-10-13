package com.huynqb.laundrylocker.assistant.orders;

import com.huynqb.laundrylocker.assistant.orders.OrderDocumentFormatter.Entry;
import com.huynqb.laundrylocker.assistant.orders.OrderDocumentFormatter.Snapshot;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.BoxInfo;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.IncomingOrder;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.LockerInfo;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.MyOrder;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ContextDocument;
import com.huynqb.laundrylocker.assistant.query.OrderIntent;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/// Dữ liệu đơn của chính người hỏi cho trợ lý: đơn họ tạo + đơn người khác gửi cho họ, tải song song
/// với hạn chờ ngắn. Không bao giờ ném lỗi — order-service lỗi/chậm thì trợ lý trả lời như khi không có
/// dữ liệu đơn (chỉ dựa vào tài liệu).
@Slf4j
@Service
public class OrderContextService {

    /// Lấy dư đơn người khác gửi để vẫn chọn được đơn gần nhất sau khi gộp với đơn tự tạo.
    static final int INCOMING_LIMIT = 30;
    private static final Duration ORDERS_DEADLINE = Duration.ofMillis(3500);
    private static final Duration LOCKERS_DEADLINE = Duration.ofSeconds(2);
    /// locker-service nhận tối đa 100 id mỗi lần.
    private static final int MAX_LOOKUP_IDS = 100;

    private final OrderServiceClient orders;
    private final LockerLookupClient lockers;
    private final OrderDocumentFormatter formatter;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public OrderContextService(OrderServiceClient orders, LockerLookupClient lockers) {
        this(orders, lockers, ZoneId.systemDefault(), Clock.systemUTC());
    }

    OrderContextService(OrderServiceClient orders, LockerLookupClient lockers, ZoneId storageZone, Clock clock) {
        this.orders = orders;
        this.lockers = lockers;
        this.formatter = new OrderDocumentFormatter(storageZone, BusinessTime.DEFAULT_BUSINESS_ZONE);
        this.clock = clock;
    }

    /// Tài liệu "Đơn hàng của bạn" cho `userId` (lấy từ header X-User-Id). Rỗng khi không tải được
    /// cả hai danh sách.
    public Optional<ContextDocument> load(long userId, String question) {
        try {
            CompletableFuture<List<MyOrder>> mineFuture = async(() -> data(orders.myOrders(userId)));
            CompletableFuture<List<IncomingOrder>> incomingFuture =
                    async(() -> data(orders.incoming(userId, false, INCOMING_LIMIT)));
            List<MyOrder> mine = await(mineFuture, ORDERS_DEADLINE, "my-orders");
            List<IncomingOrder> incoming = await(incomingFuture, ORDERS_DEADLINE, "incoming");
            if (mine == null && incoming == null) {
                return Optional.empty();
            }
            Set<String> mentioned = OrderIntent.orderCodes(question);
            List<Entry> selected = OrderDocumentFormatter.select(mine, incoming, mentioned);
            LockerLookup lookup = lookupLockers(selected);
            Snapshot snapshot = new Snapshot(
                    mine, incoming, incoming != null && incoming.size() >= INCOMING_LIMIT, selected,
                    lookup.lockers(), lookup.boxNumbers(), mentioned);
            return Optional.of(formatter.format(snapshot, clock.instant()));
        } catch (RuntimeException ex) {
            log.warn("Could not build the order context for the assistant: {}", ex.toString());
            return Optional.empty();
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private record LockerLookup(Map<Long, LockerInfo> lockers, Map<Long, Integer> boxNumbers) {
    }

    /// Tên/địa chỉ tủ và số ô cho đơn tự tạo (`OrderResponse` chỉ có id); lỗi thì bỏ qua phần này.
    private LockerLookup lookupLockers(List<Entry> selected) {
        Set<Long> lockerIds = new LinkedHashSet<>();
        Set<Long> boxIds = new LinkedHashSet<>();
        for (Entry entry : selected) {
            MyOrder order = entry.mine();
            if (order == null) {
                continue;
            }
            if (order.lockerId() != null) {
                lockerIds.add(order.lockerId());
            }
            Long boxId = order.receiveBoxId() != null ? order.receiveBoxId() : order.sendBoxId();
            if (boxId != null) {
                boxIds.add(boxId);
            }
        }
        CompletableFuture<List<LockerInfo>> lockerFuture = lockerIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : async(() -> data(lockers.lockers(limit(lockerIds))));
        CompletableFuture<List<BoxInfo>> boxFuture = boxIds.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : async(() -> data(lockers.boxes(limit(boxIds))));
        Map<Long, LockerInfo> lockerMap = new HashMap<>();
        Map<Long, Integer> boxMap = new HashMap<>();
        List<LockerInfo> lockerList = await(lockerFuture, LOCKERS_DEADLINE, "locker lookup");
        if (lockerList != null) {
            lockerList.stream().filter(locker -> locker != null && locker.id() != null)
                    .forEach(locker -> lockerMap.put(locker.id(), locker));
        }
        List<BoxInfo> boxList = await(boxFuture, LOCKERS_DEADLINE, "box lookup");
        if (boxList != null) {
            boxList.stream().filter(box -> box != null && box.id() != null && box.boxNumber() != null)
                    .forEach(box -> boxMap.put(box.id(), box.boxNumber()));
        }
        return new LockerLookup(lockerMap, boxMap);
    }

    private <T> CompletableFuture<T> async(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, executor);
    }

    /// Kết quả hoặc null khi lỗi/quá hạn chờ (đã ghi log).
    private static <T> T await(CompletableFuture<T> future, Duration deadline, String what) {
        try {
            return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return null;
        } catch (Exception ex) {
            future.cancel(true);
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            log.warn("Assistant order context: {} unavailable: {}", what, cause.toString());
            return null;
        }
    }

    private static <T> List<T> data(ApiResponse<List<T>> response) {
        if (response == null || !response.success()) {
            throw new IllegalStateException("unsuccessful response" + (response == null ? "" : " " + response.code()));
        }
        return response.data() == null ? List.of() : response.data();
    }

    private static List<Long> limit(Set<Long> ids) {
        return ids.stream().limit(MAX_LOOKUP_IDS).toList();
    }
}
