CREATE SCHEMA IF NOT EXISTS order_schema;

CREATE TABLE order_schema.orders
(
    id                      BIGSERIAL PRIMARY KEY,
    order_code              VARCHAR(50) NOT NULL UNIQUE,
    user_id                 BIGINT      NOT NULL,
    receiver_id             BIGINT,
    receiver_phone          VARCHAR(50),
    receiver_name           VARCHAR(255),
    locker_id               BIGINT,
    send_box_id             BIGINT,
    receive_box_id          BIGINT,
    store_id                BIGINT,
    staff_id                BIGINT,
    type                    VARCHAR(30) NOT NULL DEFAULT 'STORAGE',
    service_category        VARCHAR(30)          DEFAULT 'STORAGE',
    status                  VARCHAR(30) NOT NULL DEFAULT 'INITIALIZED',
    actual_weight           NUMERIC(10, 2),
    weight_unit             VARCHAR(20)          DEFAULT 'kg',
    extra_fee               NUMERIC(12, 2)       DEFAULT 0,
    discount                NUMERIC(12, 2)       DEFAULT 0,
    reservation_fee         NUMERIC(12, 2)       DEFAULT 0,
    storage_price           NUMERIC(12, 2)       DEFAULT 0,
    shipping_fee            NUMERIC(12, 2)       DEFAULT 0,
    total_price             NUMERIC(12, 2)       DEFAULT 0,
    original_price          NUMERIC(12, 2)       DEFAULT 0,
    promotion_code          VARCHAR(255),
    applied_promotion_codes VARCHAR(1000),
    pin_code                VARCHAR(20),
    pin_code_issued_at      TIMESTAMP,
    receive_at              TIMESTAMP,
    intended_receive_at     TIMESTAMP,
    completed_at            TIMESTAMP,
    returned_at             TIMESTAMP,
    pickup_deadline         TIMESTAMP,
    description             VARCHAR(2000),
    customer_note           VARCHAR(1000),
    staff_note              VARCHAR(1000),
    cancel_reason           INTEGER,
    delivery_address        VARCHAR(500),
    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP
);

CREATE INDEX idx_orders_user_id ON order_schema.orders (user_id);
CREATE INDEX idx_orders_status ON order_schema.orders (status);
CREATE INDEX idx_orders_order_code ON order_schema.orders (order_code);

CREATE TABLE order_schema.order_details
(
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT         NOT NULL,
    service_id  BIGINT         NOT NULL,
    quantity    NUMERIC(10, 2) NOT NULL DEFAULT 1,
    price       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    description VARCHAR(1000)
);

CREATE TABLE order_schema.order_status_history
(
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT      NOT NULL,
    old_status         VARCHAR(30),
    new_status         VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT,
    note               VARCHAR(1000),
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_schema.order_ratings
(
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT    NOT NULL UNIQUE,
    user_id    BIGINT    NOT NULL,
    rating     INTEGER   NOT NULL,
    comment    VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_schema.order_complaints
(
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    type        VARCHAR(50)   NOT NULL DEFAULT 'OTHER',
    description VARCHAR(2000) NOT NULL,
    status      VARCHAR(30)   NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_schema.promotions
(
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(100)   NOT NULL UNIQUE,
    name                VARCHAR(255)   NOT NULL,
    discount_type       VARCHAR(30)    NOT NULL DEFAULT 'FIXED_AMOUNT',
    discount_value      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    max_discount_amount NUMERIC(12, 2),
    min_order_amount    NUMERIC(12, 2),
    stackable           BOOLEAN        NOT NULL DEFAULT FALSE,
    status              VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',
    start_at            TIMESTAMP,
    end_at              TIMESTAMP,
    usage_count         INTEGER        NOT NULL DEFAULT 0,
    created_by_user_id  BIGINT,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE INDEX idx_order_details_order_id ON order_schema.order_details (order_id);
CREATE INDEX idx_order_status_history_order_id ON order_schema.order_status_history (order_id);
CREATE INDEX idx_order_complaints_order_id ON order_schema.order_complaints (order_id);
