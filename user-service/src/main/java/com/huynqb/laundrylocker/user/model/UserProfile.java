package com.huynqb.laundrylocker.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AdminDataChangeListener.class)
@Table(name = "user_profiles")
@Getter
@Setter
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private LocalDate birthday;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "roles", nullable = false, length = 500)
    private String roles = "CUSTOMER";

    /// Đã bấm "Đồng ý" Điều khoản dịch vụ trên app chưa (V6). Tài khoản cũ = false ⇒ app hỏi một lần.
    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted = false;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /// Phiên bản điều khoản đã đồng ý (app gửi lên), vd. "2026-10-31".
    @Column(name = "terms_version", length = 30)
    private String termsVersion;

    /// Xoá mềm từ trang admin: còn nguyên trong DB kèm lý do, thời điểm và người xoá.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

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
