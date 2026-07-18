"""Request/response shapes. Output shapes match api/openapi.yaml exactly."""
from django.utils.text import slugify
from rest_framework import serializers

from .models import Category, Product


# --- auth ---
class RegisterSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(min_length=8, write_only=True)


class LoginSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(write_only=True)


class UserSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    email = serializers.EmailField()
    role = serializers.CharField()


# --- catalog ---
class CategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = Category
        fields = ["id", "name", "slug"]


class ProductSerializer(serializers.ModelSerializer):
    stock = serializers.SerializerMethodField()

    class Meta:
        model = Product
        fields = ["id", "name", "slug", "description", "price",
                  "category_id", "is_active", "stock"]

    def get_stock(self, obj):
        inv = getattr(obj, "inventory", None)
        return inv.quantity if inv else 0


class ProductWriteSerializer(serializers.Serializer):
    name = serializers.CharField(max_length=200)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    price = serializers.DecimalField(max_digits=10, decimal_places=2, min_value=0)
    category_id = serializers.IntegerField(required=False, allow_null=True)
    is_active = serializers.BooleanField(required=False, default=True)
    stock = serializers.IntegerField(required=False, min_value=0, default=0)

    def validate_name(self, value):
        # derive a unique-ish slug; the DB UNIQUE constraint is the real guard
        self._slug = slugify(value)
        return value


# --- cart ---
class CartItemWriteSerializer(serializers.Serializer):
    product_id = serializers.IntegerField()
    quantity = serializers.IntegerField(min_value=1)


class QuantitySerializer(serializers.Serializer):
    quantity = serializers.IntegerField(min_value=1)


# --- orders ---
class OrderStatusSerializer(serializers.Serializer):
    STATUSES = ["pending", "paid", "shipped", "delivered", "cancelled"]
    status = serializers.ChoiceField(choices=STATUSES)


# --- response shapes (used to document the API; responses are built as dicts) ---
class AuthResponseSerializer(serializers.Serializer):
    token = serializers.CharField()
    user = UserSerializer()


class ProductPageSerializer(serializers.Serializer):
    results = ProductSerializer(many=True)
    page = serializers.IntegerField()
    page_size = serializers.IntegerField()
    total = serializers.IntegerField()


class CartItemSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    product_id = serializers.IntegerField()
    name = serializers.CharField()
    unit_price = serializers.CharField()
    quantity = serializers.IntegerField()
    line_total = serializers.CharField()


class CartSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    items = CartItemSerializer(many=True)
    total = serializers.CharField()


class OrderItemSerializer(serializers.Serializer):
    product_id = serializers.IntegerField()
    name = serializers.CharField()
    quantity = serializers.IntegerField()
    unit_price = serializers.CharField()


class OrderSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    status = serializers.CharField()
    total = serializers.CharField()
    created_at = serializers.DateTimeField()
    items = OrderItemSerializer(many=True)
