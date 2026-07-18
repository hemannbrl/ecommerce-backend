import { readFileSync } from "node:fs";

import pg from "pg";

// Load the shared schema into the test database once before the suite runs.
export async function setup() {
  const connectionString =
    process.env.DATABASE_URL ||
    "postgresql://postgres:postgres@localhost:5433/ecommerce_test";
  const schema = readFileSync(new URL("../../db/schema.sql", import.meta.url), "utf8");
  const client = new pg.Client({ connectionString });
  await client.connect();
  await client.query(schema); // schema.sql drops + recreates the tables
  await client.end();
}
