"""Business logic: cart management and the atomic checkout transaction."""
from decimal import Decimal

from django.db import IntegrityError, transaction

from .errors import ConflictError
from .models import Cart, CartItem, Inventory, Order, OrderItem, Product


def get_or_create_cart(user) -> Cart:
    cart, _ = Cart.objects.get_or_create(user=user)
    return cart


def cart_to_dict(cart: Cart) -> dict:
    items, total = [], Decimal("0")
    for item in cart.items.select_related("product").order_by("id"):
        line = item.product.price * item.quantity
        total += line
        items.append({
            "id": item.id,
            "product_id": item.product_id,
            "name": item.product.name,
            "unit_price": str(item.product.price),
            "quantity": item.quantity,
            "line_total": str(line),
        })
    return {"id": cart.id, "items": items, "total": str(total)}


def order_to_dict(order: Order) -> dict:
    items = [{
        "product_id": it.product_id,
        "name": it.product.name,
        "quantity": it.quantity,
        "unit_price": str(it.unit_price),
    } for it in order.items.select_related("product").order_by("id")]
    return {
        "id": order.id,
        "status": order.status,
        "total": str(order.total),
        "created_at": order.created_at.isoformat(),
        "items": items,
    }


@transaction.atomic
def checkout(user) -> Order:
    """Turn the user's cart into an order, atomically.

    Locks each product's stock row, decrements it (the DB CHECK blocks
    overselling), snapshots unit prices, records the order, and clears the cart.
    Any failure rolls the whole thing back.
    """
    cart = get_or_create_cart(user)
    lines = list(cart.items.select_related("product").order_by("id"))
    if not lines:
        raise ConflictError("Cart is empty")

    order = Order.objects.create(user=user, status="pending", total=0)
    total = Decimal("0")
    for line in lines:
        # lock this product's stock row for the life of the transaction
        stock = Inventory.objects.select_for_update().get(product_id=line.product_id)
        product = line.product

        OrderItem.objects.create(
            order=order, product=product,
            quantity=line.quantity, unit_price=product.price,
        )
        stock.quantity -= line.quantity        # CHECK (quantity >= 0) guards this
        try:
            stock.save(update_fields=["quantity", "updated_at"])
        except IntegrityError:
            raise ConflictError("Insufficient stock")
        total += product.price * line.quantity

    order.total = total
    order.save(update_fields=["total"])
    cart.items.all().delete()                  # empty the cart
    return order
