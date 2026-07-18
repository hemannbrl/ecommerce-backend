import pytest

pytestmark = pytest.mark.django_db


def test_register_returns_token_and_user(api):
    resp = api.post("/auth/register", {"email": "new@shop.test", "password": "secretpw1"}, format="json")
    assert resp.status_code == 201
    assert resp.data["user"]["email"] == "new@shop.test"
    assert resp.data["user"]["role"] == "customer"
    assert resp.data["token"]


def test_register_duplicate_email_conflicts(api, make_user):
    make_user(email="dupe@shop.test")
    resp = api.post("/auth/register", {"email": "dupe@shop.test", "password": "secretpw1"}, format="json")
    assert resp.status_code == 409


def test_login_success_and_me(api, make_user, auth):
    user = make_user(email="log@shop.test", password="Password123!")
    resp = api.post("/auth/login", {"email": "log@shop.test", "password": "Password123!"}, format="json")
    assert resp.status_code == 200
    me = auth(user).get("/auth/me")
    assert me.status_code == 200
    assert me.data["email"] == "log@shop.test"


def test_login_wrong_password_401(api, make_user):
    make_user(email="bad@shop.test", password="Password123!")
    resp = api.post("/auth/login", {"email": "bad@shop.test", "password": "wrong"}, format="json")
    assert resp.status_code == 401


def test_me_requires_auth(api):
    assert api.get("/auth/me").status_code == 401
