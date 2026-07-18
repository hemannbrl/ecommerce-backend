# E-Commerce backend — Spring Boot

The Spring Boot implementation of the shared e-commerce API. Java + Spring Boot over the shared
`ecommerce_db` schema, with JWT auth, a product catalog, a cart, and an atomic checkout that won't
oversell stock.

One of three interchangeable backends (see the repo root); it implements the same contract as the
Django and Express versions.

## Stack

- Spring Boot 3.4 (Web, Data JPA, Validation, Security)
- PostgreSQL — JPA/Hibernate entities mapped onto the existing schema (`ddl-auto: none`)
- JWT auth (jjwt) with Spring Security; bcrypt passwords (`BCryptPasswordEncoder`)
- springdoc-openapi for Swagger UI
- JUnit + Testcontainers

## Running it

### With Docker (brings up Postgres too)

```bash
docker compose up --build
# API on http://localhost:8080, Swagger at http://localhost:8080/docs
```

The database container loads `../db/schema.sql` and `../db/seed.sql` on first start.

### Locally

First seed the shared database (creates it and loads schema + demo data) — from the repo root:

```bash
./db/setup.sh          # or  make setup
```

Then run the app:

```bash
./mvnw spring-boot:run
```

Config comes from `application.yml` / environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`,
`JWT_SECRET`). Seed logins (password `Password123!`): `admin@shop.test` (admin), `alice@shop.test`,
`bob@shop.test`.

## A quick tour

```bash
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"alice@shop.test","password":"Password123!"}'
# -> {"token":"...","user":{"id":2,"email":"alice@shop.test","role":"customer"}}

TOKEN=...
curl -s "localhost:8080/products?search=head&sort=price"
curl -s -X POST localhost:8080/cart/items -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{"productId":3,"quantity":1}'
curl -s -X POST localhost:8080/orders -H "Authorization: Bearer $TOKEN"
```

## How checkout works

`POST /orders` runs in a single `@Transactional` service method (`OrderService`):

1. Lock each product's stock row — a repository method annotated `@Lock(PESSIMISTIC_WRITE)`
   (`SELECT ... FOR UPDATE`).
2. Snapshot the price into an order line and decrement stock (`saveAndFlush` so the constraint is
   checked immediately).
3. The database `CHECK (quantity >= 0)` rejects any decrement that would oversell → mapped to HTTP 409.
4. On success the cart is cleared; on any failure the whole transaction rolls back.

`CheckoutConcurrencyTest` proves the guarantee: it runs two checkouts on separate threads racing for the
last unit and asserts exactly one wins and stock never goes negative.

## Auth note

The shared `users` table stores bcrypt hashes, so `BCryptPasswordEncoder` reads and writes the same
column the Django and Express backends use. A small `JwtAuthFilter` validates the bearer token and sets
the Spring Security context; role (`customer`/`admin`) gates the admin endpoints.

## Tests

```bash
./mvnw test
```

Testcontainers starts a real PostgreSQL, loads `db/schema.sql`, and the suite covers auth, catalog,
cart, checkout, and the concurrency guarantee (7 tests). Requires Docker running.

## Layout

```
spring/src/main/java/com/example/shop/
├── domain/            JPA entities over ecommerce_db
├── Repositories.java  Spring Data repositories (incl. the pessimistic-lock query)
├── Dto.java           request/response records
├── JwtService.java, JwtAuthFilter.java, SecurityConfig.java   auth + security
├── AuthService, CatalogService, CartService, OrderService     business logic
├── Controllers.java   the REST endpoints
└── ApiExceptionHandler.java   consistent {"detail": "..."} errors
```
