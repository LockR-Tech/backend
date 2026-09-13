package com.huynqb.laundrylocker.auth.config;

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

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.credentials-json:}")
    private String credentialsJson;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions.Builder builder = FirebaseOptions.builder();
                if (StringUtils.hasText(credentialsJson)) {
                    builder.setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))));
                } else {
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                }
                FirebaseApp.initializeApp(builder.build());
            }
        } catch (Exception e) {
            log.warn("Failed to initialize FirebaseApp. Firebase features will be disabled. Error: {}", e.getMessage());
        }
    }
}
