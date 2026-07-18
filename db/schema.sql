-- ecommerce_db — the single source of truth for all three backends.
-- The Django, Spring Boot, and Express apps all map onto this exact schema;
-- none of them owns it. Load this first, then seed.sql.

-- Clean slate (safe to re-run in development)
DROP TABLE IF EXISTS order_items, orders, cart_items, carts,
                     inventory, products, categories, users CASCADE;

-- Accounts. password_hash holds a bcrypt hash ($2b$...), so every framework
-- (Spring's BCryptPasswordEncoder, Node's bcrypt, a bcrypt check in Django)
-- reads the same column.
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'customer'
                                CHECK (role IN ('customer', 'admin')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    slug        VARCHAR(220)  NOT NULL UNIQUE,
    description TEXT          NOT NULL DEFAULT '',
    price       NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    category_id BIGINT        REFERENCES categories(id) ON DELETE SET NULL,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- One stock row per product (one-to-one). quantity can never go negative —
-- this CHECK is the last line of defence against overselling at checkout.
CREATE TABLE inventory (
    product_id BIGINT      PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    quantity   INTEGER     NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One active cart per user.
CREATE TABLE carts (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT    NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT    NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity   INTEGER   NOT NULL CHECK (quantity > 0),
    UNIQUE (cart_id, product_id)          -- one row per product in a cart
);

CREATE TABLE orders (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status     VARCHAR(20)   NOT NULL DEFAULT 'pending'
                             CHECK (status IN ('pending','paid','shipped','delivered','cancelled')),
    total      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Line items snapshot the price at order time (unit_price), so later price
-- changes never rewrite past orders.
CREATE TABLE order_items (
    id         BIGSERIAL     PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT        NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity   INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0)
);

-- Indexes on the foreign keys the API filters and joins on.
CREATE INDEX idx_products_category   ON products(category_id);
CREATE INDEX idx_cart_items_cart     ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product  ON cart_items(product_id);
CREATE INDEX idx_orders_user         ON orders(user_id);
CREATE INDEX idx_order_items_order   ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
