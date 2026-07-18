-- Demo data for ecommerce_db. Load after schema.sql.
--
-- CANONICAL DATASET: this is the single source of truth for the demo data, shared
-- by all three backends (Django, Spring Boot, Express) AND referenced throughout
-- the Playground learning content (product counts, stock levels, revenue figures,
-- order history). Loaded automatically by `docker compose up` and by db/setup.sh.
-- Change it deliberately — edits here must stay consistent with the lessons.
--
-- Demo login for every seeded user: password "Password123!"
-- (the same bcrypt hash is reused below so any account logs in).

BEGIN;

-- Users ---------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, role) VALUES
  (1, 'admin@shop.test', '$2b$10$wkB7jZmnOFycD4VcrREXaeIs7MmIc2WbgabzvE8uzkAkp4yg7QHni', 'admin'),
  (2, 'alice@shop.test', '$2b$10$wkB7jZmnOFycD4VcrREXaeIs7MmIc2WbgabzvE8uzkAkp4yg7QHni', 'customer'),
  (3, 'bob@shop.test',   '$2b$10$wkB7jZmnOFycD4VcrREXaeIs7MmIc2WbgabzvE8uzkAkp4yg7QHni', 'customer');

-- Categories ----------------------------------------------------------------
INSERT INTO categories (id, name, slug) VALUES
  (1, 'Electronics',    'electronics'),
  (2, 'Books',          'books'),
  (3, 'Home & Kitchen', 'home-kitchen'),
  (4, 'Clothing',       'clothing'),
  (5, 'Sports',         'sports');

-- Products ------------------------------------------------------------------
INSERT INTO products (id, name, slug, description, price, category_id, is_active) VALUES
  (1,  'Laptop 14"',        'laptop-14',        'Lightweight 14-inch laptop.',           899.99, 1, TRUE),
  (2,  'Smartphone X',      'smartphone-x',     'Flagship smartphone.',                  699.00, 1, TRUE),
  (3,  'Wireless Headphones','wireless-headphones','Over-ear noise-cancelling.',           79.99, 1, TRUE),
  (4,  '27" Monitor',       '27-monitor',       'QHD 27-inch monitor.',                  249.50, 1, TRUE),
  (5,  'Mechanical Keyboard','mechanical-keyboard','Tactile mechanical keyboard.',          59.90, 1, TRUE),
  (6,  'The Pragmatic Programmer','pragmatic-programmer','Classic software book.',           39.99, 2, TRUE),
  (7,  'Clean Code',        'clean-code',       'Handbook of agile craftsmanship.',       34.50, 2, TRUE),
  (8,  'SQL in Depth',      'sql-in-depth',     'Relational databases, thoroughly.',      44.00, 2, TRUE),
  (9,  'Blender Pro',       'blender-pro',      '900W kitchen blender.',                  89.00, 3, TRUE),
  (10, 'Coffee Maker',      'coffee-maker',     '12-cup drip coffee maker.',              49.99, 3, TRUE),
  (11, 'Chef Knife',        'chef-knife',       '8-inch stainless chef knife.',           29.99, 3, TRUE),
  (12, 'Running Shoes',     'running-shoes',    'Cushioned road-running shoes.',          119.99,5, TRUE),
  (13, 'Yoga Mat',          'yoga-mat',         'Non-slip 6mm yoga mat.',                 24.99, 5, TRUE),
  (14, 'Cotton T-Shirt',    'cotton-t-shirt',   'Plain cotton crew-neck tee.',            14.99, 4, TRUE),
  (15, 'Denim Jacket',      'denim-jacket',     'Classic denim jacket.',                  69.99, 4, TRUE),
  (16, 'Webcam 1080p',      'webcam-1080p',     '1080p USB webcam.',                       45.00, 1, TRUE),
  (17, 'Desk Lamp',         'desk-lamp',        'Dimmable LED desk lamp.',                27.50, 3, FALSE); -- inactive/discontinued

-- Inventory (one row per product; product 3 well-stocked, 5 low, 16 never ordered)
INSERT INTO inventory (product_id, quantity) VALUES
  (1, 8), (2, 3), (3, 40), (4, 12), (5, 4), (6, 25), (7, 30), (8, 18),
  (9, 15), (10, 4), (11, 22), (12, 9), (13, 33), (14, 60), (15, 7), (16, 20), (17, 0);

-- One active cart (Alice), demonstrating cart state --------------------------
INSERT INTO carts (id, user_id) VALUES (1, 2);
INSERT INTO cart_items (cart_id, product_id, quantity) VALUES
  (1, 3, 1),      -- Wireless Headphones
  (1, 5, 2);      -- Mechanical Keyboard x2

-- Historical orders (for reports: top sellers, revenue, order history) -------
INSERT INTO orders (id, user_id, status, total, created_at) VALUES
  (1, 2, 'delivered', 979.98, now() - interval '30 days'),
  (2, 3, 'delivered', 403.80, now() - interval '21 days'),
  (3, 2, 'shipped',   119.99, now() - interval '10 days'),
  (4, 3, 'paid',      168.98, now() - interval '5 days'),
  (5, 2, 'pending',    79.99, now() - interval '1 day'),
  (6, 3, 'cancelled',  34.50, now() - interval '3 days');

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
  (1, 1, 1, 899.99), (1, 3, 1, 79.99),                 -- order 1
  (2, 4, 1, 249.50),                                    -- (note: historical price snapshot)
  (2, 7, 1, 34.50), (2, 5, 2, 59.90),                  -- order 2
  (3, 12, 1, 119.99),                                   -- order 3
  (4, 9, 1, 89.00), (4, 10, 1, 49.99), (4, 11, 1, 29.99), -- order 4
  (5, 3, 1, 79.99),                                     -- order 5
  (6, 7, 1, 34.50);                                     -- order 6 (cancelled)

-- Keep the BIGSERIAL sequences ahead of the hand-set ids so app inserts work.
SELECT setval('users_id_seq',      (SELECT max(id) FROM users));
SELECT setval('categories_id_seq', (SELECT max(id) FROM categories));
SELECT setval('products_id_seq',   (SELECT max(id) FROM products));
SELECT setval('carts_id_seq',      (SELECT max(id) FROM carts));
SELECT setval('cart_items_id_seq', (SELECT max(id) FROM cart_items));
SELECT setval('orders_id_seq',     (SELECT max(id) FROM orders));
SELECT setval('order_items_id_seq',(SELECT max(id) FROM order_items));

COMMIT;
