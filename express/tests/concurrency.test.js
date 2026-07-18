import { afterAll, expect, it } from "vitest";

import { prisma } from "../src/prisma.js";
import { checkout } from "../src/services.js";
import { makeProduct, makeUser } from "./helpers.js";

afterAll(async () => {
  await prisma.$disconnect();
});

// The important test: two shoppers race for the last unit. The row lock
// (SELECT ... FOR UPDATE) plus the CHECK constraint must let exactly one win.
it("two concurrent checkouts cannot oversell the last unit", async () => {
  const product = await makeProduct({ name: "LastOne", price: "10.00", stock: 1 });

  const buyers = [];
  for (let i = 0; i < 2; i++) {
    const { user } = await makeUser();
    const cart = await prisma.cart.create({ data: { userId: user.id } });
    await prisma.cartItem.create({
      data: { cartId: cart.id, productId: product.id, quantity: 1 },
    });
    buyers.push(user);
  }

  const outcomes = await Promise.all(
    buyers.map((u) =>
      checkout(u.id).then(() => "ok").catch((e) => (e.status === 409 ? "rejected" : `err:${e.message}`)),
    ),
  );

  expect(outcomes.sort()).toEqual(["ok", "rejected"]);
  const inv = await prisma.inventory.findUnique({ where: { productId: product.id } });
  expect(inv.quantity).toBe(0); // never negative
});
