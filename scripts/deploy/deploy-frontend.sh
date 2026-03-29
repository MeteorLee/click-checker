#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
FRONTEND_HEALTH_URL="${FRONTEND_HEALTH_URL:-http://127.0.0.1:3001/healthz}"
FRONTEND_HEALTH_ATTEMPTS="${FRONTEND_HEALTH_ATTEMPTS:-20}"
FRONTEND_HEALTH_DELAY_SECONDS="${FRONTEND_HEALTH_DELAY_SECONDS:-3}"
FRONTEND_STATE_FILE="${FRONTEND_STATE_FILE:-/var/run/click-checker-frontend-image}"
PREVIOUS_FRONTEND_IMAGE=""

compose() {
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} "$@"
}

wait_frontend_health() {
  local attempt

  for attempt in $(seq 1 "${FRONTEND_HEALTH_ATTEMPTS}"); do
    if curl -fsS "${FRONTEND_HEALTH_URL}" 2>/dev/null | grep -q '"status":"ok"'; then
      echo "[frontend] health check passed"
      return 0
    fi
    echo "[frontend] health retry ${attempt}/${FRONTEND_HEALTH_ATTEMPTS}"
    sleep "${FRONTEND_HEALTH_DELAY_SECONDS}"
  done

  echo "[frontend] health check failed" >&2
  return 1
}

write_frontend_state() {
  local image="$1"
  sudo sh -c "printf '%s\n' '${image}' > '${FRONTEND_STATE_FILE}'"
}

read_frontend_state() {
  if sudo test -f "${FRONTEND_STATE_FILE}"; then
    sudo cat "${FRONTEND_STATE_FILE}"
  fi
}

rollback_on_error() {
  local exit_code=$?

  trap - ERR

  if [ "${exit_code}" -eq 0 ]; then
    return
  fi

  echo "[frontend] deploy failed" >&2

  if [ -n "${PREVIOUS_FRONTEND_IMAGE}" ] && [ "${PREVIOUS_FRONTEND_IMAGE}" != "${FRONTEND_IMAGE}" ]; then
    echo "[frontend] recovering previous image ${PREVIOUS_FRONTEND_IMAGE}" >&2
    FRONTEND_IMAGE="${PREVIOUS_FRONTEND_IMAGE}" compose up -d frontend >/dev/null 2>&1 || true
    wait_frontend_health || true
  fi

  exit "${exit_code}"
}

main() {
  if [ -z "${FRONTEND_IMAGE:-}" ]; then
    echo "[frontend] FRONTEND_IMAGE is empty" >&2
    exit 1
  fi

  PREVIOUS_FRONTEND_IMAGE="$(read_frontend_state || true)"

  echo "[frontend] starting frontend with image ${FRONTEND_IMAGE}"
  compose up -d frontend >/dev/null
  wait_frontend_health
  write_frontend_state "${FRONTEND_IMAGE}"
  echo "[frontend] completed"
}

trap rollback_on_error ERR

main "$@"
