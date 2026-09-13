package com.huynqb.laundrylocker.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.huynqb.laundrylocker.notification.model.FcmToken;
import com.huynqb.laundrylocker.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushNotificationService {

    private static final int FCM_MULTICAST_LIMIT = 500;

    private final FcmTokenRepository fcmTokenRepository;

    @Async
    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) {
            log.debug("Firebase not initialized, skipping FCM push for user {}", userId);
            return;
        }
        List<String> tokens = fcmTokenRepository.findByUserId(userId).stream().map(FcmToken::getToken).toList();
        sendInBatches(tokens, title, body, data);
    }

    @Async
    public void broadcast(String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) {
            log.debug("Firebase not initialized, skipping FCM broadcast");
            return;
        }
        sendInBatches(fcmTokenRepository.findAllDistinctTokens(), title, body, data);
    }

    public void sendToToken(String token, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) {
            return;
        }
        try {
            Message.Builder builder =
                    Message.builder()
                            .setToken(token)
                            .setNotification(
                                    com.google.firebase.messaging.Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build());
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }
            FirebaseMessaging.getInstance().send(builder.build());
        } catch (FirebaseMessagingException ex) {
            handleTokenError(token, ex);
        }
    }

    private void sendInBatches(
            List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (int i = 0; i < tokens.size(); i += FCM_MULTICAST_LIMIT) {
            sendMulticast(tokens.subList(i, Math.min(i + FCM_MULTICAST_LIMIT, tokens.size())), title, body, data);
        }
    }

    private void sendMulticast(
            List<String> tokens, String title, String body, Map<String, String> data) {
        try {
            MulticastMessage.Builder builder =
                    MulticastMessage.builder()
                            .addAllTokens(tokens)
                            .setNotification(
                                    com.google.firebase.messaging.Notification.builder()
                                            .setTitle(title)
                                            .setBody(body)
                                            .build());
            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(builder.build());
            if (response.getFailureCount() > 0) {
                removeStaleTokens(tokens, response.getResponses());
            }
        } catch (FirebaseMessagingException ex) {
            log.warn("FCM multicast failed: {}", ex.getMessage());
        }
    }

    private void removeStaleTokens(List<String> tokens, List<SendResponse> responses) {
        List<String> staleTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (!response.isSuccessful()
                    && response.getException() != null
                    && (response.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || response.getException().getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                staleTokens.add(tokens.get(i));
            }
        }
        staleTokens.forEach(token -> fcmTokenRepository.findByToken(token).ifPresent(fcmTokenRepository::delete));
    }

    private void handleTokenError(String token, FirebaseMessagingException ex) {
        if (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
            fcmTokenRepository.findByToken(token).ifPresent(fcmTokenRepository::delete);
        } else {
            log.warn("FCM send failed: {}", ex.getMessage());
        }
    }

    private boolean isFirebaseAvailable() {
        try {
            return !FirebaseApp.getApps().isEmpty();
        } catch (Exception ex) {
            return false;
        }
    }
}
