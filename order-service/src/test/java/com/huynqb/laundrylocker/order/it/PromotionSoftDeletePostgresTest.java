package com.huynqb.laundrylocker.order.it;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.repository.PromotionRepository;
import com.huynqb.laundrylocker.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Xoá khuyến mãi ở admin là xoá mềm (V42): bản ghi còn trong DB kèm lý do, biến khỏi truy vấn, mã được dùng lại.
class PromotionSoftDeletePostgresTest extends OrderServicePostgresTestBase {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PromotionRepository promotions;
    @Autowired
    private OrderService orderService;

    @Test
    void deletedPromotionStaysInTheTableWithItsReasonAndFreesTheCode() {
        String code = "SOFT" + (System.nanoTime() % 1_000_000);
        Promotion promotion = promotions.saveAndFlush(promotion(code));

        orderService.deletePromotion(promotion.getId(), "  Hết chương trình  ", 7L);

        assertTrue(promotions.findById(promotion.getId()).isEmpty());
        assertTrue(promotions.findByCodeIgnoreCase(code).isEmpty());
        Map<String, Object> row = jdbc.queryForMap(
                "select deleted_at, deleted_by, deleted_reason from order_schema.promotions where id = ?",
                promotion.getId());
        assertNotNull(row.get("deleted_at"));
        assertEquals(7L, ((Number) row.get("deleted_by")).longValue());
        assertEquals("Hết chương trình", row.get("deleted_reason"));

        Promotion reused = promotions.saveAndFlush(promotion(code));
        assertNotNull(reused.getId());
        assertThrows(DataIntegrityViolationException.class, () -> promotions.saveAndFlush(promotion(code)));
    }

    @Test
    void deletingWithoutAReasonIsRejected() {
        Promotion promotion = promotions.saveAndFlush(promotion("NOREASON" + (System.nanoTime() % 1_000_000)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.deletePromotion(promotion.getId(), " ", 7L));

        assertEquals("DELETE_REASON_REQUIRED", ex.getCode());
        assertTrue(promotions.findById(promotion.getId()).isPresent());
    }

    private static Promotion promotion(String code) {
        Promotion promotion = new Promotion();
        promotion.setCode(code);
        promotion.setName("Khuyến mãi " + code);
        promotion.setDiscountValue(new BigDecimal("10000"));
        return promotion;
    }
}
