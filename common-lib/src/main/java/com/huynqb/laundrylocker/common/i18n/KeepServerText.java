package com.huynqb.laundrylocker.common.i18n;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Đặt trên controller (hoặc từng hàm) mà JSON trả về chứa dữ liệu phải giữ nguyên chữ — giá trị cấu hình,
/// nội dung admin soạn: [EnglishResponseAdvice] bỏ qua, controller tự dịch phần là chữ server soạn (nhãn,
/// mô tả…) qua [ServerText] khi cần. Lỗi ném ra từ các hàm này vẫn được dịch như thường.
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface KeepServerText {
}
