package com.huynqb.laundrylocker.order.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/// Đặt giao hàng bằng drone, lấy hàng tận nhà. Người gửi cho biết lấy kiện ở đâu và giao tới tủ
/// nào; hệ thống tự chọn tủ gửi (tủ có bãi đáp gần điểm lấy hàng nhất) và KTV drone tới lấy.
public record CreateDroneDeliveryOrderRequest(
        /// Tủ người nhận tới lấy hàng bằng mã mở ô.
        @NotNull Long destinationLockerId,
        /// Ô DRONE mong muốn ở tủ nhận; bỏ trống thì hệ thống tự giữ một ô trống.
        Long preferredBoxId,

        /// Điểm lấy hàng: vị trí hiện tại của người gửi hoặc điểm họ ghim trên bản đồ.
        @NotBlank @Size(min = 5, max = 500) String pickupAddress,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLongitude,
        /// Chỉ dẫn cho KTV (tầng, cổng, giờ có nhà…), tuỳ chọn.
        @Size(max = 500) String pickupNote,

        @NotBlank @Size(max = 120) String senderName,
        @NotBlank @Pattern(regexp = PHONE, message = "Sender phone number is not valid") String senderPhone,
        @NotBlank @Email @Size(max = 255) String senderEmail,

        @NotBlank @Size(max = 120) String receiverName,
        @NotBlank @Pattern(regexp = PHONE, message = "Receiver phone number is not valid") String receiverPhone,
        @NotBlank @Email @Size(max = 255) String receiverEmail,

        /// Tối đa theo cấu hình tải của drone (mặc định 5 kg một đơn).
        @NotNull @Min(1) Integer parcelWeightGrams,
        /// DOCUMENT | FOOD | CLOTHING | ELECTRONICS | COSMETICS | OTHER.
        @NotBlank @Size(max = 30) String parcelCategory,
        @Size(max = 2000) String description,
        /// Kích thước kiện (cm), tuỳ chọn; có nhập thì phải nhập đủ ba cạnh và lọt khoang drone.
        @Min(1) Integer parcelLengthCm,
        @Min(1) Integer parcelWidthCm,
        @Min(1) Integer parcelHeightCm,
        /// Giá trị khai báo (VND) làm căn cứ bồi thường, tuỳ chọn.
        @DecimalMin("0") BigDecimal declaredValue,
        Boolean fragile,
        /// Người gửi cam kết kiện không chứa hàng cấm bay (pin rời, chất lỏng, dễ cháy nổ…).
        /// Bắt buộc true.
        Boolean prohibitedItemsDeclared,

        /// DEMO | STANDARD; bỏ trống thì theo cấu hình.
        String fulfillmentMode,

        /// Mã giảm giá (tuỳ chọn); mã theo tủ được kiểm theo TỦ GỬI hệ thống chọn.
        @Size(max = 100) String promotionCode,
        /// Số điểm thưởng muốn dùng (tuỳ chọn); loyalty-service kẹp theo quy tắc admin.
        Integer pointsToUse) {

    /// Số điện thoại 9–15 chữ số, cho phép dấu `+` đầu.
    public static final String PHONE = "^[+]?[0-9]{9,15}$";
}
