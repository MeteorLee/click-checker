#!/usr/bin/env bash
set -euo pipefail

# Minimal deploy smoke suite shared by:
# - direct target verification before nginx switch
# - public-path verification after nginx switch
#
# Required env:
# - BASE_URL
#
# Optional env:
# - APP_DOMAIN         default: clickchecker.dev
# - PUBLIC_RESOLVE     default: 0
# - EXPORT_API_KEY_FILE  optional file path for temporary api key export

BASE_URL="${BASE_URL:-}"
APP_DOMAIN="${APP_DOMAIN:-clickchecker.dev}"
PUBLIC_RESOLVE="${PUBLIC_RESOLVE:-0}"
AGGREGATE_PATHS_ENDPOINT="${AGGREGATE_PATHS_ENDPOINT:-/api/v1/events/analytics/aggregates/paths}"
EXPORT_API_KEY_FILE="${EXPORT_API_KEY_FILE:-}"

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

require_env_value() {
  local key="$1"
  local value="$2"
  if [ -z "${value}" ]; then
    echo "Missing required env: ${key}" >&2
    exit 1
  fi
}

dump_smoke_org_response() {
  if [ ! -f /tmp/smoke_org.json ]; then
    echo "smoke_org.json not found"
    return
  fi

  echo "---- /tmp/smoke_org.json (redacted) ----"
  if jq -c '{organizationId, name, ownerMembershipId, apiKeyPrefix, apiKey:"[REDACTED]"}' /tmp/smoke_org.json >/tmp/smoke_org_redacted.json 2>/dev/null; then
    cat /tmp/smoke_org_redacted.json
  else
    echo "Response body redacted because it may contain apiKey."
  fi
}

redact_smoke_org_file() {
  if [ ! -f /tmp/smoke_org.json ]; then
    return
  fi

  if jq '{organizationId, name, ownerMembershipId, apiKeyPrefix, apiKey:"[REDACTED]"}' \
    /tmp/smoke_org.json >/tmp/smoke_org_redacted.json 2>/dev/null; then
    mv /tmp/smoke_org_redacted.json /tmp/smoke_org.json
  else
    rm -f /tmp/smoke_org_redacted.json
  fi
}

export_api_key_if_requested() {
  local api_key="$1"

  if [ -z "${EXPORT_API_KEY_FILE}" ]; then
    return
  fi

  printf '%s\n' "${api_key}" > "${EXPORT_API_KEY_FILE}"
  chmod 600 "${EXPORT_API_KEY_FILE}"
  echo "[smoke] exported api key to ${EXPORT_API_KEY_FILE}"
}

smoke_post_json() {
  local url="$1"
  local data="$2"
  local out="$3"

  curl -sS -o "$out" -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -X POST "${url}" \
    -d "${data}" || true
}

smoke_post_json_public() {
  local url="$1"
  local data="$2"
  local out="$3"

  curl -sS -o "$out" -w "%{http_code}" \
    --resolve "${APP_DOMAIN}:443:127.0.0.1" \
    -H "Content-Type: application/json" \
    -X POST "${url}" \
      -d "${data}" || true
}

smoke_post_json_with_bearer() {
  local url="$1"
  local data="$2"
  local token="$3"
  local out="$4"

  curl -sS -o "$out" -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${token}" \
    -X POST "${url}" \
    -d "${data}" || true
}

smoke_post_json_public_with_bearer() {
  local url="$1"
  local data="$2"
  local token="$3"
  local out="$4"

  curl -sS -o "$out" -w "%{http_code}" \
    --resolve "${APP_DOMAIN}:443:127.0.0.1" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${token}" \
    -X POST "${url}" \
    -d "${data}" || true
}

