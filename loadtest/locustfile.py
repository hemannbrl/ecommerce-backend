"""Load test for the e-commerce API (Locust).

Because all three stacks (Django, Spring, Express) expose the *same* OpenAPI
contract, this one file load-tests whichever one you point it at. It hits the
public, read-heavy path — the product catalog + health — which is what a store's
traffic actually looks like (browsing >> checkout).

    pip install locust
    # boot a stack: make django  (or  make spring  /  make express)
    locust -f loadtest/locustfile.py --host http://localhost:8000
    # headless:
    locust -f loadtest/locustfile.py --host http://localhost:8000 \
        --users 500 --spawn-rate 50 --run-time 2m --headless

For a real number, point --host at a deployed stack backed by managed Postgres
(see docs/SCALING.md). The no-oversell checkout row-locks per product, so its
throughput is bounded only under contention on a single hot item — the catalog
reads below are the part that has to scale to thousands.
"""

from locust import HttpUser, between, task


class ShopperUser(HttpUser):
    wait_time = between(0.3, 1.5)

    @task(6)
    def list_products(self):
        self.client.get("/products", name="/products")

    @task(2)
    def product_page(self):
        # id 1 exists in the seed data; adjust if your seed differs.
        self.client.get("/products/1", name="/products/{id}")

    @task(1)
    def health(self):
        self.client.get("/health", name="/health")
