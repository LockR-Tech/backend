package com.huynqb.laundrylocker.auth.email;

import org.springframework.scheduling.annotation.Async;

public interface EmailService {

    @Async
    void sendSimpleEmail(String to, String subject, String text);

    @Async
    void sendHtmlEmail(String to, String subject, String htmlContent);
}
