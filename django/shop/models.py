"""Models mapped onto the existing ecommerce_db schema (managed = False)."""
from django.db import models


class User(models.Model):
    email = models.EmailField(max_length=255, unique=True)
    password_hash = models.CharField(max_length=255)
    role = models.CharField(max_length=20, default="customer")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        managed = False
        db_table = "users"

    # DRF treats request.user as authenticated when this is truthy
    is_authenticated = True

    @property
    def is_admin(self):
        return self.role == "admin"

    def __str__(self):
        return self.email


class Category(models.Model):
    name = models.CharField(max_length=100, unique=True)
    slug = models.CharField(max_length=120, unique=True)

    class Meta:
        managed = False
        db_table = "categories"


class Product(models.Model):
    name = models.CharField(max_length=200)
    slug = models.CharField(max_length=220, unique=True)
    description = models.TextField(default="")
    price = models.DecimalField(max_digits=10, decimal_places=2)
    category = models.ForeignKey(
        Category, on_delete=models.SET_NULL, null=True,
        db_column="category_id", related_name="products",
    )
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        managed = False
        db_table = "products"


class Inventory(models.Model):
    product = models.OneToOneField(
        Product, on_delete=models.CASCADE, primary_key=True,
        db_column="product_id", related_name="inventory",
    )
    quantity = models.IntegerField(default=0)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        managed = False
        db_table = "inventory"


class Cart(models.Model):
    user = models.OneToOneField(
        User, on_delete=models.CASCADE, db_column="user_id", related_name="cart",
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        managed = False
        db_table = "carts"


class CartItem(models.Model):
    cart = models.ForeignKey(
        Cart, on_delete=models.CASCADE, db_column="cart_id", related_name="items",
    )
    product = models.ForeignKey(
        Product, on_delete=models.CASCADE, db_column="product_id", related_name="+",
    )
    quantity = models.IntegerField()

    class Meta:
        managed = False
        db_table = "cart_items"


class Order(models.Model):
    user = models.ForeignKey(
        User, on_delete=models.PROTECT, db_column="user_id", related_name="orders",
    )
    status = models.CharField(max_length=20, default="pending")
    total = models.DecimalField(max_digits=12, decimal_places=2, default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        managed = False
        db_table = "orders"


class OrderItem(models.Model):
    order = models.ForeignKey(
        Order, on_delete=models.CASCADE, db_column="order_id", related_name="items",
    )
    product = models.ForeignKey(
        Product, on_delete=models.PROTECT, db_column="product_id", related_name="+",
    )
    quantity = models.IntegerField()
    unit_price = models.DecimalField(max_digits=10, decimal_places=2)

    class Meta:
        managed = False
        db_table = "order_items"
