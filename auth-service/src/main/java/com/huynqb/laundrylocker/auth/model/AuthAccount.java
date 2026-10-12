package com.huynqb.laundrylocker.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_accounts")
@Getter
@Setter
public class AuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 500)
    private String passwordHash;

    /// false = mật khẩu ngẫu nhiên do hệ thống tạo (tài khoản Google/Firebase, đăng ký OTP không đặt mật khẩu):
    /// người dùng chưa biết mật khẩu nào nên được đặt mật khẩu đầu tiên mà không cần mật khẩu hiện tại.
    @Column(name = "password_set", nullable = false)
    private Boolean passwordSet = true;

    @Column(name = "auth_provider", nullable = false, length = 30)
    private String authProvider = "LOCAL";

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private Boolean phoneVerified = false;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
