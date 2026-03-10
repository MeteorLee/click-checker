#!/usr/bin/env bash
set -euo pipefail

# Switch the production app between app-blue(8081) and app-green(8082).
#
# Usage:
#   ./scripts/blue-green-prod-switch.sh
#   ./scripts/blue-green-prod-switch.sh blue
#   ./scripts/blue-green-prod-switch.sh green
#
# Requirements:
# - Run on the EC2 host
# - /etc/nginx/sites-available/default is the active nginx config
# - app-blue/app-green are defined in docker-compose.prod.yml
#
# Environment:
#   COMPOSE_FILES            Compose args override
#   NGINX_CONFIG             Active nginx config path
#   SKIP_STOP_OLD=1          Keep old color running after switch
#   SKIP_BUILD=1             Skip --build on target color startup
#   STABILIZE_SECONDS        Wait time after direct app verification

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
NGINX_CONFIG="${NGINX_CONFIG:-/etc/nginx/sites-available/default}"
STATE_FILE="${STATE_FILE:-/var/run/click-checker-active-color}"
STABILIZE_SECONDS="${STABILIZE_SECONDS:-5}"
PUBLIC_VERIFY_ATTEMPTS="${PUBLIC_VERIFY_ATTEMPTS:-5}"
PUBLIC_VERIFY_DELAY_SECONDS="${PUBLIC_VERIFY_DELAY_SECONDS:-2}"
TARGET_COLOR="${1:-}"
ACTIVE_COLOR=""
TARGET_STARTED=0
NGINX_SWITCHED=0

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command docker
require_command curl
require_command grep
require_command sed
require_command sudo

compose() {
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} "$@"
}

assert_color() {
  case "$1" in
    blue|green) ;;
    *)
      echo "Target color must be blue or green" >&2
      exit 1
      ;;
  esac
}

target_port() {
  case "$1" in
    blue) echo "8081" ;;
    green) echo "8082" ;;
    *)
      echo "Unknown color: $1" >&2
      exit 1
      ;;
  esac
}

other_color() {
  case "$1" in
    blue) echo "green" ;;
    green) echo "blue" ;;
    *)
      echo "Unknown color: $1" >&2
      exit 1
      ;;
  esac
}

current_color() {
  if sudo test -f "${STATE_FILE}"; then
    local state_color
    state_color=$(sudo cat "${STATE_FILE}")
    case "${state_color}" in
      blue|green)
        echo "${state_color}"
        return
        ;;
    esac
  fi

  local upstream_server
  upstream_server=$(sudo awk '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1; next }
    in_block && /\}/ { in_block=0 }
    in_block && /server 127\.0\.0\.1:(8081|8082);/ { print; exit }
  ' "${NGINX_CONFIG}")

  case "${upstream_server}" in
    *8081*)
      echo "blue"
      return
      ;;
    *8082*)
      echo "green"
      return
      ;;
  esac

  local proxy_pass_port
  proxy_pass_port=$(sudo awk '
    /server_name clickchecker\.dev;/ { in_server=1 }
    in_server && /server_name grafana\.clickchecker\.dev;/ { in_server=0 }
    in_server && /proxy_pass http:\/\/127\.0\.0\.1:(8081|8082);/ {
      if (match($0, /8081|8082/)) {
        print substr($0, RSTART, RLENGTH)
        exit
      }
    }
  ' "${NGINX_CONFIG}")

  case "${proxy_pass_port}" in
    8081)
      echo "blue"
      return
      ;;
    8082)
      echo "green"
      return
      ;;
  esac

  echo "Could not detect active color from ${NGINX_CONFIG}" >&2
  exit 1
}

write_state_file() {
  local color="$1"
  sudo sh -c "printf '%s\n' '${color}' > '${STATE_FILE}'"
}

switch_upstream_target() {
  local port="$1"
  local tmp_file
  tmp_file=$(mktemp)

  sudo awk -v target_port="${port}" '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1 }
    in_block && /server 127\.0\.0\.1:(8081|8082);/ {
      sub(/127\.0\.0\.1:(8081|8082);/, "127.0.0.1:" target_port ";")
      in_block=0
    }
    { print }
  ' "${NGINX_CONFIG}" > "${tmp_file}"

  sudo cp "${tmp_file}" "${NGINX_CONFIG}"
  rm -f "${tmp_file}"
}

switch_proxy_pass_target() {
  local port="$1"
  sudo sed -i -E "s#proxy_pass http://127\\.0\\.0\\.1:(8081|8082);#proxy_pass http://127.0.0.1:${port};#g" "${NGINX_CONFIG}"
}

switch_nginx_target() {
  local color="$1"
  local port
  port=$(target_port "${color}")

  if sudo grep -q 'upstream click_checker_app' "${NGINX_CONFIG}"; then
    switch_upstream_target "${port}"
  else
    switch_proxy_pass_target "${port}"
  fi
}

