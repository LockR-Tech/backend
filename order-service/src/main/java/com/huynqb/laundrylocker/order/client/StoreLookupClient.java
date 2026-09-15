package com.huynqb.laundrylocker.order.client;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.order.dto.admin.StoreInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "store-service", contextId = "storeLookupClient", path = "/internal/stores")
public interface StoreLookupClient {

    /// ids null ⇒ toàn bộ cửa hàng.
    @GetMapping("/batch")
    ApiResponse<List<StoreInfo>> getStores(@RequestParam(value = "ids", required = false) Collection<Long> ids);
}
