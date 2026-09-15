package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.dto.ApiResponse;
import com.huynqb.laundrylocker.common.dto.UserSummary;
import com.huynqb.laundrylocker.order.client.LockerLookupClient;
import com.huynqb.laundrylocker.order.client.PaymentReportClient;
import com.huynqb.laundrylocker.order.client.StoreLookupClient;
import com.huynqb.laundrylocker.order.client.UserLookupClient;
import com.huynqb.laundrylocker.order.dto.admin.OrderPaymentSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReferenceResolverTest {

    @Mock private UserLookupClient userLookupClient;
    @Mock private LockerLookupClient lockerLookupClient;
    @Mock private StoreLookupClient storeLookupClient;
    @Mock private PaymentReportClient paymentReportClient;
    @InjectMocks private AdminReferenceResolver resolver;

    @Test
    void usersDeduplicatesIdsAndSplitsLargeBatches() {
        List<Long> ids = new ArrayList<>(LongStream.rangeClosed(1, 250).boxed().toList());
        ids.add(1L);
        ids.add(null);
        when(userLookupClient.getUsers(anyCollection())).thenAnswer(invocation -> {
            List<Long> chunk = new ArrayList<>(invocation.<java.util.Collection<Long>>getArgument(0));
            return ApiResponse.ok(chunk.stream()
                    .map(id -> new UserSummary(id, null, null, "User " + id, "ACTIVE"))
                    .toList());
        });

        AdminReferenceResolver.Lookup<UserSummary> result = resolver.users(ids);

        assertTrue(result.available());
        assertEquals(250, result.values().size());
        assertEquals("User 250", result.get(250L).fullName());
        verify(userLookupClient, times(2)).getUsers(anyCollection());
    }

    @Test
    void failingServiceYieldsUnavailableLookupInsteadOfException() {
        when(paymentReportClient.getOrderSummaries(anyCollection())).thenThrow(new RuntimeException("payment-service down"));

        AdminReferenceResolver.Lookup<OrderPaymentSummary> result = resolver.paymentSummaries(List.of(1L));

        assertFalse(result.available());
        assertNull(result.get(1L));
    }

    @Test
    void emptyIdsSkipRemoteCall() {
        assertTrue(resolver.users(Arrays.asList(null, null)).available());
        verifyNoInteractions(userLookupClient);
    }

    @Test
    void phoneLookupSwallowsNotFound() {
        when(userLookupClient.getUserByPhone("0900000000")).thenThrow(new RuntimeException("404"));
        assertNull(resolver.userIdByPhone("0900000000"));
    }
}
