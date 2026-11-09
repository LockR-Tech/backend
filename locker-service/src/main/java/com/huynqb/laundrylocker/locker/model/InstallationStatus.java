package com.huynqb.laundrylocker.locker.model;

/// Trạng thái hồ sơ lắp đặt tủ (F3-G12). Tủ tương ứng: SCHEDULED → PLANNED, IN_PROGRESS → INSTALLING,
/// SUBMITTED → COMMISSIONING, APPROVED → ACTIVE; CANCELLED trả tủ về trạng thái trước khi lập hồ sơ.
public enum InstallationStatus {
    SCHEDULED("PLANNED"),
    IN_PROGRESS("INSTALLING"),
    SUBMITTED("COMMISSIONING"),
    APPROVED("ACTIVE"),
    CANCELLED(null);

    private final String lockerStatus;

    InstallationStatus(String lockerStatus) {
        this.lockerStatus = lockerStatus;
    }

    public String lockerStatus() {
        return lockerStatus;
    }

    public boolean isOpen() {
        return this == SCHEDULED || this == IN_PROGRESS || this == SUBMITTED;
    }
}
