package com.huynqb.laundrylocker.payment.dto;

import java.math.BigDecimal;

public record WalletResponse(Long userId, BigDecimal balance, String currency) {
}
