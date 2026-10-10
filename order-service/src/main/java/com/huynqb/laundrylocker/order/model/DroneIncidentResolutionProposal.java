package com.huynqb.laundrylocker.order.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "drone_incident_resolution_proposals")
@Getter
@Setter
public class DroneIncidentResolutionProposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "incident_id", nullable = false)
    private Long incidentId;
    @Column(name = "proposal_version", nullable = false)
    private Integer proposalVersion;
    @Column(name = "resolution_type", nullable = false, length = 50)
    private String resolutionType;
    @Column(name = "redelivery_offered", nullable = false)
    private boolean redeliveryOffered;
    @Column(name = "compensation_amount", precision = 12, scale = 2)
    private BigDecimal compensationAmount;
    @Column(name = "refund_shipping_fee", nullable = false)
    private boolean refundShippingFee;
    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;
    @Column(name = "override_reason", length = 1000)
    private String overrideReason;
    @Column(name = "proposed_by_user_id", nullable = false)
    private Long proposedByUserId;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "customer_response_note", length = 1000)
    private String customerResponseNote;
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
