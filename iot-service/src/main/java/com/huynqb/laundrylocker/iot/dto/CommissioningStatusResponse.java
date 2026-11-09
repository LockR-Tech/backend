package com.huynqb.laundrylocker.iot.dto;

import java.time.LocalDateTime;
import java.util.List;

/// Dữ liệu nghiệm thu một tủ đang lắp đặt (F3-G12) cho locker-service tự xác nhận các bước:
/// bộ điều khiển đã gán / online / cài sơ đồ ô, cấu hình phần cứng đã áp, và kết quả các lệnh
/// mở thử ô / nắp kể từ lúc bắt đầu lắp (`since`).
public record CommissioningStatusResponse(
        Long lockerId,
        GatewayDeviceResponse controller,
        String hardwareApplyStatus,
        LocalDateTime hardwareAppliedAt,
        String hardwareApplyMessage,
        List<CommandResult> commands) {

    /// Một lệnh điều khiển đã ghi ở `cabinet_control_logs` (BOX_OPEN, BOX_OPEN_TEST, LID_OPEN, LID_CLOSE).
    public record CommandResult(
            String action,
            Long boxId,
            Integer boxNumber,
            Integer axis,
            String status,
            String message,
            LocalDateTime occurredAt) {
    }
}
