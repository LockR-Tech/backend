package com.huynqb.laundrylocker.payment.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.payment.dto.internal.IncidentCompensationUpdate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/// Báo kết quả chi bồi thường sự cố rơi kiện về order-service (chủ của sự cố), để sự cố không đứng mãi ở
/// "đang chi trả" sau khi admin đã chuyển khoản.
@FeignClient(name = "order-service", contextId = "droneIncidentClient", path = "/internal/drone-incidents")
public interface DroneIncidentClient {

    @PostMapping("/{incidentCode}/compensation")
    ApiResponse<Object> updateCompensation(
            @PathVariable("incidentCode") String incidentCode,
            @RequestBody IncidentCompensationUpdate request);
}
