import { hashPassword, makeToken } from "../src/auth.js";
import { prisma } from "../src/prisma.js";

let counter = 0;
const uniqueEmail = (p = "user") => `${p}-${Date.now()}-${counter++}@shop.test`;

export async function makeUser(role = "customer") {
  const email = uniqueEmail(role);
  const user = await prisma.user.create({
    data: { email, passwordHash: await hashPassword("Password123!"), role },
  });
  return { user, token: makeToken(user) };
}

export async function makeProduct({ name = "Widget", price = "10.00", stock = 5 } = {}) {
  const slug = `${name}-${Date.now()}-${counter++}`.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  return prisma.product.create({
    data: {
      name,
      slug,
      price,
      isActive: true,
      inventory: { create: { quantity: stock } },
    },
    include: { inventory: true },
  });
}
