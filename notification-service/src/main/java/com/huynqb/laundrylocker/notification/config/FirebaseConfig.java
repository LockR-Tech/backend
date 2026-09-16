package com.huynqb.laundrylocker.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/// Khởi tạo `FirebaseApp` cho notification-service.
///
/// `FcmPushNotificationService` đã viết đủ từ trước, nhưng mọi hàm gửi đều thoát sớm ở
/// `isFirebaseAvailable()` vì **không có ai gọi `FirebaseApp.initializeApp` trong service
/// này** — chỉ auth-service khởi tạo, mà `FirebaseApp.getApps()` là trạng thái theo tiến
/// trình nên service khác không thấy. Kết quả: app đăng ký device token đều đặn, server
/// im lặng bỏ qua mọi lệnh push.
///
/// Thiếu credentials thì chỉ ghi cảnh báo rồi chạy tiếp: push tắt, phần còn lại của
/// notification-service (in-app, STOMP, SMS, email) vẫn hoạt động.
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.credentials-json:}")
    private String credentialsJson;

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }
            FirebaseOptions.Builder builder = FirebaseOptions.builder();
            if (StringUtils.hasText(credentialsJson)) {
                builder.setCredentials(
                        GoogleCredentials.fromStream(
                                new ByteArrayInputStream(
                                        credentialsJson.getBytes(StandardCharsets.UTF_8))));
            } else {
                // Trên GCP thì lấy được credentials mặc định của máy; ở nơi khác sẽ ném
                // lỗi và rơi xuống catch — đúng ý, vì lúc đó push vốn không dùng được.
                builder.setCredentials(GoogleCredentials.getApplicationDefault());
            }
            FirebaseApp.initializeApp(builder.build());
            log.info("Firebase initialised — FCM push enabled");
        } catch (Exception ex) {
            log.warn("Firebase not initialised, FCM push disabled: {}", ex.getMessage());
        }
    }
}
