import request from "supertest";
import { afterAll, describe, expect, it } from "vitest";

import { createApp } from "../src/app.js";
import { prisma } from "../src/prisma.js";
import { makeProduct, makeUser } from "./helpers.js";

const app = createApp();
const bearer = (t) => ({ Authorization: `Bearer ${t}` });

afterAll(async () => {
  await prisma.$disconnect();
});

describe("auth", () => {
  it("registers, returns a token, and /auth/me works", async () => {
    const email = `reg-${Date.now()}@shop.test`;
    const reg = await request(app).post("/auth/register").send({ email, password: "Password123!" });
    expect(reg.status).toBe(201);
    expect(reg.body.token).toBeTruthy();
    expect(reg.body.user.email).toBe(email);

    const me = await request(app).get("/auth/me").set(bearer(reg.body.token));
    expect(me.status).toBe(200);
    expect(me.body.email).toBe(email);
  });

  it("rejects a wrong password with 401", async () => {
    const { user } = await makeUser();
    const resp = await request(app).post("/auth/login")
      .send({ email: user.email, password: "wrong" });
    expect(resp.status).toBe(401);
  });

  it("requires auth for /auth/me", async () => {
    expect((await request(app).get("/auth/me")).status).toBe(401);
  });
});

describe("catalog", () => {
  it("lists and searches products", async () => {
    const term = `Zzt${Date.now()}`;
    await makeProduct({ name: `${term} Mug`, price: "5.00" });
    await makeProduct({ name: "Other Thing", price: "5.00" });
    const resp = await request(app).get(`/products?search=${term}`);
    expect(resp.status).toBe(200);
    expect(resp.body.total).toBe(1);
    expect(resp.body.results[0].name).toContain(term);
  });

  it("paginates", async () => {
    const resp = await request(app).get("/products?page_size=2&page=1");
    expect(resp.body.page_size).toBe(2);
    expect(resp.body.results.length).toBeLessThanOrEqual(2);
  });

  it("lets an admin create a product but not a customer", async () => {
    const { token: adminTok } = await makeUser("admin");
    const created = await request(app).post("/products").set(bearer(adminTok))
      .send({ name: "New Gadget", price: 12.5, stock: 4 });
    expect(created.status).toBe(201);
    expect(created.body.stock).toBe(4);

    const { token: custTok } = await makeUser("customer");
    const denied = await request(app).post("/products").set(bearer(custTok))
      .send({ name: "Nope", price: 1 });
    expect(denied.status).toBe(403);
  });
});

describe("cart & checkout", () => {
  it("adds to cart, checks out, decrements stock, and clears the cart", async () => {
    const product = await makeProduct({ name: "Buyable", price: "10.00", stock: 5 });
    const { token } = await makeUser();

    await request(app).post("/cart/items").set(bearer(token))
      .send({ product_id: Number(product.id), quantity: 2 });

    const order = await request(app).post("/orders").set(bearer(token));
    expect(order.status).toBe(201);
    expect(order.body.total).toBe("20.00");
    expect(order.body.created_at).toBeTruthy();

    const inv = await prisma.inventory.findUnique({ where: { productId: product.id } });
    expect(inv.quantity).toBe(3); // 5 - 2

    const cart = await request(app).get("/cart").set(bearer(token));
    expect(cart.body.items).toHaveLength(0);
  });

  it("rejects checkout that would oversell with 409 and leaves stock unchanged", async () => {
    const product = await makeProduct({ name: "Scarce", price: "10.00", stock: 1 });
    const { token } = await makeUser();
    await request(app).post("/cart/items").set(bearer(token))
      .send({ product_id: Number(product.id), quantity: 5 });

    const order = await request(app).post("/orders").set(bearer(token));
    expect(order.status).toBe(409);

    const inv = await prisma.inventory.findUnique({ where: { productId: product.id } });
    expect(inv.quantity).toBe(1);
  });
});
