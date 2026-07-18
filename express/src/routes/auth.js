import { Router } from "express";

import { hashPassword, makeToken, requireAuth, verifyPassword } from "../auth.js";
import { conflict, unauthorized, wrap } from "../errors.js";
import { prisma } from "../prisma.js";
import { loginSchema, registerSchema, validate } from "../validate.js";

const router = Router();

const userDto = (u) => ({ id: Number(u.id), email: u.email, role: u.role });

router.post("/auth/register", validate(registerSchema), wrap(async (req, res) => {
  const { email, password } = req.body;
  if (await prisma.user.findUnique({ where: { email } })) {
    throw conflict("Email already registered");
  }
  const user = await prisma.user.create({
    data: { email, passwordHash: await hashPassword(password), role: "customer" },
  });
  await prisma.cart.create({ data: { userId: user.id } });
  res.status(201).json({ token: makeToken(user), user: userDto(user) });
}));

router.post("/auth/login", validate(loginSchema), wrap(async (req, res) => {
  const { email, password } = req.body;
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user || !(await verifyPassword(password, user.passwordHash))) {
    throw unauthorized("Invalid credentials");
  }
  res.json({ token: makeToken(user), user: userDto(user) });
}));

router.get("/auth/me", requireAuth, (req, res) => {
  res.json(userDto(req.user));
});

export default router;
