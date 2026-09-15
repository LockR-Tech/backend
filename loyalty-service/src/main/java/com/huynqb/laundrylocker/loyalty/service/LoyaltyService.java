package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.loyalty.dto.*;
import com.huynqb.laundrylocker.loyalty.model.LoyaltyAccount;
import com.huynqb.laundrylocker.loyalty.model.PointTransaction;
import com.huynqb.laundrylocker.loyalty.repository.LoyaltyAccountRepository;
import com.huynqb.laundrylocker.loyalty.repository.PointTransactionRepository;
import com.huynqb.laundrylocker.loyalty.settings.LoyaltyRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;
    /// Mốc hạng, số tem/điểm đổi thưởng do admin cấu hình (ADR-0005).
    private final LoyaltyRules rules;

    @Transactional
    public LoyaltyAccountResponse adjustPoints(AdjustPointsRequest request) {
        LoyaltyAccount account =
                accountRepository
                        .findByUserId(request.userId())
                        .orElseGet(
                                () -> {
                                    LoyaltyAccount created = new LoyaltyAccount();
                                    created.setUserId(request.userId());
                                    return created;
                                });
        account.setPoints(account.getPoints() + request.points());
        account.setTier(resolveTier(account.getPoints()));

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(request.userId());
        transaction.setOrderId(request.orderId());
        transaction.setPoints(request.points());
        transaction.setType(StringUtils.hasText(request.type()) ? request.type() : "ADJUSTMENT");
        transactionRepository.save(transaction);

        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public LoyaltyAccountResponse getByUser(Long userId) {
        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseGet(() -> {
            LoyaltyAccount created = new LoyaltyAccount();
            created.setUserId(userId);
            return created;
        });
        return toResponse(account);
    }

    @Transactional
    public LoyaltyAccountResponse redeemPoints(RedeemPointsRequest request) {
        return adjustPoints(new AdjustPointsRequest(request.userId(), null, -Math.abs(request.points()), "REDEEM"));
    }

    @Transactional
    public LoyaltyAccountResponse redeemStamp(RedeemStampRequest request) {
        LoyaltyAccount account = accountRepository.findByUserId(request.userId()).orElseThrow(() -> new com.huynqb.laundrylocker.common.exception.NotFoundException("LoyaltyAccount", request.userId()));
        account.setStamps(Math.max(0, account.getStamps() - Math.abs(request.stamps())));
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public LoyaltyAccountResponse addStamp(Long userId, Integer count) {
        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseGet(() -> {
            LoyaltyAccount created = new LoyaltyAccount();
            created.setUserId(userId);
            return created;
        });
        account.setStamps(account.getStamps() + (count == null ? rules.stampIncrement() : count));
        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public java.util.List<PointTransactionResponse> history(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toTransaction).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> stampCards(Long userId) {
        LoyaltyAccount account = accountRepository.findByUserId(userId).orElseGet(() -> {
            LoyaltyAccount created = new LoyaltyAccount();
            created.setUserId(userId);
            return created;
        });
        return List.of(Map.of(
                "userId", userId,
                "stamps", account.getStamps(),
                "rewardAvailable", account.getStamps() >= rules.stampsPerReward()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stampCard(Long userId, Long stampCardId) {
        return Map.of("id", stampCardId, "userId", userId, "stamps", getByUser(userId).stamps());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rewards() {
        int stamps = rules.stampsPerReward();
        return List.of(
                Map.of("id", 1L, "name", "Free basic wash", "requiredPoints", rules.pointsRewardCost()),
                Map.of("id", 2L, "name", stampRewardName(stamps), "requiredStamps", stamps));
    }

    @Transactional
    public LoyaltyAccountResponse redeemReward(Long userId, Long rewardId) {
        if (rewardId == 2L) {
            int stamps = rules.stampsPerReward();
            return redeemStamp(new RedeemStampRequest(userId, stamps, stampRewardName(stamps)));
        }
        return redeemPoints(new RedeemPointsRequest(userId, rules.pointsRewardCost(), "Reward redemption"));
    }

    private static String stampRewardName(int stamps) {
        return stamps == 10 ? "Ten-stamp reward" : stamps + "-stamp reward";
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expiringPoints(Long userId) {
        return List.of();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statistics() {
        return Map.of("accounts", accountRepository.count(), "transactions", transactionRepository.count());
    }

    private String resolveTier(int points) {
        return rules.tierFor(points);
    }

    private LoyaltyAccountResponse toResponse(LoyaltyAccount account) {
        return new LoyaltyAccountResponse(account.getId(), account.getUserId(), account.getPoints(), account.getStamps(), account.getTier());
    }

    private PointTransactionResponse toTransaction(PointTransaction transaction) {
        return new PointTransactionResponse(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getOrderId(),
                transaction.getPoints(),
                transaction.getType(),
                transaction.getCreatedAt());
    }
}
