package com.huynqb.laundrylocker.locker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một ảnh đính kèm phiếu sự cố. File nằm trên Cloudinary; bảng này chỉ giữ metadata.
@Entity
@Table(name = "report_attachments")
@Getter
@Setter
public class ReportAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "repair_log_id")
    private Long repairLogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachmentStage stage;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "secure_url", nullable = false, length = 1000)
    private String secureUrl;

    @Column(length = 20)
    private String format;

    private Long bytes;

    private Integer width;

    private Integer height;

    @Column(length = 500)
    private String caption;

    private Double latitude;

    private Double longitude;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
