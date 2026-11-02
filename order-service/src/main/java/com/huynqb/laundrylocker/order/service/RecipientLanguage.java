package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.order.client.UserClient;
import com.huynqb.laundrylocker.order.model.LockerOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/// Ngôn ngữ soạn thư/SMS gửi ngoài app (ADR-0010). Nơi gọi vẫn soạn tiếng Việt và gửi kèm ngôn ngữ này;
/// notification-service dịch khi là `en`.
///
/// - Người đặt đơn: ngôn ngữ app trên tài khoản của họ (user-service, app báo qua `PUT /api/user/language`).
/// - Người nhận có tài khoản: ngôn ngữ của chính họ; chưa có tài khoản (hoặc chưa báo): theo người đặt đơn —
///   người gửi dùng ngôn ngữ nào thường cũng viết cho người nhận bằng ngôn ngữ đó.
///
/// Tra không được thì trả null (tiếng Việt): hỏng tra cứu không được chặn thư.
@Slf4j
final class RecipientLanguage {

    private RecipientLanguage() {
    }

    static String ofUser(UserClient users, Long userId) {
        if (users == null || userId == null) {
            return null;
        }
        try {
            var response = users.getUser(userId);
            String language = response == null || response.data() == null ? null : response.data().language();
            return StringUtils.hasText(language) ? language : null;
        } catch (Exception ex) {
            log.debug("Language of user {} not resolved: {}", userId, ex.getMessage());
            return null;
        }
    }

    static String sender(UserClient users, LockerOrder order) {
        return order == null ? null : ofUser(users, order.getUserId());
    }

    static String receiver(UserClient users, LockerOrder order) {
        if (order == null) {
            return null;
        }
        String own = ofUser(users, order.getReceiverUserId());
        return own != null ? own : sender(users, order);
    }
}
