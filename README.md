# E-Commerce Backend — one API, three stacks

The same e-commerce backend built three times, in **Django**, **Spring Boot**, and **Express**. All
three run against the same PostgreSQL database and implement the same REST API, so you can start any one
of them and hit it with the same requests.

The point isn't to pick a winner — it's to show the same design (auth, catalog, cart, and an atomic
checkout that can't oversell stock) expressed idiomatically in three ecosystems.

## Layout

```
ecommerce-backend/
├── db/            schema.sql + seed.sql — the shared database
├── api/           openapi.yaml — the shared contract all three implement
├── docs/          BUILD_ORDER.md and design notes
├── django/        Django + DRF          (port 8000)
├── spring/        Spring Boot + JPA      (port 8080)
└── express/       Express + Prisma       (port 3000)
```

## What it does

- **Auth** — register, log in for a JWT, role-based access (customer vs admin).
- **Catalog** — browse products with search, category filter, sorting, and pagination; admins manage
  products and categories.
- **Cart** — add items, change quantities, remove them.
- **Checkout** — turn the cart into an order in a single transaction: it locks each product's stock,
  decrements it (a database `CHECK` makes overselling impossible), snapshots prices, and clears the cart.
  If anything fails, the whole order rolls back.
- **Orders** — see your orders and their details; admins update order status.

## The database — seeded automatically

`db/schema.sql` and `db/seed.sql` are the single source of truth. Each backend maps onto the schema
rather than owning it, and **every backend comes seeded with the same demo data out of the box** — the
exact data the Playground learning content references (5 categories, 17 products with stock, 3 users, and
order history).

**Running with Docker (recommended) — nothing to do:** `docker compose up` in any backend folder starts a
PostgreSQL and **loads `schema.sql` + `seed.sql` automatically** on first boot.

**Running a backend locally against your own PostgreSQL — one command:**

```bash
./db/setup.sh          # or:  make setup
```

This creates `ecommerce_db` and loads the schema and seed. (It uses `PGHOST`/`PGPORT`/`PGUSER`/`PGPASSWORD`
env vars, defaulting to `localhost:5433` / `postgres`.)

Seed logins (all use the password `Password123!`): `admin@shop.test` (admin), `alice@shop.test` and
`bob@shop.test` (customers).

## Running a backend

From the repo root:

```bash
make django     # or  make spring  /  make express   — runs the app + its DB in Docker (auto-seeds)
```

Each folder also has its own README with local setup. See `docs/BUILD_ORDER.md` for the full plan and the
shared contract.

## The contract

Every backend serves the same paths and shapes — see `api/openapi.yaml`, and each app also serves live
Swagger docs. In short:

```
POST /auth/register · POST /auth/login · GET /auth/me
GET  /products · GET /products/{id} · GET /categories        (+ admin writes)
GET  /cart · POST /cart/items · PATCH|DELETE /cart/items/{id}
POST /orders (checkout) · GET /orders · GET /orders/{id}      (+ admin status)
```
