package com.huynqb.laundrylocker.loyalty.service;

import com.huynqb.laundrylocker.common.util.BusinessTime;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;
    /// Mốc hạng, số tem/điểm đổi thưởng do admin cấu hình (ADR-0005).
    private final LoyaltyRules rules;

    /// Hạng hiển thị sẵn trong thống kê (0 nếu chưa có ai) theo thứ tự tăng dần.
    private static final List<String> TIERS = List.of("BRONZE", "SILVER", "GOLD", "PLATINUM");
    /// Loại giao dịch điểm tự cộng khi đơn hoàn tất; DB chỉ cho một dòng loại này mỗi orderId.
    public static final String ORDER_EARN_TYPE = "ORDER_EARN";

    private BusinessTime time = BusinessTime.system();

    void setTime(BusinessTime time) {
        this.time = time;
    }

    @Transactional
    public LoyaltyAccountResponse adjustPoints(AdjustPointsRequest request) {
        if (request.userId() == null) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_USER_REQUIRED", "Thiếu userId của tài khoản cần điều chỉnh điểm");
        }
        if (ORDER_EARN_TYPE.equalsIgnoreCase(request.type() == null ? "" : request.type().trim())) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_TYPE_RESERVED", "Loại " + ORDER_EARN_TYPE + " chỉ dành cho điểm tự cộng khi đơn hoàn tất");
        }
        return applyPoints(request.userId(), request.orderId(), request.points(),
                StringUtils.hasText(request.type()) ? request.type() : "ADJUSTMENT");
    }

    /// Cộng điểm khi đơn hoàn tất — mỗi đơn đúng một lần. Sự kiện giao lại/xử lý song song bị chặn bởi kiểm tra
    /// trước và unique index (order_id) WHERE type = 'ORDER_EARN' (lần ghi thứ hai ném DataIntegrityViolationException,
    /// transaction rollback cả phần cộng điểm). Số tiền 0đ hoặc dưới mức 1 điểm ⇒ không cộng.
    @Transactional
    public Optional<LoyaltyAccountResponse> awardOrderPoints(Long orderId, Long userId, BigDecimal paidAmount) {
        if (orderId == null || userId == null) {
            return Optional.empty();
        }
        int points = rules.pointsFor(paidAmount);
        if (points <= 0 || transactionRepository.existsByOrderIdAndType(orderId, ORDER_EARN_TYPE)) {
            return Optional.empty();
        }
        return Optional.of(applyPoints(userId, orderId, points, ORDER_EARN_TYPE));
    }

    private LoyaltyAccountResponse applyPoints(Long userId, Long orderId, int points, String type) {
        LoyaltyAccount account =
                accountRepository
                        .findByUserId(userId)
                        .orElseGet(
                                () -> {
                                    LoyaltyAccount created = new LoyaltyAccount();
                                    created.setUserId(userId);
                                    return created;
                                });
        account.setPoints(account.getPoints() + points);
        account.setTier(resolveTier(account.getPoints()));

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setPoints(points);
        transaction.setType(type);
        LoyaltyAccountResponse response = toResponse(accountRepository.save(account));
        // Flush ngay để unique index chặn cộng trùng ném lỗi trong lời gọi này (rollback cả phần cộng điểm).
        transactionRepository.saveAndFlush(transaction);
        return response;
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

    /// Đổi điểm: số điểm phải dương và không vượt số dư (không để điểm âm).
    @Transactional
    public LoyaltyAccountResponse redeemPoints(RedeemPointsRequest request) {
        int points = request.points() == null ? 0 : request.points();
        if (points <= 0) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_POINTS_INVALID", "Số điểm đổi phải lớn hơn 0");
        }
        int balance = accountRepository.findByUserId(request.userId()).map(LoyaltyAccount::getPoints).orElse(0);
        if (balance < points) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_POINTS_INSUFFICIENT", "Không đủ điểm để đổi (hiện có " + balance + " điểm)");
        }
        return applyPoints(request.userId(), null, -points, "REDEEM");
    }

    /// Đổi tem: số tem phải dương và không vượt số tem đang có.
    @Transactional
    public LoyaltyAccountResponse redeemStamp(RedeemStampRequest request) {
        LoyaltyAccount account = accountRepository.findByUserId(request.userId()).orElseThrow(() -> new com.huynqb.laundrylocker.common.exception.NotFoundException("LoyaltyAccount", request.userId()));
        int stamps = request.stamps() == null ? 0 : request.stamps();
        if (stamps <= 0 || account.getStamps() < stamps) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_STAMPS_INSUFFICIENT",
                    "Không đủ tem để đổi (cần " + Math.max(stamps, 1) + ", hiện có " + account.getStamps() + ")");
        }
        account.setStamps(account.getStamps() - stamps);
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

    /// Thống kê cho trang /admin/loyalty: hai truy vấn tổng hợp + một truy vấn đếm theo hạng,
    /// không nạp từng tài khoản/giao dịch lên bộ nhớ.
    @Transactional(readOnly = true)
    public LoyaltyStatisticsResponse statistics() {
        LocalDateTime monthStart = time.startOfDay(time.startOfMonth(time.today()));
        LocalDateTime activeSince = time.now().minusDays(30);

        LoyaltyAccountRepository.AccountAggregate accounts = accountRepository.aggregate(monthStart);
        PointTransactionRepository.TransactionAggregate transactions =
                transactionRepository.aggregate(monthStart, activeSince);

        Map<String, Long> tiers = new LinkedHashMap<>();
        TIERS.forEach(tier -> tiers.put(tier, 0L));
        accountRepository.countByTier()
                .forEach(row -> tiers.merge(row.getTier(), row.getMembers() == null ? 0L : row.getMembers(), Long::sum));

        long totalMembers = asLong(accounts.getTotalMembers());
        return new LoyaltyStatisticsResponse(
                totalMembers,
                asLong(transactions.getTransactions()),
                totalMembers,
                asLong(accounts.getNewMembersThisMonth()),
                asLong(transactions.getActiveMembers()),
                tiers,
                asLong(accounts.getTotalPoints()),
                round2(accounts.getAveragePoints()),
                round2(accounts.getMedianPoints()),
                asLong(transactions.getPointsIssued()),
                asLong(transactions.getPointsRedeemed()),
                asLong(transactions.getPointsIssuedThisMonth()),
                asLong(transactions.getPointsRedeemedThisMonth()));
    }

    private static long asLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private static double round2(Number value) {
        if (value == null) {
            return 0d;
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).doubleValue();
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
