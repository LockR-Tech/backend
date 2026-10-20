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
import java.util.Set;

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
    /// Trừ điểm để giảm tiền một đơn (điểm âm); DB chỉ cho một dòng mỗi orderId.
    public static final String ORDER_REDEEM_TYPE = "ORDER_REDEEM";
    /// Hoàn đúng số điểm đã trừ khi đơn bị huỷ/hoàn tiền (điểm dương); DB chỉ cho một dòng mỗi orderId.
    public static final String ORDER_REDEEM_REFUND_TYPE = "ORDER_REDEEM_REFUND";
    /// Thu hồi điểm đã tích của đơn được hoàn tiền (điểm âm, theo tỉ lệ tiền đã hoàn; có thể nhiều dòng mỗi đơn).
    public static final String ORDER_EARN_REVERSAL_TYPE = "ORDER_EARN_REVERSAL";
    public static final String ADJUSTMENT_TYPE = "ADJUSTMENT";
    /// Đổi điểm lấy phần thưởng (luồng cũ).
    public static final String REDEEM_TYPE = "REDEEM";
    /// Các loại chỉ hệ thống được ghi — admin điều chỉnh tay không được dùng.
    private static final Set<String> RESERVED_TYPES =
            Set.of(ORDER_EARN_TYPE, ORDER_REDEEM_TYPE, ORDER_REDEEM_REFUND_TYPE, ORDER_EARN_REVERSAL_TYPE);
    /// Dùng/hoàn điểm không làm đổi tổng điểm đã tích (hạng); mọi loại khác (tích, điều chỉnh tay) thì có.
    private static final Set<String> SPENDING_TYPES =
            Set.of(ORDER_REDEEM_TYPE, ORDER_REDEEM_REFUND_TYPE, REDEEM_TYPE);

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
        String type = StringUtils.hasText(request.type()) ? request.type().trim() : ADJUSTMENT_TYPE;
        if (RESERVED_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_TYPE_RESERVED", "Loại " + type.toUpperCase(Locale.ROOT)
                            + " chỉ dành cho điểm hệ thống tự ghi theo đơn hàng");
        }
        return applyPoints(request.userId(), request.orderId(), request.points(), type);
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

    /// Đơn được hoàn tiền ⇒ thu hồi phần điểm đã tích tương ứng: điểm đã tích × (tiền đã hoàn / tiền đã thu),
    /// làm tròn. `refundedAmount` là tổng luỹ kế nên gọi lại (sự kiện giao lại, hoàn thêm lần nữa) chỉ thu
    /// phần chênh. Không trừ quá số dư hiện có (khách đã tiêu điểm thì chỉ thu được phần còn lại).
    /// Trả về số điểm vừa thu hồi.
    @Transactional
    public int reverseOrderEarn(Long orderId, BigDecimal refundedAmount, BigDecimal paidAmount) {
        if (orderId == null || refundedAmount == null || refundedAmount.signum() <= 0
                || paidAmount == null || paidAmount.signum() <= 0) {
            return 0;
        }
        PointTransaction earn = transactionRepository.findFirstByOrderIdAndType(orderId, ORDER_EARN_TYPE).orElse(null);
        if (earn == null || earn.getPoints() == null || earn.getPoints() <= 0) {
            return 0;
        }
        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(earn.getUserId()).orElse(null);
        if (account == null) {
            return 0;
        }
        int target = BigDecimal.valueOf(earn.getPoints())
                .multiply(refundedAmount.min(paidAmount))
                .divide(paidAmount, 0, RoundingMode.HALF_UP)
                .intValue();
        // Đọc dưới khoá tài khoản: lần thu hồi song song cùng đơn đã commit thì thấy ở đây.
        int reversed = (int) -transactionRepository.sumPointsByOrderIdAndType(orderId, ORDER_EARN_REVERSAL_TYPE);
        int deduct = Math.min(target - reversed, Math.max(0, account.getPoints()));
        if (deduct <= 0) {
            return 0;
        }
        applyPoints(account, orderId, -deduct, ORDER_EARN_REVERSAL_TYPE, null);
        return deduct;
    }

    private LoyaltyAccountResponse applyPoints(Long userId, Long orderId, int points, String type) {
        return applyPoints(lockedAccountOrNew(userId), orderId, points, type, null);
    }

    /// Ghi một giao dịch điểm vào tài khoản ĐÃ KHOÁ: đổi số dư, tổng điểm đã tích (trừ các loại dùng/hoàn
    /// điểm) và hạng theo tổng điểm đã tích.
    private LoyaltyAccountResponse applyPoints(
            LoyaltyAccount account, Long orderId, int points, String type, BigDecimal amount) {
        account.setPoints(account.getPoints() + points);
        if (!SPENDING_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            account.setLifetimePoints(Math.max(0, lifetimeOf(account) + points));
        }
        account.setTier(resolveTier(lifetimeOf(account)));

        PointTransaction transaction = new PointTransaction();
        transaction.setUserId(account.getUserId());
        transaction.setOrderId(orderId);
        transaction.setPoints(points);
        transaction.setType(type);
        transaction.setAmount(amount);
        LoyaltyAccountResponse response = toResponse(accountRepository.save(account));
        // Flush ngay để unique index chặn ghi trùng theo đơn ném lỗi trong lời gọi này (rollback cả phần đổi điểm).
        transactionRepository.saveAndFlush(transaction);
        return response;
    }

    /// Tài khoản đã khoá hàng; chưa có thì tạo mới (chưa lưu — lưu cùng giao dịch đầu tiên).
    private LoyaltyAccount lockedAccountOrNew(Long userId) {
        return accountRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
            LoyaltyAccount created = new LoyaltyAccount();
            created.setUserId(userId);
            return created;
        });
    }

    private static int lifetimeOf(LoyaltyAccount account) {
        return account.getLifetimePoints() == null ? 0 : account.getLifetimePoints();
    }

    // ------------------------------------------------------------------ dùng điểm thanh toán đơn

    /// Khách xem trước được dùng tối đa bao nhiêu điểm cho đơn `orderTotal` đồng (số tiền sau mã giảm giá).
    @Transactional(readOnly = true)
    public RedeemPreviewResponse redeemPreview(Long userId, BigDecimal orderTotal) {
        int balance = accountRepository.findByUserId(userId).map(LoyaltyAccount::getPoints).orElse(0);
        int maxPoints = rules.maxRedeemablePoints(balance, orderTotal);
        return new RedeemPreviewResponse(
                rules.redeemEnabled(),
                balance,
                rules.redeemVndPerPoint(),
                rules.redeemMaxPercent(),
                rules.redeemMinPoints(),
                maxPoints,
                rules.redeemValue(maxPoints));
    }

    /// Trừ điểm cho đơn vừa tạo. Số điểm kẹp theo cùng quy tắc với {@link #redeemPreview}. Idempotent theo
    /// orderId: gọi lại trả đúng kết quả lần đầu, không trừ thêm. Khoá hàng tài khoản nên hai lần trừ đồng
    /// thời không làm điểm âm; unique index (order_id) WHERE type = 'ORDER_REDEEM' chặn nốt trường hợp còn lại.
    @Transactional
    public OrderRedemptionResponse redeemForOrder(OrderRedemptionRequest request) {
        if (request.userId() == null || request.orderId() == null) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_REDEMPTION_INVALID", "Thiếu userId hoặc orderId của đơn dùng điểm");
        }
        int requested = request.requestedPoints() == null ? 0 : request.requestedPoints();
        if (requested <= 0) {
            return OrderRedemptionResponse.none();
        }
        Optional<PointTransaction> existing = existingRedemption(request);
        if (existing.isPresent()) {
            return redemptionOf(existing.get());
        }
        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(request.userId()).orElse(null);
        if (account == null) {
            return OrderRedemptionResponse.none();
        }
        // Lần gọi song song cùng đơn có thể vừa commit trong lúc chờ khoá.
        existing = existingRedemption(request);
        if (existing.isPresent()) {
            return redemptionOf(existing.get());
        }
        int points = rules.clampRedeemPoints(requested, account.getPoints(), request.orderTotal());
        if (points <= 0) {
            return OrderRedemptionResponse.none();
        }
        BigDecimal discount = rules.redeemValue(points);
        applyPoints(account, request.orderId(), -points, ORDER_REDEEM_TYPE, discount);
        return new OrderRedemptionResponse(points, discount);
    }

    /// Hoàn đúng số điểm đơn đã dùng (đơn huỷ / hoàn tiền). Idempotent: đơn không dùng điểm hoặc đã hoàn
    /// thì trả 0 và không ghi gì.
    @Transactional
    public OrderRedemptionRefundResponse refundOrderRedemption(Long orderId) {
        if (orderId == null) {
            return new OrderRedemptionRefundResponse(0);
        }
        PointTransaction redemption =
                transactionRepository.findFirstByOrderIdAndType(orderId, ORDER_REDEEM_TYPE).orElse(null);
        if (redemption == null
                || transactionRepository.existsByOrderIdAndType(orderId, ORDER_REDEEM_REFUND_TYPE)) {
            return new OrderRedemptionRefundResponse(0);
        }
        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(redemption.getUserId()).orElse(null);
        // Kiểm lại dưới khoá: lần hoàn song song có thể vừa commit.
        if (account == null || transactionRepository.existsByOrderIdAndType(orderId, ORDER_REDEEM_REFUND_TYPE)) {
            return new OrderRedemptionRefundResponse(0);
        }
        int points = Math.abs(redemption.getPoints());
        if (points == 0) {
            return new OrderRedemptionRefundResponse(0);
        }
        applyPoints(account, orderId, points, ORDER_REDEEM_REFUND_TYPE, redemption.getAmount());
        return new OrderRedemptionRefundResponse(points);
    }

    private Optional<PointTransaction> existingRedemption(OrderRedemptionRequest request) {
        Optional<PointTransaction> existing =
                transactionRepository.findFirstByOrderIdAndType(request.orderId(), ORDER_REDEEM_TYPE);
        if (existing.isPresent() && !request.userId().equals(existing.get().getUserId())) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_REDEMPTION_CONFLICT",
                    "Đơn #" + request.orderId() + " đã dùng điểm của tài khoản khác",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
        return existing;
    }

    private OrderRedemptionResponse redemptionOf(PointTransaction transaction) {
        int points = Math.abs(transaction.getPoints());
        BigDecimal amount = transaction.getAmount() != null ? transaction.getAmount() : rules.redeemValue(points);
        return new OrderRedemptionResponse(points, amount);
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
        // Khoá hàng trước khi đọc số dư: hai lần đổi đồng thời không được cùng thấy đủ điểm.
        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(request.userId()).orElse(null);
        int balance = account == null ? 0 : account.getPoints();
        if (account == null || balance < points) {
            throw new com.huynqb.laundrylocker.common.exception.BusinessException(
                    "LOYALTY_POINTS_INSUFFICIENT", "Không đủ điểm để đổi (hiện có " + balance + " điểm)");
        }
        return applyPoints(account, null, -points, REDEEM_TYPE, null);
    }

    /// Đổi tem: số tem phải dương và không vượt số tem đang có.
    @Transactional
    public LoyaltyAccountResponse redeemStamp(RedeemStampRequest request) {
        LoyaltyAccount account = accountRepository.findByUserIdForUpdate(request.userId()).orElseThrow(() -> new com.huynqb.laundrylocker.common.exception.NotFoundException("LoyaltyAccount", request.userId()));
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
        LoyaltyAccount account = lockedAccountOrNew(userId);
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
        LoyaltyRules.TierThresholds thresholds = rules.tierThresholds();
        accountRepository.countByLifetimeTier(thresholds.silver(), thresholds.gold(), thresholds.platinum())
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

    private String resolveTier(int lifetimePoints) {
        return rules.tierFor(lifetimePoints);
    }

    /// Hạng luôn tính lại theo mốc đang áp dụng (admin đổi mốc thì tài khoản chưa đổi điểm cũng thấy ngay).
    private LoyaltyAccountResponse toResponse(LoyaltyAccount account) {
        int lifetime = lifetimeOf(account);
        return new LoyaltyAccountResponse(
                account.getId(), account.getUserId(), account.getPoints(), account.getStamps(),
                resolveTier(lifetime), lifetime);
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
