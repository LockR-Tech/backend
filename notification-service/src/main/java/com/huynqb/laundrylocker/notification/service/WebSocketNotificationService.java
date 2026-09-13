package com.huynqb.laundrylocker.notification.service;

import com.huynqb.laundrylocker.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private static final String USER_NOTIFICATION_DESTINATION = "/queue/notifications";
    private static final String BROADCAST_DESTINATION = "/topic/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToUser(Long userId, NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId), USER_NOTIFICATION_DESTINATION, notification);
        } catch (Exception ex) {
            log.warn("Failed to send WebSocket notification to user {}: {}", userId, ex.getMessage());
        }
    }

    public void broadcast(NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSend(BROADCAST_DESTINATION, notification);
        } catch (Exception ex) {
            log.warn("Failed to broadcast WebSocket notification: {}", ex.getMessage());
        }
    }

    public void sendToDestination(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception ex) {
            log.warn("Failed to send WebSocket message to {}: {}", destination, ex.getMessage());
        }
    }
}
