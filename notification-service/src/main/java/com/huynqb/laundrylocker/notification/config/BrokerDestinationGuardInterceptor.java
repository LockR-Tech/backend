package com.huynqb.laundrylocker.notification.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Set;

/// Quyền với broker đơn giản (`/topic`, `/queue`), kiểm ở frame SUBSCRIBE/SEND sau khi
/// JwtStompPrincipalChannelInterceptor đã gắn người dùng cho phiên (SEC-05):
///
/// - Các topic phát chung (`/topic/orders`, `/topic/notifications`, `/topic/technicians/**`,
///   `/topic/lockers/**`...) mang nội dung thông báo của mọi người — kể cả tin "Mã mở tủ" gửi người
///   nhận — và vị trí KTV, nên chỉ ADMIN (web quản trị) được nghe. `/topic/deliveries/**` do
///   DeliveryTopicSubscriptionInterceptor kiểm theo đơn.
/// - `/topic/public/locker-availability` chỉ mang mã tủ vừa đổi số ô trống, `/topic/public/catalog`
///   chỉ mang loại + mã khuyến mãi/cửa hàng/tủ admin vừa sửa — mọi người dùng đã đăng nhập được nghe
///   để màn đặt đơn, danh sách tủ, ưu đãi cập nhật ngay.
/// - `/topic/staff/maintenance` chỉ mang loại + mã việc bảo trì vừa đổi (phiếu, lịch, tủ, drone, đơn drone,
///   thiết bị) — KTV tủ, KTV drone và ADMIN nghe để màn KTV cập nhật ngay; khách bị từ chối.
/// - Không ai đăng ký thẳng `/queue/**`: hàng đợi riêng phải đi qua `/user/queue/...` để Spring
///   gắn đúng phiên; đăng ký kiểu `/queue/**` sẽ nhận hộp thư của mọi người.
/// - Chỉ ADMIN được đăng ký theo mẫu (`*`, `?`, `{}`).
/// - Client chỉ được SEND tới `/app/**`; gửi thẳng vào `/topic` hay `/queue` là giả tin của server.
@Component
public class BrokerDestinationGuardInterceptor implements ChannelInterceptor {

    private static final String DELIVERY_PREFIX = "/topic/deliveries/";
    private static final Set<String> PUBLIC_TOPICS = Set.of(
            "/topic/public/locker-availability", "/topic/public/catalog");
    private static final String STAFF_MAINTENANCE_TOPIC = "/topic/staff/maintenance";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        if (StompCommand.SEND.equals(command) && !destination.startsWith("/app/")) {
            throw new MessagingException("Clients cannot publish to " + destination);
        }
        if (!StompCommand.SUBSCRIBE.equals(command)) {
            return message;
        }
        if (isUnder(destination, "/queue")) {
            throw new MessagingException("Subscribe to /user/queue/... instead of " + destination);
        }
        boolean admin = isAdmin(accessor);
        if (STAFF_MAINTENANCE_TOPIC.equals(destination)) {
            if (isStaff(accessor)) {
                return message;
            }
            throw new MessagingException("Not allowed to subscribe to " + destination);
        }
        // Broker khớp đích theo mẫu Ant: `/**` hay `/t*/**` nghe được mọi topic và hộp thư.
        if (isPattern(destination) && !admin) {
            throw new MessagingException("Wildcard subscriptions are not allowed: " + destination);
        }
        if (isUnder(destination, "/topic")
                && !destination.startsWith(DELIVERY_PREFIX)
                && !PUBLIC_TOPICS.contains(destination)
                && !admin) {
            throw new MessagingException("Not allowed to subscribe to " + destination);
        }
        return message;
    }

    private static boolean isPattern(String destination) {
        return destination.indexOf('*') >= 0 || destination.indexOf('?') >= 0 || destination.indexOf('{') >= 0;
    }

    private static boolean isUnder(String destination, String prefix) {
        return destination.equals(prefix) || destination.startsWith(prefix + "/");
    }

    private static boolean isStaff(StompHeaderAccessor accessor) {
        return accessor.getUser() instanceof StompPrincipal principal
                && principal.hasAnyRole("ADMIN", "LOCKER_TECHNICIAN", "DRONE_TECHNICIAN");
    }

    private static boolean isAdmin(StompHeaderAccessor accessor) {
        return accessor.getUser() instanceof StompPrincipal principal && principal.hasAnyRole("ADMIN");
    }
}
