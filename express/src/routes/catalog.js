import { Router } from "express";

import { requireAdmin } from "../auth.js";
import { notFound, wrap } from "../errors.js";
import { productDto } from "../mappers.js";
import { prisma } from "../prisma.js";
import { productPatchSchema, productWriteSchema, validate } from "../validate.js";

const router = Router();

const SORTS = {
  price: { price: "asc" },
  "-price": { price: "desc" },
  name: { name: "asc" },
  "-name": { name: "desc" },
  newest: { createdAt: "desc" },
};

async function uniqueSlug(name) {
  const base = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "product";
  let slug = base;
  let n = 1;
  while (await prisma.product.findUnique({ where: { slug } })) {
    n += 1;
    slug = `${base}-${n}`;
  }
  return slug;
}

router.get("/products", wrap(async (req, res) => {
  const { search, category, sort } = req.query;
  const page = Math.max(1, parseInt(req.query.page) || 1);
  const pageSize = Math.min(100, Math.max(1, parseInt(req.query.page_size) || 20));

  const where = { isActive: true };
  if (search) {
    where.OR = [
      { name: { contains: search, mode: "insensitive" } },
      { description: { contains: search, mode: "insensitive" } },
    ];
  }
  if (category) where.category = { slug: category };

  const [rows, total] = await Promise.all([
    prisma.product.findMany({
      where,
      include: { inventory: true },
      orderBy: SORTS[sort] || { id: "asc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
    }),
    prisma.product.count({ where }),
  ]);

  res.json({ results: rows.map(productDto), page, page_size: pageSize, total });
}));

router.get("/products/:id", wrap(async (req, res) => {
  const product = await prisma.product.findUnique({
    where: { id: BigInt(req.params.id) },
    include: { inventory: true },
  });
  if (!product) throw notFound("Product not found");
  res.json(productDto(product));
}));

router.post("/products", requireAdmin, validate(productWriteSchema), wrap(async (req, res) => {
  const { name, description, price, categoryId, isActive, stock } = req.body;
  const product = await prisma.product.create({
    data: {
      name,
      slug: await uniqueSlug(name),
      description: description ?? "",
      price,
      categoryId: categoryId ?? null,
      isActive: isActive ?? true,
      inventory: { create: { quantity: stock ?? 0 } },
    },
    include: { inventory: true },
  });
  res.status(201).json(productDto(product));
}));

router.patch("/products/:id", requireAdmin, validate(productPatchSchema), wrap(async (req, res) => {
  const id = BigInt(req.params.id);
  if (!(await prisma.product.findUnique({ where: { id } }))) throw notFound("Product not found");
  const { name, description, price, categoryId, isActive, stock } = req.body;
  await prisma.product.update({
    where: { id },
    data: {
      ...(name !== undefined && { name }),
      ...(description !== undefined && { description }),
      ...(price !== undefined && { price }),
      ...(categoryId !== undefined && { categoryId }),
      ...(isActive !== undefined && { isActive }),
    },
  });
  if (stock !== undefined) {
    await prisma.inventory.upsert({
      where: { productId: id },
      create: { productId: id, quantity: stock },
      update: { quantity: stock },
    });
  }
  const product = await prisma.product.findUnique({ where: { id }, include: { inventory: true } });
  res.json(productDto(product));
}));

router.delete("/products/:id", requireAdmin, wrap(async (req, res) => {
  const id = BigInt(req.params.id);
  if (!(await prisma.product.findUnique({ where: { id } }))) throw notFound("Product not found");
  await prisma.product.delete({ where: { id } });
  res.status(204).end();
}));

router.get("/categories", wrap(async (req, res) => {
  const cats = await prisma.category.findMany({ orderBy: { name: "asc" } });
  res.json(cats.map((c) => ({ id: Number(c.id), name: c.name, slug: c.slug })));
}));

export default router;