wait_for_readiness() {
  local color="$1"
  local port
  port=$(target_port "${color}")
  local attempt

  for attempt in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:${port}/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "[switch] readiness passed for ${color}"
      return 0
    fi
    echo "[switch] readiness retry ${attempt}/30 for ${color}"
    sleep 2
  done

  echo "[switch] readiness check failed for ${color} on port ${port}" >&2
  exit 1
}

verify_root_color() {
  local color="$1"
  local port
  port=$(target_port "${color}")

  if ! curl -fsS "http://127.0.0.1:${port}/" 2>/dev/null | grep -q "\"color\":\"${color}\""; then
    echo "[switch] root response did not report expected color: ${color}" >&2
    exit 1
  fi

  echo "[switch] direct app response verified for ${color}"
}

verify_nginx_color() {
  local color="$1"
  local attempt

  for attempt in $(seq 1 "${PUBLIC_VERIFY_ATTEMPTS}"); do
    if curl -fsS --resolve clickchecker.dev:443:127.0.0.1 https://clickchecker.dev 2>/dev/null | grep -q "\"color\":\"${color}\""; then
      echo "[switch] public response verified for ${color}"
      return 0
    fi
    echo "[switch] public verify retry ${attempt}/${PUBLIC_VERIFY_ATTEMPTS} for ${color}"
    sleep "${PUBLIC_VERIFY_DELAY_SECONDS}"
  done

  echo "[switch] public nginx path did not report expected color: ${color}" >&2
  exit 1
}

stop_old_color() {
  local color="$1"

  if [[ "${SKIP_STOP_OLD:-0}" == "1" ]]; then
    echo "[switch] skipping old color stop"
    return
  fi

  compose stop "app-${color}" >/dev/null
}

rollback_on_error() {
  local exit_code=$?

  trap - ERR

  if [[ "${exit_code}" -eq 0 ]]; then
    return
  fi

  echo "[switch] failed, attempting rollback" >&2

  if [[ -n "${ACTIVE_COLOR}" && "${NGINX_SWITCHED}" -eq 1 ]]; then
    echo "[switch] restoring nginx target to ${ACTIVE_COLOR}" >&2
    switch_nginx_target "${ACTIVE_COLOR}" || true
    if sudo nginx -t >/dev/null 2>&1; then
      sudo systemctl reload nginx >/dev/null 2>&1 || true
      write_state_file "${ACTIVE_COLOR}" || true
    fi
  fi

  if [[ -n "${TARGET_COLOR}" && "${TARGET_STARTED}" -eq 1 ]]; then
    echo "[switch] stopping failed target color: ${TARGET_COLOR}" >&2
    compose stop "app-${TARGET_COLOR}" >/dev/null 2>&1 || true
  fi

  if [[ -n "${ACTIVE_COLOR}" ]]; then
    echo "[switch] ensuring previous active color is running: ${ACTIVE_COLOR}" >&2
    compose up -d "app-${ACTIVE_COLOR}" >/dev/null 2>&1 || true
  fi

  exit "${exit_code}"
}

main() {
  local active
  active=$(current_color)
  ACTIVE_COLOR="${active}"

  if [[ -z "${TARGET_COLOR}" ]]; then
    TARGET_COLOR=$(other_color "${active}")
  fi

  assert_color "${TARGET_COLOR}"

  if [[ "${TARGET_COLOR}" == "${active}" ]]; then
    echo "Target color is already active: ${TARGET_COLOR}" >&2
    exit 1
  fi

  local old_color
  old_color="${active}"

  echo "[switch] active color=${active}"
  echo "[switch] target color=${TARGET_COLOR}"

  if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
    echo "[switch] starting app-${TARGET_COLOR} without build"
    compose up -d "app-${TARGET_COLOR}" >/dev/null
  else
    echo "[switch] starting app-${TARGET_COLOR} with build"
    compose up -d --build "app-${TARGET_COLOR}" >/dev/null
  fi
  TARGET_STARTED=1

  echo "[switch] waiting for readiness on ${TARGET_COLOR}"
  wait_for_readiness "${TARGET_COLOR}"

  echo "[switch] verifying direct app response"
  verify_root_color "${TARGET_COLOR}"

  echo "[switch] waiting ${STABILIZE_SECONDS}s for app stabilization"
  sleep "${STABILIZE_SECONDS}"

  echo "[switch] switching nginx target to ${TARGET_COLOR}"
  switch_nginx_target "${TARGET_COLOR}"
  NGINX_SWITCHED=1

  echo "[switch] validating nginx config"
  sudo nginx -t >/dev/null

  echo "[switch] reloading nginx"
  sudo systemctl reload nginx
  write_state_file "${TARGET_COLOR}"

  echo "[switch] verifying public response"
  verify_nginx_color "${TARGET_COLOR}"

  echo "[switch] stopping old color: ${old_color}"
  stop_old_color "${old_color}"

  echo "[switch] complete"
}

trap rollback_on_error ERR

main "$@"
