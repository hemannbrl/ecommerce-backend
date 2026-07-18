# E-Commerce backend — Express

The Express implementation of the shared e-commerce API. Node.js + Express over the shared
`ecommerce_db` schema, using Prisma for data access, with JWT auth, a product catalog, a cart, and an
atomic checkout that won't oversell stock.

One of three interchangeable backends (see the repo root); it implements the same contract as the Django
and Spring Boot versions.

## Stack

- Express 5
- Prisma ORM (mapped onto the existing schema — migrations are owned by `../db/schema.sql`)
- JWT auth (`jsonwebtoken`) with bcrypt passwords
- zod for request validation
- swagger-ui-express serving the shared `../api/openapi.yaml`
- Vitest + Supertest

## Running it

### With Docker (brings up Postgres too)

```bash
docker compose up --build
# API on http://localhost:3000, Swagger at http://localhost:3000/docs
```

The database container loads `../db/schema.sql` and `../db/seed.sql` on first start.

### Locally

First seed the shared database (creates it and loads schema + demo data) — from the repo root:

```bash
./db/setup.sh          # or  make setup
```

Then run the app:

```bash
npm install
cp .env.example .env          # set DATABASE_URL, JWT_SECRET
npx prisma generate
npm start
```

Seed logins (password `Password123!`): `admin@shop.test` (admin), `alice@shop.test`, `bob@shop.test`.

## A quick tour

```bash
curl -s -X POST localhost:3000/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"alice@shop.test","password":"Password123!"}'
# -> {"token":"...","user":{"id":2,"email":"alice@shop.test","role":"customer"}}

TOKEN=...
curl -s "localhost:3000/products?search=head&sort=price"
curl -s -X POST localhost:3000/cart/items -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{"product_id":3,"quantity":1}'
curl -s -X POST localhost:3000/orders -H "Authorization: Bearer $TOKEN"
```

## How checkout works

`POST /orders` runs inside one Prisma interactive transaction (`src/services.js`):

1. Lock each product's stock row with a raw `SELECT ... FOR UPDATE` (Prisma runs the whole callback on a
   single connection, so the lock holds for the transaction).
2. Check the locked quantity, snapshot the price into an order line, and decrement stock.
3. If stock is insufficient the transaction throws and rolls back (→ HTTP 409); the database
   `CHECK (quantity >= 0)` is the final backstop.
4. On success the cart is cleared.

`tests/concurrency.test.js` proves the guarantee: it fires two checkouts at once for the last unit and
asserts exactly one wins and stock never goes negative.

## Auth note

The shared `users` table stores bcrypt hashes, so Node's `bcrypt` reads and writes the same column the
Django and Spring Boot backends use. A small middleware validates the bearer token and attaches the user;
role (`customer`/`admin`) gates the admin routes.

## Tests

```bash
# needs a Postgres reachable at DATABASE_URL (a dedicated ecommerce_test DB)
DATABASE_URL=postgresql://postgres:postgres@localhost:5433/ecommerce_test npm test
```

A global setup loads `db/schema.sql` into the test database; the suite covers auth, catalog, cart,
checkout, and the concurrency guarantee (9 tests).

## Layout

```
express/
├── prisma/schema.prisma   models mapped onto ecommerce_db
└── src/
    ├── app.js             express app + middleware + docs
    ├── prisma.js          Prisma client
    ├── auth.js            JWT + bcrypt + requireAuth/requireAdmin
    ├── validate.js        zod schemas
    ├── services.js        cart helpers + the checkout transaction
    ├── mappers.js         Prisma rows -> API response shapes
    └── routes/            auth, catalog, cart, orders
```
