#!/usr/bin/env bash
set -euo pipefail

# Seed sample organizations and events for local/EC2 environments.
# Usage:
#   ./scripts/data/seed-sample-data.sh
#   BASE_URL=http://localhost:8080 RUN_LABEL=my-run ./scripts/data/seed-sample-data.sh
# Output:
#   Writes a summary JSON file that includes organization IDs and apiKeys.
#
BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_LABEL="${RUN_LABEL:-$(date -u +"%Y%m%d%H%M%S")}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/click-checker-seed-${RUN_LABEL}.json}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_command date

post_json() {
  local url="$1"
  local body="$2"
  local headers=("${@:3}")

  local response
  response=$(mktemp)

  local code
  code=$(curl -sS -o "${response}" -w "%{http_code}" \
    -H "Content-Type: application/json" \
    "${headers[@]}" \
    -X POST "${url}" \
    -d "${body}")

  if [[ "${code}" != "200" && "${code}" != "201" ]]; then
    echo "Request failed: POST ${url} -> ${code}" >&2
    cat "${response}" >&2 || true
    rm -f "${response}"
    exit 1
  fi

  cat "${response}"
  rm -f "${response}"
}

create_organization() {
  local name="$1"
  local payload
  payload=$(jq -nc --arg name "${name}" '{name:$name}')
  post_json "${BASE_URL}/api/organizations" "${payload}"
}

post_event() {
  local api_key="$1"
  local external_user_id="$2"
  local event_type="$3"
  local path="$4"
  local occurred_at="$5"
  local payload="$6"

  local body
  if [[ -n "${external_user_id}" ]]; then
    body=$(jq -nc \
      --arg externalUserId "${external_user_id}" \
      --arg eventType "${event_type}" \
      --arg path "${path}" \
      --arg occurredAt "${occurred_at}" \
      --arg payload "${payload}" \
      '{externalUserId:$externalUserId,eventType:$eventType,path:$path,occurredAt:$occurredAt,payload:$payload}')
  else
    body=$(jq -nc \
      --arg eventType "${event_type}" \
      --arg path "${path}" \
      --arg occurredAt "${occurred_at}" \
      --arg payload "${payload}" \
      '{eventType:$eventType,path:$path,occurredAt:$occurredAt,payload:$payload}')
  fi

  post_json "${BASE_URL}/api/events" "${body}" -H "X-API-Key: ${api_key}" >/dev/null
}

iso_at() {
  local day_offset="$1"
  local hour="$2"
  local minute="$3"
  date -u -d "2026-03-01 +${day_offset} day ${hour}:${minute}:00" +"%Y-%m-%dT%H:%M:%SZ"
}

seed_acme_media() {
  local api_key="$1"
  local -a users=("u-1001" "u-1002" "u-1003" "u-1004" "u-1005" "u-1006")
  local i

  for i in "${!users[@]}"; do
    local user="${users[$i]}"
    local day=$((i % 5))

    post_event "${api_key}" "${user}" "page_view" "/" "$(iso_at "${day}" 9 $((5 + i)))" '{"referrer":"direct","device":"desktop"}'
    post_event "${api_key}" "${user}" "page_view" "/pricing" "$(iso_at "${day}" 9 $((20 + i)))" '{"referrer":"home","device":"desktop"}'
    post_event "${api_key}" "${user}" "cta_click" "/pricing" "$(iso_at "${day}" 9 $((25 + i)))" '{"buttonId":"start-trial","section":"hero"}'
    post_event "${api_key}" "${user}" "page_view" "/features/team-analytics" "$(iso_at "${day}" 14 $((10 + i)))" '{"referrer":"pricing","device":"mobile"}'
    post_event "${api_key}" "${user}" "page_view" "/blog/rds-migration-checklist" "$(iso_at "${day}" 20 $((10 + i)))" '{"referrer":"search","device":"mobile"}'
  done

  post_event "${api_key}" "" "page_view" "/" "$(iso_at 0 8 30)" '{"referrer":"newsletter","device":"mobile"}'
  post_event "${api_key}" "" "page_view" "/blog/k6-baseline-guide" "$(iso_at 1 21 5)" '{"referrer":"search","device":"mobile"}'
  post_event "${api_key}" "u-1002" "signup_start" "/signup" "$(iso_at 2 10 15)" '{"plan":"pro","source":"pricing"}'
  post_event "${api_key}" "u-1004" "signup_start" "/signup" "$(iso_at 3 11 45)" '{"plan":"team","source":"features"}'
  post_event "${api_key}" "u-1002" "cta_click" "/blog/k6-baseline-guide" "$(iso_at 4 19 12)" '{"buttonId":"book-demo","section":"sidebar"}'
}

