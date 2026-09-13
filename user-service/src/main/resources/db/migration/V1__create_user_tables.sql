CREATE SCHEMA IF NOT EXISTS user_schema;

CREATE TABLE user_schema.user_profiles
(
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(255) UNIQUE,
    phone_number VARCHAR(50) UNIQUE,
    first_name   VARCHAR(100),
    last_name    VARCHAR(100),
    birthday     DATE,
    image_url    VARCHAR(1000),
    status       VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    roles        VARCHAR(500) NOT NULL DEFAULT 'CUSTOMER',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE user_schema.roles
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE user_schema.permissions
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE user_schema.role_permissions
(
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO user_schema.roles(name, description)
VALUES ('CUSTOMER', 'Customer role'),
       ('TECHNICIAN', 'Technician role'),
       ('COURIER', 'Courier role'),
       ('ADMIN', 'Administrator role') ON CONFLICT (name) DO NOTHING;
