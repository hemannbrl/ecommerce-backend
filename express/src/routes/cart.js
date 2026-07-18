import { Router } from "express";

import { requireAuth } from "../auth.js";
import { notFound, wrap } from "../errors.js";
import { cartDto } from "../mappers.js";
import { prisma } from "../prisma.js";
import { cartItems, getOrCreateCart } from "../services.js";
import { cartItemSchema, quantitySchema, validate } from "../validate.js";

const router = Router();
router.use(requireAuth);

async function respondCart(res, cart) {
  res.json(cartDto(cart, await cartItems(cart.id)));
}

router.get("/cart", wrap(async (req, res) => {
  const cart = await getOrCreateCart(req.user.id);
  await respondCart(res, cart);
}));

router.post("/cart/items", validate(cartItemSchema), wrap(async (req, res) => {
  const productId = BigInt(req.body.product_id);
  const { quantity } = req.body;
  const product = await prisma.product.findUnique({ where: { id: productId } });
  if (!product) throw notFound("Product not found");
  const cart = await getOrCreateCart(req.user.id);
  await prisma.cartItem.upsert({
    where: { cartId_productId: { cartId: cart.id, productId } },
    create: { cartId: cart.id, productId, quantity },
    update: { quantity: { increment: quantity } },
  });
  await respondCart(res, cart);
}));

router.patch("/cart/items/:id", validate(quantitySchema), wrap(async (req, res) => {
  const cart = await getOrCreateCart(req.user.id);
  const item = await prisma.cartItem.findUnique({ where: { id: BigInt(req.params.id) } });
  if (!item || item.cartId !== cart.id) throw notFound("Cart item not found");
  await prisma.cartItem.update({ where: { id: item.id }, data: { quantity: req.body.quantity } });
  await respondCart(res, cart);
}));

router.delete("/cart/items/:id", wrap(async (req, res) => {
  const cart = await getOrCreateCart(req.user.id);
  const item = await prisma.cartItem.findUnique({ where: { id: BigInt(req.params.id) } });
  if (!item || item.cartId !== cart.id) throw notFound("Cart item not found");
  await prisma.cartItem.delete({ where: { id: item.id } });
  await respondCart(res, cart);
}));

export default router;