seed_northstar_shop() {
  local api_key="$1"
  local -a users=("u-2001" "u-2002" "u-2003" "u-2004" "u-2005")
  local i

  for i in "${!users[@]}"; do
    local user="${users[$i]}"
    local day=$((i % 4))

    post_event "${api_key}" "${user}" "page_view" "/" "$(iso_at "${day}" 10 $((3 + i)))" '{"referrer":"ad","device":"mobile"}'
    post_event "${api_key}" "${user}" "page_view" "/products/smart-lamp" "$(iso_at "${day}" 10 $((15 + i)))" '{"referrer":"home","device":"mobile"}'
    post_event "${api_key}" "${user}" "cta_click" "/products/smart-lamp" "$(iso_at "${day}" 10 $((18 + i)))" '{"buttonId":"add-to-cart","campaign":"spring-sale"}'
    post_event "${api_key}" "${user}" "page_view" "/cart" "$(iso_at "${day}" 10 $((25 + i)))" '{"referrer":"product","device":"mobile"}'
    post_event "${api_key}" "${user}" "page_view" "/products/wireless-speaker" "$(iso_at "${day}" 18 $((5 + i)))" '{"referrer":"category","device":"desktop"}'
  done

  post_event "${api_key}" "" "page_view" "/" "$(iso_at 0 12 5)" '{"referrer":"instagram","device":"mobile"}'
  post_event "${api_key}" "" "page_view" "/collections/spring-sale" "$(iso_at 1 13 40)" '{"referrer":"ad","device":"mobile"}'
  post_event "${api_key}" "u-2003" "checkout_start" "/checkout" "$(iso_at 2 19 10)" '{"payment":"card","cartSize":2}'
  post_event "${api_key}" "u-2005" "cta_click" "/products/wireless-speaker" "$(iso_at 3 20 35)" '{"buttonId":"buy-now","campaign":"bundle-offer"}'
}

main() {
  local acme_name="Acme Analytics ${RUN_LABEL}"
  local northstar_name="Northstar Commerce ${RUN_LABEL}"

  echo "Seeding sample organizations into ${BASE_URL}"

  local acme_json
  acme_json=$(create_organization "${acme_name}")
  local acme_id
  acme_id=$(jq -r '.id' <<<"${acme_json}")
  local acme_key
  acme_key=$(jq -r '.apiKey' <<<"${acme_json}")
  local acme_prefix
  acme_prefix=$(jq -r '.apiKeyPrefix' <<<"${acme_json}")

  local northstar_json
  northstar_json=$(create_organization "${northstar_name}")
  local northstar_id
  northstar_id=$(jq -r '.id' <<<"${northstar_json}")
  local northstar_key
  northstar_key=$(jq -r '.apiKey' <<<"${northstar_json}")
  local northstar_prefix
  northstar_prefix=$(jq -r '.apiKeyPrefix' <<<"${northstar_json}")

  seed_acme_media "${acme_key}"
  seed_northstar_shop "${northstar_key}"

  jq -nc \
    --arg generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
    --arg baseUrl "${BASE_URL}" \
    --arg runLabel "${RUN_LABEL}" \
    --argjson acmeId "${acme_id}" \
    --arg acmeName "${acme_name}" \
    --arg acmeApiKey "${acme_key}" \
    --arg acmeApiKeyPrefix "${acme_prefix}" \
    --argjson northstarId "${northstar_id}" \
    --arg northstarName "${northstar_name}" \
    --arg northstarApiKey "${northstar_key}" \
    --arg northstarApiKeyPrefix "${northstar_prefix}" \
    '{
      generatedAt: $generatedAt,
      baseUrl: $baseUrl,
      runLabel: $runLabel,
      organizations: [
        {
          id: $acmeId,
          name: $acmeName,
          apiKey: $acmeApiKey,
          apiKeyPrefix: $acmeApiKeyPrefix,
          seededEventsApprox: 35
        },
        {
          id: $northstarId,
          name: $northstarName,
          apiKey: $northstarApiKey,
          apiKeyPrefix: $northstarApiKeyPrefix,
          seededEventsApprox: 29
        }
      ]
    }' > "${OUTPUT_FILE}"

  echo "Seed complete"
  echo "Summary file: ${OUTPUT_FILE}"
  echo "Warning: the summary file contains plain apiKeys. Handle it carefully."
  jq -c '{generatedAt, runLabel, organizations: [.organizations[] | {id, name, apiKeyPrefix, seededEventsApprox}]}' "${OUTPUT_FILE}"
  echo "Organizations seeded:"
  jq -r '.organizations[] | "- id=\(.id), name=\(.name), apiKeyPrefix=\(.apiKeyPrefix), approxEvents=\(.seededEventsApprox)"' "${OUTPUT_FILE}"
}

main "$@"
