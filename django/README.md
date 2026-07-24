# E-Commerce backend — Django

The Django implementation of the shared e-commerce API. It's a REST API built with Django REST
Framework over the shared `ecommerce_db` schema, with JWT auth, a product catalog, a cart, and an
atomic checkout that won't oversell stock.

This is one of three interchangeable backends (see the repo root); it implements the same contract as
the Spring Boot and Express versions.

## Stack

- Django + Django REST Framework
- PostgreSQL (the app maps onto the existing schema — `managed = False` models, no ORM-owned tables)
- JWT auth (PyJWT) over bcrypt-hashed passwords
- drf-spectacular for the OpenAPI schema and Swagger UI
- pytest for tests

## Running it

### With Docker (brings up Postgres too)

```bash
docker compose up --build
# API on http://localhost:8000, Swagger at http://localhost:8000/docs
```

The database container loads `../db/schema.sql` and `../db/seed.sql` on first start.

### Locally

First seed the shared database (creates it and loads schema + demo data) — from the repo root:

```bash
./db/setup.sh          # or  make setup
```

Then run the app:

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# settings read from the environment; the defaults match ./db/setup.sh (localhost:5433),
# so this runs as-is. To override, export the vars in .env.example (e.g. DB_PORT) first:
#   set -a; source .env.example; set +a
python manage.py runserver
```

Seed logins (password `Password123!`): `admin@shop.test` (admin), `alice@shop.test`, `bob@shop.test`.

## A quick tour

```bash
# log in
curl -s -X POST localhost:8000/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"alice@shop.test","password":"Password123!"}'
# -> {"token":"...","user":{"id":2,"email":"alice@shop.test","role":"customer"}}

TOKEN=...   # paste the token

# browse the catalog (search, filter by category slug, sort, paginate)
curl -s "localhost:8000/products?search=head&sort=price&page=1"

# add to cart and check out
curl -s -X POST localhost:8000/cart/items -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{"product_id":3,"quantity":1}'
curl -s -X POST localhost:8000/orders -H "Authorization: Bearer $TOKEN"
```

## How checkout works

Checkout is the interesting part. `POST /orders` runs in a single transaction (`shop/services.py`):

1. Lock each product's stock row (`select_for_update()` → `SELECT ... FOR UPDATE`).
2. Snapshot the price into an order line and decrement stock.
3. The database `CHECK (quantity >= 0)` rejects any decrement that would oversell.
4. On success the cart is cleared; on any failure the whole order rolls back and the client gets a 409.

Because the row is locked, two shoppers racing for the last unit can't both succeed. There's a test that
proves it (`shop/tests/test_concurrency.py`): it runs two real checkouts on separate threads and asserts
exactly one wins and stock never goes negative.

## Auth note

The shared `users` table stores bcrypt hashes so all three backends read the same column. Django's
default auth model doesn't fit that schema, so this app verifies bcrypt and issues JWTs itself
(`shop/auth.py`) rather than using Django's auth framework.

## Tests

```bash
pip install -r requirements-dev.txt
pytest
```

The suite loads `db/schema.sql` into a throwaway test database, then covers auth, catalog, cart,
checkout, and the concurrency guarantee — 17 tests.

## Layout

```
django/
├── config/         settings, urls, wsgi
├── shop/
│   ├── models.py       managed=False models over ecommerce_db
│   ├── auth.py         bcrypt + JWT authentication
│   ├── permissions.py  IsAuthenticated / IsAdmin
│   ├── serializers.py  request/response validation
│   ├── services.py     cart + the checkout transaction
│   ├── views.py        the endpoints
│   └── tests/
├── Dockerfile
├── docker-compose.yml
└── requirements.txt
```
