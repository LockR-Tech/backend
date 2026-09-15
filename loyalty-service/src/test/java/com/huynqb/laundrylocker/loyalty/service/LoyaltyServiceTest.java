package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.loyalty.dto.AdjustPointsRequest;
import com.huynqb.laundrylocker.loyalty.model.LoyaltyAccount;
import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import com.huynqb.laundrylocker.loyalty.repository.LoyaltyAccountRepository;
import com.huynqb.laundrylocker.loyalty.repository.PointTransactionRepository;
import com.huynqb.laundrylocker.loyalty.settings.LoyaltyRules;
import com.huynqb.laundrylocker.loyalty.settings.TestLoyaltyRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyServiceTest {

    @Mock
    LoyaltyAccountRepository accountRepository;
    @Mock
    PointTransactionRepository transactionRepository;

    private BusinessSettings settings;
    private LoyaltyService service;
    private LoyaltyAccount account;

    @BeforeEach
    void setUp() {
        settings = TestLoyaltyRules.settings(Map.of());
        service = new LoyaltyService(accountRepository, transactionRepository, new LoyaltyRules(settings));
        account = new LoyaltyAccount();
        account.setUserId(7L);
        when(accountRepository.findByUserId(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void tierThresholdsFollowAdminSettings() {
        assertEquals("SILVER", service.adjustPoints(new AdjustPointsRequest(7L, null, 1500, null)).tier());

        settings.update(Map.of("app.loyalty.tier-gold-points", 1000), null);
        assertEquals("GOLD", service.adjustPoints(new AdjustPointsRequest(7L, null, 0, null)).tier());

        // Mốc BẠCH KIM đặt thấp hơn mốc VÀNG ⇒ dùng mốc VÀNG, tổng 1500 điểm lên PLATINUM.
        settings.update(Map.of("app.loyalty.tier-platinum-points", 10), null);
        assertEquals("PLATINUM", service.adjustPoints(new AdjustPointsRequest(7L, null, 0, null)).tier());
    }

    @Test
    void stampRewardAndIncrementFollowAdminSettings() {
        account.setStamps(6);
        assertEquals(false, service.stampCards(7L).get(0).get("rewardAvailable"));

        settings.update(Map.of(
                "app.loyalty.stamps-per-reward", 5,
                "app.loyalty.points-reward-cost", 300,
                "app.loyalty.stamp-increment", 2), null);

        assertEquals(true, service.stampCards(7L).get(0).get("rewardAvailable"));
        List<Map<String, Object>> rewards = service.rewards();
        assertEquals(300, rewards.get(0).get("requiredPoints"));
        assertEquals(5, rewards.get(1).get("requiredStamps"));

        assertEquals(1, service.redeemReward(7L, 2L).stamps());
        assertEquals(3, service.addStamp(7L, null).stamps());

        account.setPoints(1000);
        assertEquals(700, service.redeemReward(7L, 1L).points());
    }
}
