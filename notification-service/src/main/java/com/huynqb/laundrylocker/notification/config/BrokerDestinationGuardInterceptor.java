package com.huynqb.laundrylocker.notification.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/// Quyền với broker đơn giản (`/topic`, `/queue`), kiểm ở frame SUBSCRIBE/SEND sau khi
/// JwtStompPrincipalChannelInterceptor đã gắn người dùng cho phiên (SEC-05):
///
/// - Các topic phát chung (`/topic/orders`, `/topic/notifications`, `/topic/technicians/**`,
///   `/topic/lockers/**`...) mang nội dung thông báo của mọi người — kể cả tin "Mã mở tủ" gửi người
///   nhận — và vị trí KTV, nên chỉ ADMIN (web quản trị) được nghe. `/topic/deliveries/**` do
///   DeliveryTopicSubscriptionInterceptor kiểm theo đơn.
/// - Không ai đăng ký thẳng `/queue/**`: hàng đợi riêng phải đi qua `/user/queue/...` để Spring
///   gắn đúng phiên; đăng ký kiểu `/queue/**` sẽ nhận hộp thư của mọi người.
/// - Chỉ ADMIN được đăng ký theo mẫu (`*`, `?`, `{}`).
/// - Client chỉ được SEND tới `/app/**`; gửi thẳng vào `/topic` hay `/queue` là giả tin của server.
@Component
public class BrokerDestinationGuardInterceptor implements ChannelInterceptor {

    private static final String DELIVERY_PREFIX = "/topic/deliveries/";

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
        // Broker khớp đích theo mẫu Ant: `/**` hay `/t*/**` nghe được mọi topic và hộp thư.
        if (isPattern(destination) && !admin) {
            throw new MessagingException("Wildcard subscriptions are not allowed: " + destination);
        }
        if (isUnder(destination, "/topic") && !destination.startsWith(DELIVERY_PREFIX) && !admin) {
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

    private static boolean isAdmin(StompHeaderAccessor accessor) {
        return accessor.getUser() instanceof StompPrincipal principal && principal.hasAnyRole("ADMIN");
    }
}
