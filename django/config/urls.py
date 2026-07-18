from django.urls import path
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView

from shop.views import (
    CartItemDetailView, CartItemsView, CartView, CategoryListView, HealthView,
    LoginView, MeView, OrderDetailView, OrderListCreateView, OrderStatusView,
    ProductDetailView, ProductListCreateView, RegisterView,
)

urlpatterns = [
    path("health", HealthView.as_view()),

    path("auth/register", RegisterView.as_view()),
    path("auth/login", LoginView.as_view()),
    path("auth/me", MeView.as_view()),

    path("products", ProductListCreateView.as_view()),
    path("products/<int:pk>", ProductDetailView.as_view()),
    path("categories", CategoryListView.as_view()),

    path("cart", CartView.as_view()),
    path("cart/items", CartItemsView.as_view()),
    path("cart/items/<int:pk>", CartItemDetailView.as_view()),

    path("orders", OrderListCreateView.as_view()),
    path("orders/<int:pk>", OrderDetailView.as_view()),
    path("orders/<int:pk>/status", OrderStatusView.as_view()),

    # API docs
    path("schema", SpectacularAPIView.as_view(), name="schema"),
    path("docs", SpectacularSwaggerView.as_view(url_name="schema")),
]
