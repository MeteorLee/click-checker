#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/blue-green-prod-lib.sh"

APP_DIR="${APP_DIR:-/home/ubuntu/click-checker}"
REPO_NGINX_CONFIG="${REPO_NGINX_CONFIG:-${APP_DIR}/nginx/click-checker.conf}"
NGINX_CONFIG="${NGINX_CONFIG:-/etc/nginx/sites-available/default}"
NGINX_BACKUP_SUFFIX="${NGINX_BACKUP_SUFFIX:-frontend-pre-apply}"

if [ ! -f "${REPO_NGINX_CONFIG}" ]; then
  echo "[nginx-apply] repo nginx config not found: ${REPO_NGINX_CONFIG}" >&2
  exit 1
fi

ACTIVE_COLOR="$(current_color)"
ACTIVE_PORT="$(target_port "${ACTIVE_COLOR}")"
TMP_FILE="$(mktemp)"

cp "${REPO_NGINX_CONFIG}" "${TMP_FILE}"
sed -i -E "0,/server 127\\.0\\.0\\.1:(8081|8082);/s//server 127.0.0.1:${ACTIVE_PORT};/" "${TMP_FILE}"

if sudo test -f "${NGINX_CONFIG}"; then
  sudo cp "${NGINX_CONFIG}" "${NGINX_CONFIG}.${NGINX_BACKUP_SUFFIX}"
fi

sudo cp "${TMP_FILE}" "${NGINX_CONFIG}"
rm -f "${TMP_FILE}"

reload_nginx

echo "[nginx-apply] applied repo nginx config with active color ${ACTIVE_COLOR} (${ACTIVE_PORT})"
