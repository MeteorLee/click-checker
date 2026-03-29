#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

export BASE_URL="${BASE_URL:-https://clickchecker.dev}"

# shellcheck disable=SC1091
source "${ROOT_DIR}/scripts/perf/prod-direct/common/db-lib.sh"

# Reuse the existing demo seed logic, but skip its auto-run entrypoint so we can
# override DB access for prod/RDS direct execution.
# shellcheck disable=SC1090
source <(sed '/^main "\$@"$/d' "${SCRIPT_DIR}/seed-demo-organization.sh")

query_db() {
  local sql="$1"
  run_psql_query_via_rds "${sql}" | sed '/^$/d'
}

apply_sql_file() {
  local sql_file="$1"
  run_psql_file_via_rds "${sql_file}"
}

main "$@"
