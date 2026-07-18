import { PrismaClient } from "@prisma/client";

// BigInt ids don't serialize to JSON by default; render them as numbers
// (our ids are well within Number's safe range). Prisma's Decimal serialises
// to a string on its own, which matches the API contract.
BigInt.prototype.toJSON = function () {
  return Number(this);
};

export const prisma = new PrismaClient();
