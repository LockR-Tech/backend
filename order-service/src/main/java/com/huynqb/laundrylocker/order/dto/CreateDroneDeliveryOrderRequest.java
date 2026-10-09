package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDroneDeliveryOrderRequest(
        @NotNull Long sourceLockerId,
        @NotNull Long destinationLockerId,
        Long preferredBoxId,
        String description,
        @NotNull Integer parcelWeightGrams,
        /// Không còn dùng: tiền thu ở bước thanh toán, đơn không lưu phương thức dự kiến.
        /// Giữ lại để client cũ còn gửi field này không bị từ chối.
        String paymentMethod,
        String fulfillmentMode,
        /// Ô DRONE người gửi chọn tại tủ gửi. Bỏ trống thì hệ thống tự lấy một ô DRONE
        /// còn trống của tủ gửi.
        Long sourceBoxId,
        /// Người nhận tại tủ đích. Bỏ trống cả hai ⇒ người đặt tự nhận.
        @Size(max = 20) String receiverPhone,
        @Size(max = 120) String receiverName,
        /// Email nhận mã mở ô, tuỳ chọn. Bỏ trống thì dùng email tài khoản của người nhận (nếu có).
        @Email @Size(max = 255) String receiverEmail,
        /// Kích thước kiện (cm), tuỳ chọn; có nhập thì phải nhập đủ ba cạnh và lọt khoang drone.
        @Min(1) Integer parcelLengthCm,
        @Min(1) Integer parcelWidthCm,
        @Min(1) Integer parcelHeightCm,
        /// DOCUMENT | FOOD | CLOTHING | ELECTRONICS | COSMETICS | OTHER. Bỏ trống ⇒ OTHER.
        @Size(max = 30) String parcelCategory,
        /// Giá trị khai báo (VND) làm căn cứ bồi thường, tuỳ chọn.
        @DecimalMin("0") BigDecimal declaredValue,
        Boolean fragile,
        /// Người gửi cam kết kiện không chứa hàng cấm bay (pin rời, chất lỏng, dễ cháy nổ…).
        /// Bắt buộc true.
        Boolean prohibitedItemsDeclared) {

    /// Các constructor ngắn dưới đây chỉ phục vụ code/test nội bộ viết trước khi đơn có phần
    /// khai báo kiện hàng: chúng coi như người gửi đã cam kết không gửi hàng cấm.
    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod,
            String fulfillmentMode,
            Long sourceBoxId,
            String receiverPhone,
            String receiverName,
            String receiverEmail) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams,
                paymentMethod, fulfillmentMode, sourceBoxId, receiverPhone, receiverName, receiverEmail,
                null, null, null, null, null, null, true);
    }

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod,
            String fulfillmentMode,
            Long sourceBoxId,
            String receiverPhone,
            String receiverName) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams,
                paymentMethod, fulfillmentMode, sourceBoxId, receiverPhone, receiverName, null);
    }

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod,
            String fulfillmentMode,
            Long sourceBoxId) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams,
                paymentMethod, fulfillmentMode, sourceBoxId, null, null);
    }

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod,
            String fulfillmentMode) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams,
                paymentMethod, fulfillmentMode, null, null, null);
    }

    public CreateDroneDeliveryOrderRequest(
            Long sourceLockerId,
            Long destinationLockerId,
            Long preferredBoxId,
            String description,
            Integer parcelWeightGrams,
            String paymentMethod) {
        this(sourceLockerId, destinationLockerId, preferredBoxId, description, parcelWeightGrams, paymentMethod,
                null, null, null, null);
    }
}
