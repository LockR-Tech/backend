package com.huynqb.laundrylocker.loyalty.controller;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.loyalty.dto.AdjustPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.LoyaltyAccountResponse;
import com.huynqb.laundrylocker.loyalty.dto.RedeemPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.RedeemStampRequest;
import com.huynqb.laundrylocker.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/// Đường của khách lấy người dùng từ X-User-Id; thao tác theo userId tuỳ ý chỉ ADMIN.
@ExtendWith(MockitoExtension.class)
class LoyaltyControllerAccessTest {

    @Mock LoyaltyService loyaltyService;
    @InjectMocks LoyaltyController controller;

    @Test
    void accountByIdIsSelfOrAdmin() {
        when(loyaltyService.getByUser(7L)).thenReturn(new LoyaltyAccountResponse(1L, 7L, 10, 0, "BRONZE", 10));

        assertEquals(7L, controller.getByUser(7L, 7L, "CUSTOMER").data().userId());
        assertEquals(7L, controller.getByUser(7L, 1L, "ADMIN").data().userId());
        BusinessException error = assertThrows(BusinessException.class, () -> controller.getByUser(7L, 9L, "CUSTOMER"));
        assertEquals("LOYALTY_FORBIDDEN", error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertThrows(BusinessException.class, () -> controller.getByUser(7L, null, null));
    }

    @Test
    void redeemUsesCallerNotBodyUser() {
        controller.redeemPoints(7L, new RedeemPointsRequest(99L, 50, "Đổi quà"));
        controller.redeemStamp(7L, new RedeemStampRequest(99L, 2, "Giặt miễn phí"));

        verify(loyaltyService).redeemPoints(new RedeemPointsRequest(7L, 50, "Đổi quà"));
        verify(loyaltyService).redeemStamp(new RedeemStampRequest(7L, 2, "Giặt miễn phí"));
    }

    @Test
    void redeemNeedsSignedInCaller() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.redeemPoints(null, new RedeemPointsRequest(7L, 50, null)));

        assertEquals("AUTH_REQUIRED", error.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verify(loyaltyService, never()).redeemPoints(any());
    }

    @Test
    void redeemPreviewIsForTheSignedInCallerOnly() {
        when(loyaltyService.redeemPreview(7L, new java.math.BigDecimal("15000"))).thenReturn(
                new com.huynqb.laundrylocker.loyalty.dto.RedeemPreviewResponse(
                        true, 500, 100, 50, 10, 75, new java.math.BigDecimal("7500")));

        assertEquals(75, controller.redeemPreview(7L, new java.math.BigDecimal("15000")).data().maxPoints());
        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.redeemPreview(null, new java.math.BigDecimal("15000")));
        assertEquals("AUTH_REQUIRED", error.getCode());
    }

    @Test
    void pointAdjustmentIsAdminOnly() {
        AdjustPointsRequest request = new AdjustPointsRequest(7L, null, 1000, null);

        assertEquals("LOYALTY_FORBIDDEN", assertThrows(BusinessException.class,
                () -> controller.adjustPoints("CUSTOMER", request)).getCode());
        verify(loyaltyService, never()).adjustPoints(any());

        controller.adjustPoints("ADMIN", request);
        verify(loyaltyService).adjustPoints(request);
        verify(loyaltyService, never()).getByUser(anyLong());
    }
}
