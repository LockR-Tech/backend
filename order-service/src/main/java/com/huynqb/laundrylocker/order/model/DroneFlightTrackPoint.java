package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/// Một điểm trên đường bay của đơn drone. `source` = `DRONE` (telemetry thật) hoặc `DRONE_SIMULATED` (đơn DEMO).
@Entity
@Table(name = "drone_flight_track_points")
@Getter
@Setter
public class DroneFlightTrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "mission_id")
    private Long missionId;

    @Column(name = "drone_code", length = 50)
    private String droneCode;

    @Column(nullable = false, length = 30)
    private String stage;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(name = "heading_deg")
    private Double headingDeg;

    @Column(name = "speed_mps")
    private Double speedMps;

    @Column(name = "battery_percent")
    private Integer batteryPercent;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
