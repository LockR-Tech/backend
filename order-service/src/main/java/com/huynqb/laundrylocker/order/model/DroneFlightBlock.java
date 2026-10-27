package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một lần hệ thống từ chối bay vì thời tiết hoặc lệnh dừng bay.
@Entity
@Table(name = "drone_flight_blocks", schema = "order_schema")
@Getter
@Setter
public class DroneFlightBlock {

    /// Lúc bị chặn: khách đặt đơn, đội bay nhận đơn, đội bay phóng drone, đơn đang chạy bị dừng.
    public static final String BOOKING = "BOOKING";
    public static final String ACCEPT = "ACCEPT";
    public static final String LAUNCH = "LAUNCH";
    public static final String IN_PROGRESS = "IN_PROGRESS";

    public static final String WEATHER_UNSAFE = "WEATHER_UNSAFE";
    public static final String WEATHER_UNAVAILABLE = "WEATHER_UNAVAILABLE";
    public static final String FLIGHTS_SUSPENDED = "FLIGHTS_SUSPENDED";
    public static final String STATION_SUSPENDED = "STATION_SUSPENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 30)
    private String cause;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_code", length = 50)
    private String orderCode;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "source_locker_id")
    private Long sourceLockerId;

    @Column(name = "destination_locker_id")
    private Long destinationLockerId;

    /// Trạm đang dừng gây ra lần chặn (cause = STATION_SUSPENDED).
    @Column(name = "blocked_locker_id")
    private Long blockedLockerId;

    @Column(length = 500)
    private String message;

    @Column(name = "weather_status", length = 20)
    private String weatherStatus;

    @Column(name = "max_wind_speed_kmh")
    private Double maxWindSpeedKmh;

    @Column(name = "max_wind_gust_kmh")
    private Double maxWindGustKmh;

    @Column(name = "max_precipitation_mm")
    private Double maxPrecipitationMm;

    @Column(name = "weather_code")
    private Integer weatherCode;

    @Column(name = "weather_provider", length = 20)
    private String weatherProvider;
}
