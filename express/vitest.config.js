import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globalSetup: "./tests/global-setup.js",
    env: {
      DATABASE_URL:
        process.env.DATABASE_URL ||
        "postgresql://postgres:postgres@localhost:5433/ecommerce_test",
    },
    fileParallelism: false, // tests share one database
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
