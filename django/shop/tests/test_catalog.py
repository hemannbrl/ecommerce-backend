import pytest

pytestmark = pytest.mark.django_db


def test_list_products_paginates(api, make_product):
    for i in range(3):
        make_product(name=f"Item {i}", price="5.00", stock=10)
    resp = api.get("/products?page_size=2")
    assert resp.status_code == 200
    assert resp.data["total"] == 3
    assert len(resp.data["results"]) == 2
    assert resp.data["page_size"] == 2


def test_search_filters_by_name(api, make_product):
    make_product(name="Blue Mug", price="5.00")
    make_product(name="Red Plate", price="5.00")
    resp = api.get("/products?search=mug")
    assert resp.data["total"] == 1
    assert resp.data["results"][0]["name"] == "Blue Mug"


def test_product_detail_and_404(api, make_product):
    p = make_product(name="Gadget", price="12.00", stock=7)
    ok = api.get(f"/products/{p.id}")
    assert ok.status_code == 200
    assert ok.data["stock"] == 7
    assert api.get("/products/999999").status_code == 404


def test_admin_can_create_product(make_user, auth):
    admin = make_user(email="admin@shop.test", role="admin")
    resp = auth(admin).post("/products", {"name": "New", "price": "3.50", "stock": 4}, format="json")
    assert resp.status_code == 201
    assert resp.data["slug"] == "new"
    assert resp.data["stock"] == 4


def test_customer_cannot_create_product_403(make_user, auth):
    customer = make_user(email="cust@shop.test", role="customer")
    resp = auth(customer).post("/products", {"name": "X", "price": "1.00"}, format="json")
    assert resp.status_code == 403


def test_create_product_requires_auth(api):
    assert api.post("/products", {"name": "X", "price": "1.00"}, format="json").status_code in (401, 403)
