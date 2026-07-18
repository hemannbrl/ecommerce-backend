"""The important test: two shoppers race for the last unit in stock.

Runs two real checkouts on separate threads/connections against Postgres.
The row lock (SELECT ... FOR UPDATE) plus the CHECK constraint must guarantee
exactly one succeeds and stock never goes negative.
"""
import threading

import pytest

from shop import services
from shop.errors import ConflictError
from shop.models import Cart, CartItem, Inventory
from django.db import connection


@pytest.mark.django_db(transaction=True)
def test_two_checkouts_cannot_oversell_last_unit(make_user, make_product):
    product = make_product(name="LastOne", price="10.00", stock=1)   # only ONE in stock
    buyers = []
    for i in range(2):
        user = make_user(email=f"race{i}@shop.test")
        cart = Cart.objects.create(user=user)
        CartItem.objects.create(cart=cart, product=product, quantity=1)
        buyers.append(user)

    results = {}
    barrier = threading.Barrier(len(buyers))   # release both threads at once

    def run(user):
        barrier.wait()
        try:
            services.checkout(user)
            results[user.id] = "ok"
        except ConflictError:
            results[user.id] = "rejected"
        finally:
            connection.close()   # each thread has its own connection

    threads = [threading.Thread(target=run, args=(u,)) for u in buyers]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    outcomes = sorted(results.values())
    assert outcomes == ["ok", "rejected"]                       # exactly one won
    assert Inventory.objects.get(product_id=product.id).quantity == 0   # never negative
