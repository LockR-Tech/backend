package com.huynqb.laundrylocker.order.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/// Voucher đổi bằng điểm cho app: mọi field của {@link PromotionView} (có `pointsCost`) + `held` = khách đang
/// giữ voucher này chưa dùng (dùng xong mới đổi tiếp được).
public record ExchangeableVoucherView(@JsonUnwrapped PromotionView promotion, boolean held) {
}
