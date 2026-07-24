# E-commerce backend

[![Django CI](https://github.com/hemannbrl/ecommerce-backend/actions/workflows/django-ci.yml/badge.svg)](https://github.com/hemannbrl/ecommerce-backend/actions/workflows/django-ci.yml)
[![Spring Boot CI](https://github.com/hemannbrl/ecommerce-backend/actions/workflows/spring-ci.yml/badge.svg)](https://github.com/hemannbrl/ecommerce-backend/actions/workflows/spring-ci.yml)
[![Express CI](https://github.com/hemannbrl/ecommerce-backend/actions/workflows/express-ci.yml/badge.svg)](https://github.com/hemannbrl/ecommerce-backend/actions/workflows/express-ci.yml)

This repo is one e-commerce API implemented three ways: in Django, Spring Boot, and Express. All three
talk to the same PostgreSQL database, expose the same REST endpoints, and pass the same kinds of tests. You
can boot any one of them and hit it with the same requests.

I built it to get past "I've used Django" or "I know Spring" and actually compare the three ecosystems on a
problem with some real teeth in it. The headline feature is a checkout that stays correct when two people
try to buy the last item at the same time, which turns out to be a good way to show how each stack handles
transactions, locking, and validation. The interesting part isn't picking a winner. It's seeing the same
design come out idiomatic in each one.

## Highlights

- **One API, three stacks** — Django + DRF, Spring Boot + JPA, and Express + Prisma, all mapped onto one shared
  PostgreSQL schema and one OpenAPI contract, so the only differences are idiomatic ones.
- **A checkout that can't oversell** — turning a cart into an order runs in a single transaction that row-locks
  each stock row (`SELECT … FOR UPDATE` / `@Lock(PESSIMISTIC_WRITE)` / `select_for_update`), with a database
  `CHECK (quantity >= 0)` as the backstop. Every stack proves it with a **concurrency test** that fires two
  checkouts at the last unit and asserts exactly one wins.
- **Secure by default** — JWT over bcrypt, role-based access (customer vs admin), and object-level ownership
  checks so a user only ever sees their own orders.
- **Tested and CI'd** — each stack runs its suite (including the concurrency test) against a real PostgreSQL on
  GitHub Actions.
- **One command to run** — `make django` / `make spring` / `make express` boots the app and its database in
  Docker, seeded, with live Swagger docs.

## What the API does

It's a small but complete storefront backend:

- Register and log in. Auth is a JWT; routes check it and enforce customer-vs-admin roles.
- Browse the catalog with search, category filters, sorting, and pagination. Admins can create and edit
  products and categories.
- Manage a cart: add items, change quantities, remove them.
- Check out. This is the one that matters (see below).
- Look at your own orders. Admins can move an order through its statuses.

## The checkout is the point

Turning a cart into an order runs as a single database transaction. It locks each product's stock row,
checks there's enough, decrements it, snapshots the price onto the order line, and empties the cart. If any
line fails, the whole thing rolls back and no order is created.

Two safety nets sit under that. The row lock (`SELECT ... FOR UPDATE`, `@Lock`, or `select_for_update`
depending on the stack) serialises concurrent checkouts so two shoppers can't both grab the last unit. And
a `CHECK (quantity >= 0)` constraint on the database means that even if application code ever got the check
wrong, the database itself refuses to let stock go negative. Belt and suspenders.

Each backend expresses this the way its ecosystem does, but the guarantee is identical, and there's a
concurrency test that fires two checkouts at the same row and asserts exactly one wins.

## The database is shared

`db/schema.sql` and `db/seed.sql` are the source of truth. None of the three apps own the schema; they map
onto it (Prisma introspection, JPA with `ddl-auto: none`, Django models with `managed = False`). That's
what keeps all three honest against one contract instead of three drifting copies.

Everything comes seeded with the same demo data: 5 categories, 17 products with stock, and 3 users with a
bit of order history. The seed logins all use the password `Password123!`:

- `admin@shop.test` — admin
- `alice@shop.test`, `bob@shop.test` — customers

## Running it

With Docker, there's nothing to set up. From the repo root:

```bash
make django      # http://localhost:8000
make spring      # http://localhost:8080
make express     # http://localhost:3000
```

Each target starts the app and its own PostgreSQL, and loads the schema and seed on first boot. Each app
also serves live Swagger docs so you can poke at the endpoints in a browser.

Each compose file publishes Postgres on host port `5433` (to steer clear of a local `5432`). If `5433` is
already taken on your machine, edit the `ports:` line in that stack's `docker-compose.yml`, e.g.
`"5444:5432"`. Only one backend at a time can hold a given host port.

If you'd rather run a backend against your own PostgreSQL, create and load the shared database once:

```bash
make setup       # creates ecommerce_db and loads schema + seed
```

That reads `PGHOST`/`PGPORT`/`PGUSER`/`PGPASSWORD` and defaults to `localhost:5433` with `postgres`/`postgres`.
Each backend folder has its own README with the local (non-Docker) run instructions.

## What each one is built with

| Stack       | Framework            | Data layer           | Auth               | Port |
|-------------|----------------------|----------------------|--------------------|------|
| Django      | Django 6 + DRF       | ORM, `managed=False` | PyJWT + bcrypt     | 8000 |
| Spring Boot | Spring Boot 3.4 (Java 21) | Spring Data JPA | jjwt + Spring Security | 8080 |
| Express     | Express 5            | Prisma 6             | jsonwebtoken + bcrypt | 3000 |

They deliberately don't share a line of code. The only shared things are the database and the API contract.

## Tests and CI

Every stack has tests, including the double-booking concurrency case, and each one runs on GitHub Actions
against a real PostgreSQL service on push and pull request. The workflows are path-filtered, so touching the
Django folder only runs the Django job.

- Django: pytest + DRF's test client
- Spring Boot: JUnit + Testcontainers
- Express: Vitest + Supertest

## Layout

```
ecommerce-backend/
├── db/         schema.sql + seed.sql, the shared database
├── api/        openapi.yaml, the contract all three implement
├── docs/       build order and design notes
├── django/     Django + DRF        (8000)
├── spring/     Spring Boot + JPA   (8080)
└── express/    Express + Prisma    (3000)
```

## The endpoints

All three serve the same paths and JSON shapes. Full detail is in `api/openapi.yaml` and each app's Swagger
UI, but the shape of it is:

```
POST /auth/register    POST /auth/login    GET /auth/me
GET  /products         GET  /products/{id}    GET /categories        (+ admin writes)
GET  /cart             POST /cart/items       PATCH|DELETE /cart/items/{id}
POST /orders           GET  /orders           GET /orders/{id}       (+ admin status update)
```

See `docs/BUILD_ORDER.md` for how it was built and the reasoning behind the shared contract.
