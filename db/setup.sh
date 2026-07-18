#!/usr/bin/env bash
# Create the shared ecommerce_db and load the schema + seed.
# One command so a fresh clone has data — the SAME data the Playground lessons use.
#
# Docker users don't need this: `docker compose up` in any backend auto-loads
# schema.sql + seed.sql on first boot. This is for running a backend locally
# against your own PostgreSQL.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5433}"          # the project's default host port
PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-postgres}"
DB="${DB_NAME:-ecommerce_db}"

echo "→ creating database '$DB' on $PGHOST:$PGPORT"
createdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$DB" 2>/dev/null \
  && echo "  created" || echo "  already exists — reloading"

echo "→ loading schema"
psql -q -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB" -f "$HERE/schema.sql"
echo "→ loading seed"
psql -q -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB" -f "$HERE/seed.sql"

echo
echo "✓ done. Demo logins (password: Password123!):"
echo "    admin@shop.test  ·  alice@shop.test  ·  bob@shop.test"
