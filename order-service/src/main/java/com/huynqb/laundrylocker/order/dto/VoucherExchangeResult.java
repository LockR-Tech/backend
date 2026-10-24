package com.huynqb.laundrylocker.order.dto;

/// Kết quả một lần đổi điểm lấy voucher: voucher vừa vào "Ưu đãi của tôi", số điểm đã trừ và số dư còn lại.
public record VoucherExchangeResult(PromotionView voucher, int pointsSpent, int balance) {
}
