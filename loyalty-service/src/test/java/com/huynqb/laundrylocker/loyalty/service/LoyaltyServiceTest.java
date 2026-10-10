package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.settings.BusinessSettings;
import com.huynqb.laundrylocker.common.util.BusinessTime;
import com.huynqb.laundrylocker.loyalty.dto.AdjustPointsRequest;
import com.huynqb.laundrylocker.loyalty.dto.LoyaltyStatisticsResponse;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @Test
    void statisticsCutMonthByBusinessTimezoneAndFillAllTiers() {
        // 18:30 UTC ngày 30/09 = 01:30 ngày 01/10 giờ Việt Nam ⇒ "tháng này" là tháng 10.
        service.setTime(new BusinessTime(
                ZoneOffset.UTC, BusinessTime.DEFAULT_BUSINESS_ZONE,
                Clock.fixed(Instant.parse("2026-09-30T18:30:00Z"), ZoneOffset.UTC)));
        LocalDateTime monthStart = LocalDateTime.of(2026, 9, 30, 17, 0);
        LocalDateTime activeSince = LocalDateTime.of(2026, 8, 31, 18, 30);

        LoyaltyAccountRepository.AccountAggregate accounts = mock(LoyaltyAccountRepository.AccountAggregate.class);
        when(accounts.getTotalMembers()).thenReturn(4L);
        when(accounts.getNewMembersThisMonth()).thenReturn(1L);
        when(accounts.getTotalPoints()).thenReturn(1400L);
        when(accounts.getAveragePoints()).thenReturn(new java.math.BigDecimal("466.666666"));
        when(accounts.getMedianPoints()).thenReturn(200.5d);
        when(accountRepository.aggregate(monthStart)).thenReturn(accounts);
        when(accountRepository.countByTier()).thenReturn(List.of(tier("SILVER", 3L), tier("BRONZE", 1L)));

        PointTransactionRepository.TransactionAggregate transactions =
                mock(PointTransactionRepository.TransactionAggregate.class);
        when(transactions.getTransactions()).thenReturn(9L);
        when(transactions.getPointsIssued()).thenReturn(1650L);
        when(transactions.getPointsRedeemed()).thenReturn(250L);
        when(transactions.getPointsIssuedThisMonth()).thenReturn(300L);
        when(transactions.getPointsRedeemedThisMonth()).thenReturn(null);
        when(transactions.getActiveMembers()).thenReturn(2L);
        when(transactionRepository.aggregate(monthStart, activeSince)).thenReturn(transactions);

        LoyaltyStatisticsResponse stats = service.statistics();

        assertEquals(4L, stats.accounts());
        assertEquals(9L, stats.transactions());
        assertEquals(4L, stats.totalMembers());
        assertEquals(1L, stats.newMembersThisMonth());
        assertEquals(2L, stats.activeMembersLast30Days());
        assertEquals(List.of("BRONZE", "SILVER", "GOLD", "PLATINUM"), List.copyOf(stats.tierDistribution().keySet()));
        assertEquals(Map.of("BRONZE", 1L, "SILVER", 3L, "GOLD", 0L, "PLATINUM", 0L), stats.tierDistribution());
        assertEquals(1400L, stats.totalPointsOutstanding());
        assertEquals(466.67, stats.averagePoints());
        assertEquals(200.5, stats.medianPoints());
        assertEquals(1650L, stats.pointsIssued());
        assertEquals(250L, stats.pointsRedeemed());
        assertEquals(300L, stats.pointsIssuedThisMonth());
        assertEquals(0L, stats.pointsRedeemedThisMonth());
    }

    private static LoyaltyAccountRepository.TierCount tier(String tier, long members) {
        return new LoyaltyAccountRepository.TierCount() {
            @Override
            public String getTier() {
                return tier;
            }

            @Override
            public Long getMembers() {
                return members;
            }
        };
    }
}
