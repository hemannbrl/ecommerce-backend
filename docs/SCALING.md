# Scaling & production-traffic runbook

One API, three stacks (Django, Spring Boot, Express) on one Postgres schema. They
scale the same way at the edges; the concurrency model differs per runtime.

## Concurrency model per stack

- **Django** — gunicorn now runs **`gthread` workers + threads** (`WEB_CONCURRENCY`), so a slow request doesn't tie up a whole worker. Scale workers to the box, and run multiple instances.
- **Spring Boot** — the embedded Tomcat is **already a thread pool** (default 200 threads), so one process handles high concurrency; tune `server.tomcat.threads.max` and the **HikariCP** pool to the DB.
- **Express** — Node is **single-threaded** (one event loop). It's async so one process serves lots of I/O-bound catalog reads, but to use multiple cores run **several replicas behind the load balancer** (or `node --cluster`/PM2). Prisma's pool is set via the `connection_limit` on `DATABASE_URL`.

## What holds traffic (all three)

- **Stateless, JWT-authenticated** → run N instances behind a load balancer.
- **Read-heavy catalog** is the traffic that has to scale, and it's cacheable — put a **CDN / reverse cache** in front of `GET /products` (short TTL) and most browsing never hits the app.
- **The no-oversell checkout** row-locks per product stock row, so it only serializes under contention on a *single* hot item's last units — it doesn't bottleneck normal traffic.
- **Persistent DB connections** — Django `CONN_MAX_AGE=60`, Hikari pool (Spring), Prisma pool (Express). Keep total connections (`instances × workers/threads × pool`) under Postgres `max_connections`, or front it with **PgBouncer**.

## Security headers per stack

- **Django** — `SecurityHeadersMiddleware` sets nosniff, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`; HSTS + SSL redirect enabled when `DEBUG` is off.
- **Express** — **helmet** (CSP disabled so Swagger UI at `/docs` still loads) sets HSTS, frame, nosniff, referrer, etc.
- **Spring** — Spring Security already sends `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY` by default. To add Referrer-Policy + Permissions-Policy, extend the `SecurityFilterChain`:

  ```java
  http.headers(h -> h
      .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
      .permissionsPolicyHeader(p -> p.policy("geolocation=(), microphone=(), camera=(), payment=()")));
  ```

CI runs `pip-audit` (Django) + `npm audit` (Express); Dependabot covers pip, npm, and maven.

## Insecure defaults to flip in production (all three)

The stacks fall back to a built-in `dev-only-…` JWT/secret if the env var is unset, and Django defaults `DEBUG=true`/`ALLOWED_HOSTS=*`. Always set real values in production (the docker-compose files use `change-me` placeholders and set `DEBUG=false`); consider failing startup if the secret is still the default.

## Load testing

`loadtest/locustfile.py` works against **any** of the three (same contract). It hits the public catalog + health:

```
pip install locust
make django            # or make spring / make express
locust -f loadtest/locustfile.py --host http://localhost:8000 \
    --users 500 --spawn-rate 50 --run-time 2m --headless
```

Run it against each stack to compare, and again with a CDN in front of `/products` — that's the difference between "handles a spike" and "melts."

## Production checklist

- [ ] Real `JWT_SECRET`/`SECRET_KEY`, `DEBUG=false`, real `ALLOWED_HOSTS` (Django).
- [ ] Managed Postgres; PgBouncer once connection count climbs; pools sized under `max_connections`.
- [ ] CDN in front of `GET /products` (+ `/products/{id}`).
- [ ] Django: `WEB_CONCURRENCY` tuned. Spring: Tomcat + Hikari sized. Express: run ≥N replicas.
- [ ] Run the Locust test per stack; record the numbers.
