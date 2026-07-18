import bcrypt from "bcrypt";
import jwt from "jsonwebtoken";

import { unauthorized, forbidden, wrap } from "./errors.js";
import { prisma } from "./prisma.js";

const SECRET = process.env.JWT_SECRET || "dev-only-insecure-secret-change-me";
const EXPIRY = process.env.JWT_EXPIRY || "24h";

export const hashPassword = (raw) => bcrypt.hash(raw, 10);
export const verifyPassword = (raw, hash) => bcrypt.compare(raw, hash);

export function makeToken(user) {
  // subject is a string; role travels in the token for authorization
  return jwt.sign({ role: user.role }, SECRET, {
    subject: String(user.id),
    expiresIn: EXPIRY,
  });
}

// Populate req.user from the Bearer token when present (does not reject).
export const authenticate = wrap(async (req, res, next) => {
  const header = req.headers.authorization;
  if (header?.startsWith("Bearer ")) {
    try {
      const payload = jwt.verify(header.slice(7), SECRET);
      const user = await prisma.user.findUnique({ where: { id: BigInt(payload.sub) } });
      if (user) req.user = user;
    } catch {
      // invalid/expired token -> stay anonymous
    }
  }
  next();
});

export function requireAuth(req, res, next) {
  if (!req.user) return next(unauthorized());
  next();
}

export function requireAdmin(req, res, next) {
  if (!req.user) return next(unauthorized());
  if (req.user.role !== "admin") return next(forbidden("Admin only"));
  next();
}
