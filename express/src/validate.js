import { z } from "zod";

import { badRequest } from "./errors.js";

// Validate req.body against a zod schema; on failure -> 400 with a message.
export const validate = (schema) => (req, res, next) => {
  const result = schema.safeParse(req.body);
  if (!result.success) {
    const issue = result.error.issues[0];
    return next(badRequest(`${issue.path.join(".") || "body"}: ${issue.message}`));
  }
  req.body = result.data;
  next();
};

export const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
});

export const productWriteSchema = z.object({
  name: z.string().min(1),
  description: z.string().optional(),
  price: z.coerce.number().nonnegative(),
  categoryId: z.coerce.number().int().optional().nullable(),
  isActive: z.boolean().optional(),
  stock: z.coerce.number().int().nonnegative().optional(),
});

export const productPatchSchema = productWriteSchema.partial();

export const cartItemSchema = z.object({
  product_id: z.coerce.number().int(),
  quantity: z.coerce.number().int().min(1),
});

export const quantitySchema = z.object({
  quantity: z.coerce.number().int().min(1),
});

export const orderStatusSchema = z.object({
  status: z.enum(["pending", "paid", "shipped", "delivered", "cancelled"]),
});
