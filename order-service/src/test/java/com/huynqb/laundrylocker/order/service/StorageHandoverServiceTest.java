package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.order.dto.StorageHandoverRequest;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import com.huynqb.laundrylocker.order.model.StorageHandover;
import com.huynqb.laundrylocker.order.repository.LockerOrderRepository;
import com.huynqb.laundrylocker.order.repository.StorageHandoverRepository;
import com.huynqb.laundrylocker.order.service.AdminReferenceResolver.Lookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StorageHandoverServiceTest {

    private static final long ADMIN = 9L;
    private static final MediaUpload PHOTO = new MediaUpload("lockr/storage-handovers/9/abc", 1L, "sig", "jpg", 1000L, 800, 600);

    @Mock private LockerOrderRepository orderRepository;
    @Mock private StorageHandoverRepository handoverRepository;
    @Mock private AdminReferenceResolver resolver;
    @Mock private CloudinaryMediaStorage mediaStorage;
    @Mock private OrderService orderService;
    @InjectMocks private StorageHandoverService service;

    private LockerOrder order;

    @BeforeEach
    void setUp() {
        order = new LockerOrder();
        order.setId(1L);
        order.setUserId(44L);
        order.setStatus("EXPIRED");
        order.setTotalPrice(new BigDecimal("50000"));
        order.setReceiverName("Trần Bình");
        order.setReceiverPhone("0907654321");
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(resolver.users(anyCollection())).thenReturn(new Lookup<>(
                Map.of(44L, new UserSummary(44L, "a@b.c", "+84 901 234 567", "Thùy Dương", "ACTIVE")), true));
        paid("50000");
        when(mediaStorage.verify(PHOTO, MediaPurpose.STORAGE_HANDOVER, ADMIN)).thenReturn(
                new VerifiedMedia(PHOTO.publicId(), "https://res.cloudinary.com/x/abc.jpg", "jpg", 1000L, 800, 600));
    }

    private void paid(String amount) {
        when(resolver.paymentSummaries(anyCollection())).thenReturn(new Lookup<>(Map.of(1L, new OrderPaymentSummary(
                1L, 1, 1L, "VNPAY", "SUCCESS", new BigDecimal(amount), null, new BigDecimal(amount), "VNPAY", null,
                BigDecimal.ZERO, null, null)), true));
    }

    private static StorageHandoverRequest request(String type, String last4) {
        return new StorageHandoverRequest(type, last4, null, null, PHOTO, null, null, null);
    }

    @Test
    void senderWithMatchingLast4IsHandedOverAndRecorded() {
        service.handOver(1L, request("sender", "4567"), ADMIN);

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(orderService).checkout(eq(1L), eq(ADMIN), note.capture());
        assertTrue(note.getValue().contains("khách gửi Thùy Dương (SĐT ***4567)"), note.getValue());

        ArgumentCaptor<StorageHandover> saved = ArgumentCaptor.forClass(StorageHandover.class);
        verify(handoverRepository).save(saved.capture());
        assertEquals("SENDER", saved.getValue().getRecipientType());
        assertEquals("84901234567", saved.getValue().getRecipientPhone());
        assertEquals("https://res.cloudinary.com/x/abc.jpg", saved.getValue().getPhotoUrl());
        assertEquals(ADMIN, saved.getValue().getHandedOverByUserId());
        assertNull(saved.getValue().getCollectedAmount());
    }

    @Test
    void receiverPhoneOnOrderIsAccepted() {
        service.handOver(1L, request("RECEIVER", "4321"), ADMIN);

        ArgumentCaptor<StorageHandover> saved = ArgumentCaptor.forClass(StorageHandover.class);
        verify(handoverRepository).save(saved.capture());
        assertEquals("Trần Bình", saved.getValue().getRecipientName());
    }

    @Test
    void wrongLast4IsRejectedBeforePhotoOrCheckout() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handOver(1L, request("RECEIVER", "4567"), ADMIN));

        assertEquals("HANDOVER_PHONE_MISMATCH", ex.getCode());
        verify(mediaStorage, never()).verify(any(), any(), anyLong());
        verify(orderService, never()).checkout(anyLong(), anyLong(), anyString());
    }

    @Test
    void delegateWithoutPhoneOnOrderAsksForOther() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handOver(1L, request("DELEGATE", "1234"), ADMIN));

        assertEquals("HANDOVER_PHONE_UNKNOWN", ex.getCode());
    }

    @Test
    void orderNoLongerInStorageIsRejected() {
        order.setStatus("COMPLETED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handOver(1L, request("SENDER", "4567"), ADMIN));

        assertEquals("HANDOVER_ORDER_NOT_IN_STORAGE", ex.getCode());
    }

    @Test
    void outstandingMustBeDeclaredCollected() {
        paid("20000");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handOver(1L, request("SENDER", "4567"), ADMIN));
        assertEquals("HANDOVER_OUTSTANDING_NOT_COLLECTED", ex.getCode());

        BusinessException tooLittle = assertThrows(BusinessException.class, () -> service.handOver(1L,
                new StorageHandoverRequest("SENDER", "4567", null, null, PHOTO, new BigDecimal("10000"), "CASH", null), ADMIN));
        assertEquals("HANDOVER_OUTSTANDING_NOT_COLLECTED", tooLittle.getCode());
        verify(orderService, never()).checkout(anyLong(), anyLong(), anyString());
    }

    @Test
    void collectedOutstandingIsWrittenToHistoryAndRecord() {
        paid("20000");

        service.handOver(1L, new StorageHandoverRequest(
                "SENDER", "4567", null, null, PHOTO, new BigDecimal("30000"), "cash", "Khách trả tiền mặt"), ADMIN);

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(orderService).checkout(eq(1L), eq(ADMIN), note.capture());
        assertTrue(note.getValue().contains("đã thu 30.000 ₫ tiền mặt"), note.getValue());
        assertTrue(note.getValue().endsWith("ghi chú: Khách trả tiền mặt"), note.getValue());

        ArgumentCaptor<StorageHandover> saved = ArgumentCaptor.forClass(StorageHandover.class);
        verify(handoverRepository).save(saved.capture());
        assertEquals("CASH", saved.getValue().getCollectionMethod());
        assertEquals(new BigDecimal("30000"), saved.getValue().getCollectedAmount());
    }

    @Test
    void unpaidOrderWithoutPaymentServiceStillCountsAsOwed() {
        when(resolver.paymentSummaries(anyCollection())).thenReturn(new Lookup<>(Map.of(), false));
        order.setPaymentStatus("UNPAID");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.handOver(1L, request("SENDER", "4567"), ADMIN));

        assertEquals("HANDOVER_OUTSTANDING_NOT_COLLECTED", ex.getCode());
    }

    @Test
    void otherRecipientNeedsNamePhoneAndReason() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.handOver(1L,
                new StorageHandoverRequest("OTHER", null, "Lê Văn C", "0912345678", PHOTO, null, null, null), ADMIN));
        assertEquals("HANDOVER_OTHER_RECIPIENT_INCOMPLETE", ex.getCode());

        service.handOver(1L, new StorageHandoverRequest(
                "OTHER", null, "Lê Văn C", "0912 345 678", PHOTO, null, null, "Khách gọi điện nhờ lấy hộ"), ADMIN);

        ArgumentCaptor<StorageHandover> saved = ArgumentCaptor.forClass(StorageHandover.class);
        verify(handoverRepository).save(saved.capture());
        assertEquals("0912345678", saved.getValue().getRecipientPhone());
        assertEquals("OTHER", saved.getValue().getRecipientType());
    }
}
