# Build order

One e-commerce backend, built three times — Django, Spring Boot, Express — all against the same
PostgreSQL database and the same API contract. This file is the plan and the running checklist.

## The idea

`db/schema.sql` and `api/openapi.yaml` are the single source of truth. Each backend maps onto the
same schema and implements the same endpoints with the same request/response shapes, so the three are
interchangeable behind the contract.

## Shared foundation (phase 1)

- [x] `db/schema.sql` — users, categories, products, inventory, carts, cart_items, orders, order_items
- [x] `db/seed.sql` — demo catalog, 3 bcrypt users, historical orders (login: `Password123!`)
- [x] `api/openapi.yaml` — the contract every backend implements
- [ ] root `README.md`, `docs/` set, `.env.example`

## The contract (all three identical)

| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/register`, `POST /auth/login` (JWT), `GET /auth/me` |
| Catalog | `GET /products` (search·filter·sort·paginate), `GET /products/{id}`, admin `POST/PATCH/DELETE`, `GET /categories` |
| Cart | `GET /cart`, `POST /cart/items`, `PATCH /cart/items/{id}`, `DELETE /cart/items/{id}` |
| Orders | `POST /orders` (checkout), `GET /orders`, `GET /orders/{id}`, admin `PATCH /orders/{id}/status` |
| Health | `GET /health` |

The **checkout** is the centrepiece: one transaction that locks each product's stock row
(`SELECT … FOR UPDATE`), decrements it (the `CHECK (quantity >= 0)` blocks overselling), snapshots unit
prices, creates the order, and clears the cart — all-or-nothing.

## Auth approach (shared)

Passwords are **bcrypt** hashes in `users.password_hash`, so the column is genuinely shared:

- **Spring Boot** — `BCryptPasswordEncoder`
- **Express** — `bcrypt`
- **Django** — verify bcrypt directly in the auth layer (custom API auth + SimpleJWT), not Django's
  default hasher, so it reads the same `$2b$` hashes.

JWT is a signed bearer token carrying the user id and role; role gates the admin endpoints.

## Per-backend checklist (each must reach the same bar)

Django (`/django`, port 8000) · Spring Boot (`/spring`, port 8080) · Express (`/express`, port 3000):

1. [ ] Scaffold + connect to `ecommerce_db`
2. [ ] Models/entities over the existing schema (ORM: Django ORM / JPA / Prisma)
3. [ ] Migrations owned by the app going forward (Django migrations / Flyway / Prisma migrate)
4. [ ] Auth: register, login (JWT), me; role-based authorization
5. [ ] Catalog: list with search/filter/sort/pagination, detail, admin CRUD
6. [ ] Cart: view, add, update qty, remove
7. [ ] Checkout: the atomic, row-locked order transaction
8. [ ] Orders: mine, detail (owner/admin), admin status update
9. [ ] Validation + consistent error responses
10. [ ] OpenAPI/Swagger served (drf-spectacular / springdoc / swagger-ui)
11. [ ] Tests: unit + integration + a **concurrency test proving no overselling**
12. [ ] Docker + docker-compose (app + postgres), `.env.example`
13. [ ] CI (GitHub Actions)
14. [ ] README (natural voice, setup + API + design notes)

## Conventions

- READMEs and docs read human — plain dev-notes voice, no AI-polished framing.
- No AI attribution anywhere — commits, PR bodies, files. The author writes every commit message.
- Secrets only from the environment; `.env.example` documents the variables, real `.env` is git-ignored.

## Build sequence

Django first (it pins the contract in the most batteries-included way), then Spring Boot, then Express.
Then the two Practice cards, whose content mirrors these repos exactly.

## Status

- **Django (`/django`, port 8000) — DONE.** Full contract implemented and verified live against
  `ecommerce_db`; DRF + PyJWT/bcrypt auth, managed=False models, drf-spectacular Swagger (0 schema
  errors), 17 pytest tests passing including the concurrency/no-oversell test, Dockerfile +
  docker-compose, CI workflow, README. The reference all three mirror.
- **Spring Boot (`/spring`, port 8080) — DONE.** Full contract verified live against `ecommerce_db`;
  Spring Security + jjwt JWT, BCryptPasswordEncoder over the shared bcrypt column, JPA entities
  (ddl-auto none) with `@Lock(PESSIMISTIC_WRITE)` checkout, springdoc Swagger at /docs, 7
  JUnit/Testcontainers tests passing including the concurrency/no-oversell test, Dockerfile +
  docker-compose, CI workflow, README.
- **Express (`/express`, port 3000) — DONE.** Full contract verified live against `ecommerce_db`;
  Express 5 + Prisma + jsonwebtoken/bcrypt + zod, checkout via a Prisma interactive transaction with a
  raw `SELECT ... FOR UPDATE` lock, swagger-ui serving the shared api/openapi.yaml at /docs, 9
  Vitest/Supertest tests passing including the concurrency/no-oversell test, Dockerfile (build-verified) +
  docker-compose, CI workflow, README.

**All three backends complete** — same contract, same database, identical behaviour, each idiomatic to
its stack. Next: the two Practice cards, mirroring these repos.
