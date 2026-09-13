ALTER TABLE order_schema.promotions
    ADD COLUMN IF NOT EXISTS image_url VARCHAR (500);
ALTER TABLE order_schema.promotions
    ADD COLUMN IF NOT EXISTS description VARCHAR (1000);
