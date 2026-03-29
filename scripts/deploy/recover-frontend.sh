#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:3001/healthz}"
FRONTEND_HEALTH_ATTEMPTS="${FRONTEND_HEALTH_ATTEMPTS:-20}"
FRONTEND_HEALTH_DELAY_SECONDS="${FRONTEND_HEALTH_DELAY_SECONDS:-3}"
FRONTEND_STATE_FILE="${FRONTEND_STATE_FILE:-/var/run/click-checker-frontend-image}"

compose() {
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} "$@"
}

wait_frontend_health() {
  local attempt

  for attempt in $(seq 1 "${FRONTEND_HEALTH_ATTEMPTS}"); do
    if curl -fsS "${FRONTEND_HEALTH_URL}" 2>/dev/null | grep -q '"status":"ok"'; then
      echo "[frontend-recover] health check passed"
      return 0
    fi
    echo "[frontend-recover] health retry ${attempt}/${FRONTEND_HEALTH_ATTEMPTS}"
    sleep "${FRONTEND_HEALTH_DELAY_SECONDS}"
  done

  echo "[frontend-recover] health check failed" >&2
  return 1
}

if [ -z "${FRONTEND_IMAGE:-}" ] && sudo test -f "${FRONTEND_STATE_FILE}"; then
  FRONTEND_IMAGE="$(sudo cat "${FRONTEND_STATE_FILE}")"
fi

if [ -z "${FRONTEND_IMAGE:-}" ]; then
  echo "[frontend-recover] FRONTEND_IMAGE is empty" >&2
  exit 1
fi

echo "[frontend-recover] recovering frontend with image ${FRONTEND_IMAGE}"
FRONTEND_IMAGE="${FRONTEND_IMAGE}" compose up -d frontend >/dev/null
wait_frontend_health
echo "[frontend-recover] completed"
