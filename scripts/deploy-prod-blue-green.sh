#!/usr/bin/env bash
set -euo pipefail

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
APP_DOMAIN="clickchecker.dev"
NGINX_CONFIG="/etc/nginx/sites-available/default"

compose_up_infra() {
  $COMPOSE up -d prometheus grafana
}

stop_color() {
  $COMPOSE stop "app-$1"
}

require_env() {
  local key="$1"
  local value
  value=$(grep -E "^${key}=" .env | tail -n1 | cut -d'=' -f2- || true)
  if [ -z "${value}" ]; then
    echo "Missing required .env key: ${key}" >&2
    return 1
  fi
}

dump_logs() {
  echo "---- docker compose ps ----"
  $COMPOSE ps || true
  echo "---- app-blue logs (tail=200) ----"
  $COMPOSE logs --tail=200 app-blue || true
  echo "---- app-green logs (tail=200) ----"
  $COMPOSE logs --tail=200 app-green || true
  echo "---- grafana logs (tail=100) ----"
  $COMPOSE logs --tail=100 grafana || true
  echo "---- prometheus logs (tail=100) ----"
  $COMPOSE logs --tail=100 prometheus || true
}

dump_smoke_org_response() {
  if [ ! -f /tmp/smoke_org.json ]; then
    echo "smoke_org.json not found"
    return
  fi

  echo "---- /tmp/smoke_org.json (redacted) ----"
  if jq -c '{id, apiKeyPrefix, apiKey:"[REDACTED]"}' /tmp/smoke_org.json >/tmp/smoke_org_redacted.json 2>/dev/null; then
    cat /tmp/smoke_org_redacted.json
  else
    echo "Response body redacted because it may contain apiKey."
  fi
}

current_color() {
  if sudo test -f /var/run/click-checker-active-color; then
    local state_color
    state_color=$(sudo cat /var/run/click-checker-active-color)
    case "$state_color" in
      blue|green)
        echo "$state_color"
        return 0
        ;;
    esac
  fi

  if sudo awk '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1; next }
    in_block && /\}/ { in_block=0 }
    in_block && /server 127\.0\.0\.1:8081;/ { found=1 }
    END { exit(found ? 0 : 1) }
  ' "$NGINX_CONFIG"; then
    echo blue
    return 0
  fi

  if sudo awk '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1; next }
    in_block && /\}/ { in_block=0 }
    in_block && /server 127\.0\.0\.1:8082;/ { found=1 }
    END { exit(found ? 0 : 1) }
  ' "$NGINX_CONFIG"; then
    echo green
    return 0
  fi

  if sudo grep -q 'proxy_pass http://127.0.0.1:8081;' "$NGINX_CONFIG"; then
    echo blue
    return 0
  fi

  if sudo grep -q 'proxy_pass http://127.0.0.1:8082;' "$NGINX_CONFIG"; then
    echo green
    return 0
  fi

  echo "Could not detect active color from nginx config" >&2
  return 1
}

other_color() {
  case "$1" in
    blue) echo green ;;
    green) echo blue ;;
    *)
      echo "Unknown color: $1" >&2
      return 1
      ;;
  esac
}

wait_health() {
  local tries="${1:-12}"
  local sleep_s="${2:-5}"
  local i
  for i in $(seq 1 "$tries"); do
    if curl --max-time 5 --resolve "${APP_DOMAIN}:443:127.0.0.1" -fsS "https://${APP_DOMAIN}/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null; then
      echo "Health check passed"
      return 0
    fi
    echo "Health check retry ${i}/${tries}"
    sleep "$sleep_s"
  done
  return 1
}

smoke_post_json() {
  local url="$1"
  local data="$2"
  local out="$3"
  curl -sS -o "$out" -w "%{http_code}" \
    --resolve "${APP_DOMAIN}:443:127.0.0.1" \
    -H "Content-Type: application/json" \
    -X POST "$url" \
    -d "$data" || true
}

main() {
  command -v jq >/dev/null 2>&1 || { echo "jq not installed on EC2"; exit 1; }
  [ -f .env ] || { echo ".env not found"; exit 1; }
  require_env DB_URL
  require_env DB_USERNAME
  require_env DB_PASSWORD
  require_env API_KEY_PEPPER
  require_env API_KEY_ENV

  chmod +x ./scripts/blue-green-prod-switch.sh

  local active_color target_color switched smoke_ok ts now org_code api_key event_body event_code agg_code
  active_color=$(current_color)
  target_color=$(other_color "$active_color")
  switched=0

  echo "ACTIVE_COLOR=${active_color}"
  echo "TARGET_COLOR=${target_color}"

  compose_up_infra

  if ! env SKIP_STOP_OLD=1 ./scripts/blue-green-prod-switch.sh "$target_color"; then
    echo "Deploy failed during blue-green switch"
    dump_logs
    exit 1
  fi
  switched=1

  if ! wait_health 12 5; then
    echo "Deploy verification failed (health)"
    dump_logs
    exit 1
  fi

  smoke_ok=0
  ts=$(date +"%Y%m%d%H%M%S")
  now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  echo "Smoke: create organization"
  org_code=$(smoke_post_json "https://${APP_DOMAIN}/api/organizations" \
    "{\"name\":\"__smoke-org-${ts}\"}" \
    "/tmp/smoke_org.json")

  echo "ORG_CODE=${org_code}"
  if [ "$org_code" = "200" ] || [ "$org_code" = "201" ]; then
    api_key=$(jq -r '.apiKey // empty' /tmp/smoke_org.json 2>/dev/null || true)

    if [ -n "$api_key" ]; then
      echo "Smoke: post event"
      event_body=$(jq -nc \
        --arg externalUserId "deploy-smoke-user" \
        --arg eventType "__smoke" \
        --arg path "/__smoke/deploy" \
        --arg occurredAt "$now" \
        --arg payload "{}" \
        '{externalUserId:$externalUserId,eventType:$eventType,path:$path,occurredAt:$occurredAt,payload:$payload}')

      event_code=$(curl -sS -o "/tmp/smoke_event.json" -w "%{http_code}" \
        --resolve "${APP_DOMAIN}:443:127.0.0.1" \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -X POST "https://${APP_DOMAIN}/api/events" \
        -d "$event_body" || true)

      echo "EVENT_CODE=${event_code}"
      echo "Smoke: query aggregates"
      agg_code=$(curl -sS -o "/tmp/smoke_agg.json" -w "%{http_code}" \
        --resolve "${APP_DOMAIN}:443:127.0.0.1" \
        -H "X-API-Key: ${api_key}" \
        "https://${APP_DOMAIN}/api/events/aggregates/paths?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z&top=5" || true)

      echo "AGG_CODE=${agg_code}"
      if { [ "$event_code" = "200" ] || [ "$event_code" = "201" ]; } && [ "$agg_code" = "200" ]; then
        smoke_ok=1
        echo "Smoke check passed"
      else
        echo "Smoke API failed"
        echo "---- /tmp/smoke_event.json ----"
        cat /tmp/smoke_event.json || true
        echo "---- /tmp/smoke_agg.json ----"
        cat /tmp/smoke_agg.json || true
      fi
    else
      echo "API_KEY parse failed"
      dump_smoke_org_response
    fi
  else
    echo "Organization create failed"
    dump_smoke_org_response
  fi

  if [ "$smoke_ok" -ne 1 ]; then
    echo "Deploy verification failed (smoke)"
    dump_logs
    exit 1
  fi

  echo "Stopping old color after successful verification"
  stop_color "$active_color" || true
  echo "Deploy completed successfully"
}

main "$@"
