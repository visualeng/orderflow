-- orderflow: товары со складским остатком, заказы и их позиции.
-- Схемой владеет Flyway, Hibernate только проверяет совпадение (ddl-auto: validate).

CREATE TABLE products (
    sku         VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    price_cents BIGINT       NOT NULL CHECK (price_cents >= 0),
    qty         INT          NOT NULL CHECK (qty >= 0),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(20)  NOT NULL
                    CHECK (status IN ('new', 'confirmed', 'shipped', 'cancelled')),
    total_cents     BIGINT       NOT NULL DEFAULT 0 CHECK (total_cents >= 0),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- позиции заказа: цена сохраняется на момент заказа, чтобы её правка
-- в каталоге не переписывала историю
CREATE TABLE order_items (
    order_id    UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku         VARCHAR(64)  NOT NULL,
    qty         INT          NOT NULL CHECK (qty > 0),
    price_cents BIGINT       NOT NULL CHECK (price_cents >= 0),
    PRIMARY KEY (order_id, sku)
);

CREATE INDEX orders_status_created_idx ON orders (status, created_at DESC);
CREATE INDEX order_items_sku_idx ON order_items (sku);
