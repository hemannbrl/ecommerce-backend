import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import express from "express";
import swaggerUi from "swagger-ui-express";
import YAML from "yaml";

import { authenticate } from "./auth.js";
import { errorHandler } from "./errors.js";
import authRoutes from "./routes/auth.js";
import cartRoutes from "./routes/cart.js";
import catalogRoutes from "./routes/catalog.js";
import orderRoutes from "./routes/orders.js";

export function createApp() {
  const app = express();
  app.use(express.json());
  app.use(authenticate); // populates req.user from the Bearer token if present

  app.get("/health", (req, res) => res.json({ status: "ok" }));

  app.use(authRoutes);
  app.use(catalogRoutes);
  app.use(cartRoutes);
  app.use(orderRoutes);

  // Serve the shared API contract as live Swagger docs.
  try {
    const here = dirname(fileURLToPath(import.meta.url));
    const specPath = process.env.OPENAPI_PATH || join(here, "../../api/openapi.yaml");
    const spec = YAML.parse(readFileSync(specPath, "utf8"));
    app.use("/docs", swaggerUi.serve, swaggerUi.setup(spec));
  } catch {
    // spec not found (e.g. packaged without it) — skip docs
  }

  app.use(errorHandler);
  return app;
}
