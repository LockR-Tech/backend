package com.huynqb.laundrylocker.order.model;

import java.util.Set;

/** Canonical states for the parcel-drop workflow. */
public final class DroneIncidentStates {
    private DroneIncidentStates() {
    }

    public static final Set<String> INCIDENT = Set.of(
            "REPORTED", "EVIDENCE_PENDING", "INVESTIGATING", "RECOVERY_IN_PROGRESS",
            "AWAITING_ADMIN_REVIEW", "RESOLUTION_PROPOSED", "AWAITING_CUSTOMER_RESPONSE",
            "RESOLUTION_IN_PROGRESS", "DISPUTED", "RESOLVED", "CLOSED",
            "MANUAL_INTERVENTION_REQUIRED");
    public static final Set<String> RECOVERY = Set.of(
            "OPEN", "AWAITING_ASSIGNMENT", "ASSIGNED", "ACCEPTED", "SEARCHING", "PARCEL_FOUND",
            "PARCEL_NOT_FOUND", "RECOVERED", "SUBMITTED", "VERIFIED", "CLOSED", "CANCELLED");
    public static final Set<String> PARCEL = Set.of(
            "IN_TRANSIT", "DROP_REPORTED", "RECOVERY_PENDING", "FOUND", "RECOVERED", "DAMAGED",
            "LOST", "RETURNED_TO_HUB", "REDELIVERY_PENDING", "REDELIVERED", "COMPENSATED");
    public static final Set<String> RETURN_FLIGHT = Set.of(
            "RETURN_REQUESTED", "COMMAND_ACCEPTED", "RETURNING", "COMPLETED", "FAILED",
            "MANUAL_INTERVENTION_REQUIRED");
    public static final Set<String> CONDITIONS = Set.of(
            "INTACT", "MINOR_DAMAGE", "MAJOR_DAMAGE", "UNUSABLE", "UNKNOWN");
}

