-- Voucher hoa promotions:
--  - Scope theo tu/kiosk (locker_id NULL = toan he thong)
--  - Gioi han luot dung: tong (total_usage_limit) va theo user (per_user_limit)
--  - promotion_usages: moi lan ap ma vao don (de enforce per-user + hoan khi huy)
--  - promotion_claims: "vi voucher" cua user (luu ma tu trang khuyen mai)

ALTER TABLE order_schema.promotions
    ADD COLUMN locker_id BIGINT;
ALTER TABLE order_schema.promotions
    ADD COLUMN total_usage_limit INT;
ALTER TABLE order_schema.promotions
    ADD COLUMN per_user_limit INT;

CREATE TABLE order_schema.promotion_usages
(
    id               BIGSERIAL PRIMARY KEY,
    promotion_id     BIGINT    NOT NULL,
    user_id          BIGINT    NOT NULL,
    order_id         BIGINT,
    discount_applied NUMERIC(12, 2),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (promotion_id) REFERENCES order_schema.promotions (id)
);

CREATE INDEX idx_promotion_usages_promo_user ON order_schema.promotion_usages (promotion_id, user_id);
CREATE INDEX idx_promotion_usages_order ON order_schema.promotion_usages (order_id);

CREATE TABLE order_schema.promotion_claims
(
    id           BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'SAVED',
    used_at      TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (promotion_id, user_id),
    FOREIGN KEY (promotion_id) REFERENCES order_schema.promotions (id)
);

CREATE INDEX idx_promotion_claims_user ON order_schema.promotion_claims (user_id);
