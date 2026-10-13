package com.huynqb.laundrylocker.assistant.orders;

import com.huynqb.laundrylocker.assistant.orders.OrderDtos.BoxInfo;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.IncomingOrder;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.LockerInfo;
import com.huynqb.laundrylocker.assistant.orders.OrderDtos.MyOrder;
import com.huynqb.laundrylocker.assistant.provider.AnswerGenerator.ContextDocument;
import com.huynqb.laundrylocker.common.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderContextServiceTest {

    private final OrderServiceClient orders = mock(OrderServiceClient.class);
    private final LockerLookupClient lockers = mock(LockerLookupClient.class);
    private final OrderContextService service = new OrderContextService(orders, lockers, ZoneOffset.UTC,
            Clock.fixed(Instant.parse("2026-10-13T07:05:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void loadsOnlyTheCallersOrdersAndLooksUpLockers() {
        when(orders.myOrders(42L)).thenReturn(ApiResponse.ok(List.of(myOrder())));
        when(orders.incoming(42L, false, OrderContextService.INCOMING_LIMIT)).thenReturn(ApiResponse.ok(List.of()));
        when(lockers.lockers(anyCollection())).thenReturn(ApiResponse.ok(List.of(new LockerInfo(3L, "Tủ A", "Toà A"))));
        when(lockers.boxes(anyCollection())).thenReturn(ApiResponse.ok(List.of(new BoxInfo(31L, 12))));

        ContextDocument document = service.load(42L, "đơn của tôi").orElseThrow();

        verify(orders).myOrders(42L);
        verify(orders).incoming(42L, false, OrderContextService.INCOMING_LIMIT);
        verifyNoMoreInteractions(orders);
        assertTrue(document.content().contains("- Tủ: Tủ A — Toà A · ô số 12"));
        assertTrue(document.content().contains("chưa có đơn nào người khác gửi cho bạn"));
    }

    @Test
    void bothListsFailingMeansNoDocument() {
        when(orders.myOrders(anyLong())).thenThrow(new RuntimeException("503 order-service"));
        when(orders.incoming(anyLong(), anyBoolean(), anyInt())).thenThrow(new RuntimeException("503 order-service"));

        assertEquals(Optional.empty(), service.load(42L, "đơn của tôi"));
    }

    @Test
    void oneListFailingIsReportedInTheDocument() {
        when(orders.myOrders(anyLong())).thenReturn(ApiResponse.ok(List.of(myOrder())));
        when(orders.incoming(anyLong(), anyBoolean(), anyInt())).thenThrow(new RuntimeException("user-service down"));
        when(lockers.lockers(anyCollection())).thenThrow(new RuntimeException("locker-service down"));
        when(lockers.boxes(anyCollection())).thenThrow(new RuntimeException("locker-service down"));

        String text = service.load(42L, "đơn của tôi").orElseThrow().content();

        assertTrue(text.contains("không tải được danh sách hàng người khác gửi cho bạn lúc này"));
        assertTrue(text.contains("ORD-20261012-AB12"));
        assertFalse(text.contains("- Tủ:"), "locker line omitted when the lookup fails");
    }

    @Test
    void unsuccessfulResponseCountsAsUnavailable() {
        when(orders.myOrders(anyLong())).thenReturn(new ApiResponse<>(false, "ERR", "x", null, null));
        when(orders.incoming(anyLong(), anyBoolean(), anyInt())).thenReturn(ApiResponse.ok(List.<IncomingOrder>of()));

        String text = service.load(42L, "đơn của tôi").orElseThrow().content();

        assertTrue(text.contains("Không tải được danh sách đơn bạn đã tạo lúc này"));
    }

    @Test
    void mentionedOrderCodeThatIsNotTheCallersIsReportedAsNotFound() {
        when(orders.myOrders(anyLong())).thenReturn(ApiResponse.ok(List.<MyOrder>of()));
        when(orders.incoming(anyLong(), anyBoolean(), anyInt())).thenReturn(ApiResponse.ok(List.<IncomingOrder>of()));

        String text = service.load(42L, "Đơn ORD-20261001-OTHER của người khác thế nào?").orElseThrow().content();

        assertTrue(text.contains("Không thấy đơn ORD-20261001-OTHER trong các đơn của người hỏi"));
    }

    private static MyOrder myOrder() {
        return new MyOrder(11L, "ORD-20261012-AB12", "SEND", "STORING", null, 3L, 31L, null, "PAID",
                new BigDecimal("15000"), new BigDecimal("15000"), BigDecimal.ZERO, null, false,
                LocalDateTime.parse("2026-10-14T09:12:00"), null, LocalDateTime.parse("2026-10-12T02:12:00"),
                "Nguyễn Văn A", "0912345789", null, "PICKUP", null);
    }
}