main() {
  local ts now signup_code org_code api_key access_token event_body event_code agg_code
  local smoke_login_id smoke_password

  require_command curl
  require_command jq
  require_env_value BASE_URL "${BASE_URL}"

  ts=$(date +"%Y%m%d%H%M%S")
  now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  smoke_login_id="smoke-${ts}"
  smoke_password="Smoke1234"

  echo "[smoke] signup smoke account via ${BASE_URL}"
  if [ "${PUBLIC_RESOLVE}" = "1" ]; then
    signup_code=$(smoke_post_json_public "${BASE_URL}/api/v1/admin/auth/signup" \
      "{\"loginId\":\"${smoke_login_id}\",\"password\":\"${smoke_password}\"}" \
      "/tmp/smoke_auth.json")
  else
    signup_code=$(smoke_post_json "${BASE_URL}/api/v1/admin/auth/signup" \
      "{\"loginId\":\"${smoke_login_id}\",\"password\":\"${smoke_password}\"}" \
      "/tmp/smoke_auth.json")
  fi

  echo "[smoke] signup status=${signup_code}"
  if [ "${signup_code}" != "200" ] && [ "${signup_code}" != "201" ]; then
    echo "[smoke] signup failed"
    echo "---- /tmp/smoke_auth.json ----"
    cat /tmp/smoke_auth.json || true
    exit 1
  fi

  access_token=$(jq -r '.accessToken // empty' /tmp/smoke_auth.json 2>/dev/null || true)
  if [ -z "${access_token}" ]; then
    echo "[smoke] accessToken parse failed"
    echo "---- /tmp/smoke_auth.json ----"
    cat /tmp/smoke_auth.json || true
    exit 1
  fi

  echo "[smoke] create organization via ${BASE_URL}"
  if [ "${PUBLIC_RESOLVE}" = "1" ]; then
    org_code=$(smoke_post_json_public_with_bearer "${BASE_URL}/api/v1/admin/organizations" \
      "{\"name\":\"__smoke-org-${ts}\"}" \
      "${access_token}" \
      "/tmp/smoke_org.json")
  else
    org_code=$(smoke_post_json_with_bearer "${BASE_URL}/api/v1/admin/organizations" \
      "{\"name\":\"__smoke-org-${ts}\"}" \
      "${access_token}" \
      "/tmp/smoke_org.json")
  fi

  echo "[smoke] organization status=${org_code}"
  if [ "${org_code}" != "200" ] && [ "${org_code}" != "201" ]; then
    echo "[smoke] organization create failed"
    dump_smoke_org_response
    exit 1
  fi

  api_key=$(jq -r '.apiKey // empty' /tmp/smoke_org.json 2>/dev/null || true)
  if [ -z "${api_key}" ]; then
    echo "[smoke] apiKey parse failed"
    dump_smoke_org_response
    exit 1
  fi
  export_api_key_if_requested "${api_key}"
  redact_smoke_org_file

  event_body=$(jq -nc \
    --arg externalUserId "deploy-smoke-user" \
    --arg eventType "__smoke" \
    --arg path "/__smoke/deploy" \
    --arg occurredAt "${now}" \
    --arg payload "{\"source\":\"deploy-smoke\",\"runId\":\"${ts}\"}" \
    '{externalUserId:$externalUserId,eventType:$eventType,path:$path,occurredAt:$occurredAt,payload:$payload}')

  echo "[smoke] post event via ${BASE_URL}"
  if [ "${PUBLIC_RESOLVE}" = "1" ]; then
    event_code=$(curl -sS -o "/tmp/smoke_event.json" -w "%{http_code}" \
      --resolve "${APP_DOMAIN}:443:127.0.0.1" \
      -H "Content-Type: application/json" \
      -H "X-API-Key: ${api_key}" \
      -X POST "${BASE_URL}/api/events" \
      -d "${event_body}" || true)
  else
    event_code=$(curl -sS -o "/tmp/smoke_event.json" -w "%{http_code}" \
      -H "Content-Type: application/json" \
      -H "X-API-Key: ${api_key}" \
      -X POST "${BASE_URL}/api/events" \
      -d "${event_body}" || true)
  fi

  echo "[smoke] event status=${event_code}"

  echo "[smoke] query aggregates via ${BASE_URL}"
  if [ "${PUBLIC_RESOLVE}" = "1" ]; then
    agg_code=$(curl -sS -o "/tmp/smoke_agg.json" -w "%{http_code}" \
      --resolve "${APP_DOMAIN}:443:127.0.0.1" \
      -H "X-API-Key: ${api_key}" \
      "${BASE_URL}${AGGREGATE_PATHS_ENDPOINT}?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z&top=5" || true)
  else
    agg_code=$(curl -sS -o "/tmp/smoke_agg.json" -w "%{http_code}" \
      -H "X-API-Key: ${api_key}" \
      "${BASE_URL}${AGGREGATE_PATHS_ENDPOINT}?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z&top=5" || true)
  fi

  echo "[smoke] aggregate status=${agg_code}"

  if { [ "${event_code}" = "200" ] || [ "${event_code}" = "201" ]; } && [ "${agg_code}" = "200" ]; then
    echo "[smoke] passed via ${BASE_URL}"
    exit 0
  fi

  echo "[smoke] api check failed via ${BASE_URL}"
  echo "---- /tmp/smoke_event.json ----"
  cat /tmp/smoke_event.json || true
  echo "---- /tmp/smoke_agg.json ----"
  cat /tmp/smoke_agg.json || true
  exit 1
}

main "$@"
