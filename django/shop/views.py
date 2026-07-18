"""API endpoints implementing the shared contract (see api/openapi.yaml)."""
from django.db import IntegrityError
from django.db.models import Q
from django.utils.text import slugify
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework.response import Response
from rest_framework.views import APIView

from . import services
from .auth import hash_password, make_token, verify_password
from .models import Cart, CartItem, Category, Inventory, Order, Product, User
from .permissions import IsAdmin, IsAuthenticated
from .serializers import (
    AuthResponseSerializer, CartItemWriteSerializer, CartSerializer,
    CategorySerializer, LoginSerializer, OrderSerializer, OrderStatusSerializer,
    ProductPageSerializer, ProductSerializer, ProductWriteSerializer,
    QuantitySerializer, RegisterSerializer, UserSerializer,
)

SORTS = {"price": "price", "-price": "-price", "name": "name",
         "-name": "-name", "newest": "-created_at"}


def _user_dict(user):
    return {"id": user.id, "email": user.email, "role": user.role}


def _unique_slug(name):
    base = slugify(name) or "product"
    slug, n = base, 1
    while Product.objects.filter(slug=slug).exists():
        n += 1
        slug = f"{base}-{n}"
    return slug


# --- health ---
class HealthView(APIView):
    @extend_schema(tags=["Health"], responses=OpenApiTypes.OBJECT)
    def get(self, request):
        return Response({"status": "ok"})


# --- auth ---
class RegisterView(APIView):
    @extend_schema(tags=["Auth"], request=RegisterSerializer,
                   responses={201: AuthResponseSerializer})
    def post(self, request):
        data = RegisterSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        try:
            user = User.objects.create(
                email=data.validated_data["email"],
                password_hash=hash_password(data.validated_data["password"]),
                role="customer",
            )
        except IntegrityError:
            return Response({"detail": "Email already registered"}, status=409)
        services.get_or_create_cart(user)
        return Response({"token": make_token(user), "user": _user_dict(user)}, status=201)


class LoginView(APIView):
    @extend_schema(tags=["Auth"], request=LoginSerializer,
                   responses={200: AuthResponseSerializer})
    def post(self, request):
        data = LoginSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        try:
            user = User.objects.get(email=data.validated_data["email"])
        except User.DoesNotExist:
            return Response({"detail": "Invalid credentials"}, status=401)
        if not verify_password(data.validated_data["password"], user.password_hash):
            return Response({"detail": "Invalid credentials"}, status=401)
        return Response({"token": make_token(user), "user": _user_dict(user)})


class MeView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=["Auth"], responses=UserSerializer)
    def get(self, request):
        return Response(_user_dict(request.user))


# --- catalog ---
class ProductListCreateView(APIView):
    def get_permissions(self):
        return [IsAdmin()] if self.request.method == "POST" else []

    @extend_schema(
        tags=["Catalog"], responses=ProductPageSerializer,
        parameters=[
            OpenApiParameter("search", str), OpenApiParameter("category", str),
            OpenApiParameter("sort", str, enum=list(SORTS)),
            OpenApiParameter("page", int), OpenApiParameter("page_size", int),
        ],
    )
    def get(self, request):
        qs = Product.objects.select_related("inventory").filter(is_active=True)
        search = request.query_params.get("search")
        if search:
            qs = qs.filter(Q(name__icontains=search) | Q(description__icontains=search))
        category = request.query_params.get("category")
        if category:
            qs = qs.filter(category__slug=category)
        qs = qs.order_by(SORTS.get(request.query_params.get("sort"), "id"))

        try:
            page = max(1, int(request.query_params.get("page", 1)))
            page_size = min(100, max(1, int(request.query_params.get("page_size", 20))))
        except ValueError:
            return Response({"detail": "Invalid pagination"}, status=400)
        total = qs.count()
        start = (page - 1) * page_size
        results = ProductSerializer(qs[start:start + page_size], many=True).data
        return Response({"results": results, "page": page,
                         "page_size": page_size, "total": total})

    @extend_schema(tags=["Catalog"], request=ProductWriteSerializer,
                   responses={201: ProductSerializer})
    def post(self, request):
        data = ProductWriteSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        v = data.validated_data
        product = Product.objects.create(
            name=v["name"], slug=_unique_slug(v["name"]),
            description=v.get("description", ""), price=v["price"],
            category_id=v.get("category_id"), is_active=v.get("is_active", True),
        )
        Inventory.objects.create(product=product, quantity=v.get("stock", 0))
        return Response(ProductSerializer(product).data, status=201)


