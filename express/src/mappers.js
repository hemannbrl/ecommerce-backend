// Turn Prisma rows into the API's response shapes (see ../api/openapi.yaml).

export const productDto = (p) => ({
  id: Number(p.id),
  name: p.name,
  slug: p.slug,
  description: p.description,
  price: p.price.toFixed(2),
  category_id: p.categoryId == null ? null : Number(p.categoryId),
  is_active: p.isActive,
  stock: p.inventory?.quantity ?? 0,
});

export const cartDto = (cart, items) => {
  let total = 0;
  const lines = items.map((it) => {
    const lineTotal = Number(it.product.price) * it.quantity;
    total += lineTotal;
    return {
      id: Number(it.id),
      product_id: Number(it.productId),
      name: it.product.name,
      unit_price: it.product.price.toFixed(2),
      quantity: it.quantity,
      line_total: lineTotal.toFixed(2),
    };
  });
  return { id: Number(cart.id), items: lines, total: total.toFixed(2) };
};

export const orderDto = (order, items) => ({
  id: Number(order.id),
  status: order.status,
  total: Number(order.total).toFixed(2),
  created_at: order.createdAt,
  items: items.map((it) => ({
    product_id: Number(it.productId),
    name: it.product?.name ?? "",
    quantity: it.quantity,
    unit_price: it.unitPrice.toFixed(2),
  })),
});
