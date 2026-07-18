import pytest

from shop.models import Inventory

pytestmark = pytest.mark.django_db


def test_add_to_cart_and_view(make_user, auth, make_product):
    user = make_user(email="c1@shop.test")
    p = make_product(name="Thing", price="10.00", stock=5)
    client = auth(user)
    resp = client.post("/cart/items", {"product_id": p.id, "quantity": 2}, format="json")
    assert resp.status_code == 200
    assert resp.data["total"] == "20.00"
    assert resp.data["items"][0]["quantity"] == 2


def test_adding_same_product_increments(make_user, auth, make_product):
    user = make_user(email="c2@shop.test")
    p = make_product(name="Thing2", price="10.00", stock=5)
    client = auth(user)
    client.post("/cart/items", {"product_id": p.id, "quantity": 1}, format="json")
    resp = client.post("/cart/items", {"product_id": p.id, "quantity": 2}, format="json")
    assert resp.data["items"][0]["quantity"] == 3


def test_checkout_decrements_stock_and_clears_cart(make_user, auth, make_product):
    user = make_user(email="c3@shop.test")
    p = make_product(name="Thing3", price="10.00", stock=5)
    client = auth(user)
    client.post("/cart/items", {"product_id": p.id, "quantity": 2}, format="json")

    resp = client.post("/orders")
    assert resp.status_code == 201
    assert resp.data["total"] == "20.00"
    assert Inventory.objects.get(product_id=p.id).quantity == 3      # 5 - 2
    assert client.get("/cart").data["items"] == []                  # cart emptied


def test_checkout_empty_cart_409(make_user, auth):
    user = make_user(email="c4@shop.test")
    assert auth(user).post("/orders").status_code == 409


def test_checkout_insufficient_stock_409_and_no_change(make_user, auth, make_product):
    user = make_user(email="c5@shop.test")
    p = make_product(name="Scarce", price="10.00", stock=1)
    client = auth(user)
    client.post("/cart/items", {"product_id": p.id, "quantity": 5}, format="json")  # more than stock

    resp = client.post("/orders")
    assert resp.status_code == 409
    assert Inventory.objects.get(product_id=p.id).quantity == 1      # untouched
