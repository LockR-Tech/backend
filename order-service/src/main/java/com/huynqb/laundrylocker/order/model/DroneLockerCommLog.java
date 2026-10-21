package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "drone_locker_comm_logs", schema = "order_schema")
@Getter
@Setter
public class DroneLockerCommLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "journey_id")
    private Long journeyId;

    @Column(name = "drone_id", length = 50)
    private String droneId;

    @Column(name = "locker_id")
    private Long lockerId;

    @Column(name = "compartment_id")
    private Long compartmentId;

    @Column(name = "marker_id")
    private Integer markerId;

    @Column(name = "marker_dictionary", length = 50)
    private String markerDictionary;

    @Column(name = "source_device", length = 50)
    private String sourceDevice;

    @Column(name = "destination_device", length = 50)
    private String destinationDevice;

    @Column(name = "mqtt_topic", length = 255)
    private String mqttTopic;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "publish_status", length = 30)
    private String publishStatus;

    @Column(name = "ack_status", length = 30)
    private String ackStatus;

    @Column(name = "processing_status", length = 30)
    private String processingStatus;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }
}

