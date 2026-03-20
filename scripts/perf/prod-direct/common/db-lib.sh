#!/usr/bin/env bash
set -euo pipefail

DB_CLIENT_IMAGE="${DB_CLIENT_IMAGE:-postgres:16}"
DB_SSLMODE="${DB_SSLMODE:-require}"
DB_CONNECT_TIMEOUT="${DB_CONNECT_TIMEOUT:-5}"

load_env_file_if_present() {
  local path="$1"
  if [[ -f "${path}" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${path}"
    set +a
  fi
}

ensure_prod_db_env() {
  if [[ -z "${DB_URL:-}" || -z "${DB_USERNAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
    load_env_file_if_present ".env"
    load_env_file_if_present ".env.codedeploy"
  fi

  if [[ -z "${DB_URL:-}" || -z "${DB_USERNAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
    echo "DB_URL, DB_USERNAME, DB_PASSWORD are required for prod-direct DB access" >&2
    exit 1
  fi
}

build_psql_conninfo() {
  ensure_prod_db_env

  local jdbc_prefix="jdbc:postgresql://"
  if [[ "${DB_URL}" != ${jdbc_prefix}* ]]; then
    echo "Unsupported DB_URL format: ${DB_URL}" >&2
    exit 1
  fi

  local without_prefix host_port db_with_params db_name host port
  without_prefix="${DB_URL#${jdbc_prefix}}"
  host_port="${without_prefix%%/*}"
  db_with_params="${without_prefix#*/}"
  db_name="${db_with_params%%\?*}"

  host="${host_port%%:*}"
  port="${host_port##*:}"
  if [[ "${host}" == "${port}" ]]; then
    port="5432"
  fi

  printf 'host=%s port=%s dbname=%s user=%s sslmode=%s' \
    "${host}" "${port}" "${db_name}" "${DB_USERNAME}" "${DB_SSLMODE}"
}

run_psql_file_via_rds() {
  local sql_file="$1"
  local conninfo
  conninfo="$(build_psql_conninfo)"

  docker run --rm -i \
    -e PGPASSWORD="${DB_PASSWORD}" \
    -e PGCONNECT_TIMEOUT="${DB_CONNECT_TIMEOUT}" \
    "${DB_CLIENT_IMAGE}" \
    psql -v ON_ERROR_STOP=1 "${conninfo}" < "${sql_file}"
}
