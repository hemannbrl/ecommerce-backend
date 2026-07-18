"""Test fixtures.

The models are managed = False (they map onto an existing schema), so Django's
migrations create no tables. Instead we load db/schema.sql into the test
database once per session, then each test builds the data it needs.
"""
from decimal import Decimal
from pathlib import Path

import pytest
from django.db import connection
from rest_framework.test import APIClient

from shop.auth import hash_password, make_token
from shop.models import Category, Inventory, Product, User

SCHEMA = Path(__file__).resolve().parent.parent / "db" / "schema.sql"


@pytest.fixture(scope="session")
def django_db_setup(django_db_setup, django_db_blocker):
    with django_db_blocker.unblock(), connection.cursor() as cur:
        cur.execute(SCHEMA.read_text())


@pytest.fixture
def api():
    return APIClient()


@pytest.fixture
def make_user(db):
    def _make(email="u@shop.test", role="customer", password="Password123!"):
        return User.objects.create(
            email=email, password_hash=hash_password(password), role=role,
        )
    return _make


@pytest.fixture
def auth(api):
    def _auth(user):
        api.credentials(HTTP_AUTHORIZATION=f"Bearer {make_token(user)}")
        return api
    return _auth


@pytest.fixture
def make_product(db):
    def _make(name="Widget", price="10.00", stock=5, category=None):
        if category is None:
            category = Category.objects.create(name=f"Cat {name}", slug=f"cat-{name}".lower())
        p = Product.objects.create(
            name=name, slug=name.lower().replace(" ", "-"),
            description="", price=Decimal(price), category=category, is_active=True,
        )
        Inventory.objects.create(product=p, quantity=stock)
        return p
    return _make