class ProductDetailView(APIView):
    def get_permissions(self):
        return [] if self.request.method == "GET" else [IsAdmin()]

    def _get(self, pk):
        return Product.objects.select_related("inventory").filter(pk=pk).first()

    @extend_schema(tags=["Catalog"], responses=ProductSerializer)
    def get(self, request, pk):
        product = self._get(pk)
        if not product:
            return Response({"detail": "Not found"}, status=404)
        return Response(ProductSerializer(product).data)

    @extend_schema(tags=["Catalog"], request=ProductWriteSerializer,
                   responses=ProductSerializer)
    def patch(self, request, pk):
        product = self._get(pk)
        if not product:
            return Response({"detail": "Not found"}, status=404)
        data = ProductWriteSerializer(data=request.data, partial=True)
        data.is_valid(raise_exception=True)
        for field in ("name", "description", "price", "category_id", "is_active"):
            if field in data.validated_data:
                setattr(product, field, data.validated_data[field])
        product.save()
        if "stock" in data.validated_data:
            inv, _ = Inventory.objects.get_or_create(product=product)
            inv.quantity = data.validated_data["stock"]
            inv.save()
        return Response(ProductSerializer(self._get(pk)).data)

    @extend_schema(tags=["Catalog"], responses={204: None})
    def delete(self, request, pk):
        product = self._get(pk)
        if not product:
            return Response({"detail": "Not found"}, status=404)
        product.delete()
        return Response(status=204)


class CategoryListView(APIView):
    @extend_schema(tags=["Catalog"], responses=CategorySerializer(many=True))
    def get(self, request):
        return Response(CategorySerializer(Category.objects.order_by("name"), many=True).data)


# --- cart ---
class CartView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=["Cart"], responses=CartSerializer)
    def get(self, request):
        cart = services.get_or_create_cart(request.user)
        return Response(services.cart_to_dict(cart))


class CartItemsView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=["Cart"], request=CartItemWriteSerializer, responses=CartSerializer)
    def post(self, request):
        data = CartItemWriteSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        product = Product.objects.filter(pk=data.validated_data["product_id"]).first()
        if not product:
            return Response({"detail": "Product not found"}, status=404)
        cart = services.get_or_create_cart(request.user)
        item, created = CartItem.objects.get_or_create(
            cart=cart, product=product,
            defaults={"quantity": data.validated_data["quantity"]},
        )
        if not created:
            item.quantity += data.validated_data["quantity"]
            item.save()
        return Response(services.cart_to_dict(cart))


class CartItemDetailView(APIView):
    permission_classes = [IsAuthenticated]

    def _item(self, request, pk):
        return CartItem.objects.filter(pk=pk, cart__user=request.user).first()

    @extend_schema(tags=["Cart"], request=QuantitySerializer, responses=CartSerializer)
    def patch(self, request, pk):
        item = self._item(request, pk)
        if not item:
            return Response({"detail": "Not found"}, status=404)
        data = QuantitySerializer(data=request.data)
        data.is_valid(raise_exception=True)
        item.quantity = data.validated_data["quantity"]
        item.save()
        return Response(services.cart_to_dict(item.cart))

    @extend_schema(tags=["Cart"], responses=CartSerializer)
    def delete(self, request, pk):
        item = self._item(request, pk)
        if not item:
            return Response({"detail": "Not found"}, status=404)
        cart = item.cart
        item.delete()
        return Response(services.cart_to_dict(cart))


# --- orders ---
class OrderListCreateView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=["Orders"], responses=OrderSerializer(many=True))
    def get(self, request):
        orders = Order.objects.filter(user=request.user).order_by("-id")
        return Response([services.order_to_dict(o) for o in orders])

    @extend_schema(tags=["Orders"], request=None, responses={201: OrderSerializer},
                   description="Checkout: turn the cart into an order atomically.")
    def post(self, request):
        order = services.checkout(request.user)   # ConflictError -> 409 via handler
        return Response(services.order_to_dict(order), status=201)


class OrderDetailView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(tags=["Orders"], responses=OrderSerializer)
    def get(self, request, pk):
        order = Order.objects.filter(pk=pk).first()
        if not order:
            return Response({"detail": "Not found"}, status=404)
        if order.user_id != request.user.id and not request.user.is_admin:
            return Response({"detail": "Forbidden"}, status=403)
        return Response(services.order_to_dict(order))


class OrderStatusView(APIView):
    permission_classes = [IsAdmin]

    @extend_schema(tags=["Orders"], request=OrderStatusSerializer, responses=OrderSerializer)
    def patch(self, request, pk):
        order = Order.objects.filter(pk=pk).first()
        if not order:
            return Response({"detail": "Not found"}, status=404)
        data = OrderStatusSerializer(data=request.data)
        data.is_valid(raise_exception=True)
        order.status = data.validated_data["status"]
        order.save(update_fields=["status"])
        return Response(services.order_to_dict(order))
