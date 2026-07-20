#!/usr/bin/env bash
# Seed the ssp-exchange MongoDB config collections from the JSON fixtures.
#
# Targets an already-running MongoDB container by name (default: ssp-mongodb) via `docker exec`,
# streaming each fixture over stdin — so nothing has to be mounted or copied into the container.
# Each collection is dropped and re-imported, so the seed is idempotent.
#
# Overrides (env):
#   MONGO_CONTAINER   container name            (default: ssp-mongodb)
#   MONGO_DB          target database           (default: ssp-exchange)
#   SEED_DIR          fixtures directory        (default: ../src/main/resources/seed)
set -euo pipefail

CONTAINER="${MONGO_CONTAINER:-ssp-mongodb}"
DB="${MONGO_DB:-ssp-exchange}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_DIR="${SEED_DIR:-$SCRIPT_DIR/../src/main/resources/seed}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "error: no running container named '$CONTAINER'" >&2
  exit 1
fi

for c in accounts publishers bidders; do
  echo "importing $c -> $DB.$c"
  docker exec -i "$CONTAINER" mongoimport \
    --db "$DB" --collection "$c" --jsonArray --drop < "$SEED_DIR/$c.json"
done

echo "seeded $DB in container $CONTAINER"
