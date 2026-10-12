package com.huynqb.laundrylocker.notification.config;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.notification.client.OrderParticipantClient;
import com.huynqb.laundrylocker.notification.dto.OrderParticipants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Topic theo đơn `/topic/deliveries/{orderId}/**` (vị trí drone trực tiếp) chỉ dành cho chủ đơn,
/// tài khoản người nhận, KTV drone và admin. Kiểm ở frame SUBSCRIBE, sau khi
/// JwtStompPrincipalChannelInterceptor đã gắn người dùng cho phiên. Client không được SEND vào các
/// topic này — chỉ server phát vị trí.
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTopicSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern DELIVERY_TOPIC = Pattern.compile("^/topic/deliveries/(\\d+)(/.*)?$");
    /// Đích mẫu dùng để phát hiện đăng ký kiểu wildcard (`/topic/deliveries/*/position`, `/topic/**`).
    private static final String SAMPLE_DELIVERY_TOPIC = "/topic/deliveries/0/position";
    private static final String[] STAFF_ROLES = {"ADMIN", "DRONE_TECHNICIAN"};

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final OrderParticipantClient orderParticipantClient;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();
        if (destination == null
                || (!StompCommand.SUBSCRIBE.equals(command) && !StompCommand.SEND.equals(command))) {
            return message;
        }
        Matcher matcher = DELIVERY_TOPIC.matcher(destination);
        boolean deliveryTopic = matcher.matches();
        boolean wildcardOverDeliveries = !deliveryTopic
                && pathMatcher.isPattern(destination)
                && pathMatcher.match(destination, SAMPLE_DELIVERY_TOPIC);
        if (!deliveryTopic && !wildcardOverDeliveries) {
            return message;
        }
        if (StompCommand.SEND.equals(command)) {
            throw new MessagingException("Clients cannot publish to delivery topics");
        }
        Principal user = accessor.getUser();
        if (isStaff(user)) {
            return message;
        }
        if (wildcardOverDeliveries || !isParticipant(user, Long.valueOf(matcher.group(1)))) {
            throw new MessagingException("Not allowed to subscribe to " + destination);
        }
        return message;
    }

    private static boolean isStaff(Principal user) {
        return user instanceof StompPrincipal principal && principal.hasAnyRole(STAFF_ROLES);
    }

    /// Chủ đơn hoặc tài khoản người nhận. Không tra được order-service thì từ chối.
    private boolean isParticipant(Principal user, Long orderId) {
        Long userId = userId(user);
        if (userId == null) {
            return false;
        }
        try {
            ApiResponse<OrderParticipants> response = orderParticipantClient.participants(orderId);
            OrderParticipants participants = response == null ? null : response.data();
            return participants != null
                    && (userId.equals(participants.userId()) || userId.equals(participants.receiverUserId()));
        } catch (Exception ex) {
            log.warn("Could not check participants of order {}: {}", orderId, ex.getMessage());
            return false;
        }
    }

    private static Long userId(Principal user) {
        if (user == null || user.getName() == null) {
            return null;
        }
        try {
            return Long.valueOf(user.getName().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
