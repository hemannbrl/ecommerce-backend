import { Router } from "express";

import { requireAdmin, requireAuth } from "../auth.js";
import { forbidden, notFound, wrap } from "../errors.js";
import { orderDto } from "../mappers.js";
import { prisma } from "../prisma.js";
import { checkout } from "../services.js";
import { orderStatusSchema, validate } from "../validate.js";

const router = Router();

router.get("/orders", requireAuth, wrap(async (req, res) => {
  const orders = await prisma.order.findMany({
    where: { userId: req.user.id },
    include: { items: { include: { product: true } } },
    orderBy: { id: "desc" },
  });
  res.json(orders.map((o) => orderDto(o, o.items)));
}));

router.post("/orders", requireAuth, wrap(async (req, res) => {
  const { order, items } = await checkout(req.user.id);   // conflict -> 409 via error handler
  res.status(201).json(orderDto(order, items));
}));

router.get("/orders/:id", requireAuth, wrap(async (req, res) => {
  const order = await prisma.order.findUnique({
    where: { id: BigInt(req.params.id) },
    include: { items: { include: { product: true } } },
  });
  if (!order) throw notFound("Order not found");
  if (order.userId !== req.user.id && req.user.role !== "admin") throw forbidden("Not your order");
  res.json(orderDto(order, order.items));
}));

router.patch("/orders/:id/status", requireAdmin, validate(orderStatusSchema), wrap(async (req, res) => {
  const id = BigInt(req.params.id);
  if (!(await prisma.order.findUnique({ where: { id } }))) throw notFound("Order not found");
  const order = await prisma.order.update({
    where: { id },
    data: { status: req.body.status },
    include: { items: { include: { product: true } } },
  });
  res.json(orderDto(order, order.items));
}));

export default router;
