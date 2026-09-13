package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.CellDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "locker-service", contextId = "lockerCellClient", path = "/internal/lockers")
public interface LockerCellClient {

    @GetMapping("/{id}/boxes/find")
    ApiResponse<CellDto> findAvailable(
            @PathVariable Long id,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String cellType);

    @GetMapping("/boxes/{boxId}/cell")
    ApiResponse<CellDto> getCell(@PathVariable Long boxId);
}
