package com.huynqb.laundrylocker.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/// Khai báo kiện hàng của đơn: mô tả, cân nặng, kích thước, loại hàng, giá trị khai báo, dễ vỡ và
/// lúc người gửi cam kết không có hàng cấm bay. Đơn drone khai đủ; đơn tủ thường thường chỉ có
/// mô tả hoặc cân nặng ước tính. Mọi trường đều có thể null. Dựng bằng `OrderDetailViews.parcel`.
public record OrderParcelView(
        String description,
        /// Cân nặng khai báo của đơn drone, gram.
        Integer weightGrams,
        /// Cân nặng ước tính/thực tế của đơn tủ, theo `weightUnit`.
        BigDecimal actualWeight,
        String weightUnit,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        String category,
        BigDecimal declaredValue,
        Boolean fragile,
        LocalDateTime prohibitedItemsDeclaredAt) {
}
