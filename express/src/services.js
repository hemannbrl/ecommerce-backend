import { conflict } from "./errors.js";
import { prisma } from "./prisma.js";

export async function getOrCreateCart(userId) {
  return prisma.cart.upsert({
    where: { userId },
    create: { userId },
    update: {},
  });
}

export async function cartItems(cartId) {
  return prisma.cartItem.findMany({
    where: { cartId },
    include: { product: true },
    orderBy: { id: "asc" },
  });
}

/**
 * Checkout: turn the cart into an order atomically. Inside one interactive
 * transaction, lock each product's stock row (SELECT ... FOR UPDATE), check it,
 * decrement it, snapshot the price, and clear the cart. Any throw rolls it back.
 * The database CHECK (quantity >= 0) is the final backstop against overselling.
 */
export async function checkout(userId) {
  const cart = await getOrCreateCart(userId);
  const items = await cartItems(cart.id);
  if (items.length === 0) throw conflict("Cart is empty");

  return prisma.$transaction(async (tx) => {
    const order = await tx.order.create({
      data: { userId, status: "pending", total: 0 },
    });

    let total = 0;
    for (const line of items) {
      // lock this stock row for the life of the transaction
      const rows = await tx.$queryRaw`
        SELECT quantity FROM inventory WHERE product_id = ${line.productId} FOR UPDATE`;
      const available = rows[0]?.quantity ?? 0;
      if (available < line.quantity) throw conflict("Insufficient stock");

      await tx.orderItem.create({
        data: {
          orderId: order.id,
          productId: line.productId,
          quantity: line.quantity,
          unitPrice: line.product.price,
        },
      });
      await tx.inventory.update({
        where: { productId: line.productId },
        data: { quantity: { decrement: line.quantity } },
      });
      total += Number(line.product.price) * line.quantity;
    }

    const updated = await tx.order.update({
      where: { id: order.id },
      data: { total: total.toFixed(2) },
    });
    await tx.cartItem.deleteMany({ where: { cartId: cart.id } });

    const orderItems = await tx.orderItem.findMany({
      where: { orderId: order.id },
      include: { product: true },
      orderBy: { id: "asc" },
    });
    return { order: updated, items: orderItems };
  });
}
