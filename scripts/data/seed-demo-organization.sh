#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgres}"
DEMO_ORG_NAME="${DEMO_ORG_NAME:-demo_web_shop}"
DEMO_OWNER_LOGIN_ID="${DEMO_OWNER_LOGIN_ID:-demo_seed_owner}"
DEMO_OWNER_PASSWORD="${DEMO_OWNER_PASSWORD:-DemoSeedPass123!}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/click-checker-demo-seed.json}"

DEMO_TOTAL_EVENTS=300000
DEMO_IDENTIFIED_EVENTS=210000
DEMO_ANONYMOUS_EVENTS=90000
DEMO_IDENTIFIED_USERS=10000
DEMO_EXISTING_IDENTIFIED_USERS=8000
DEMO_NEW_IDENTIFIED_USERS=2000
DEMO_EXISTING_IDENTIFIED_EVENTS=168000
DEMO_NEW_IDENTIFIED_EVENTS=42000

DEMO_ROUTE_TEMPLATES=(
  "/|home|100"
  "/pricing|pricing|100"
  "/products|product_list|100"
  "/products/{id}|product_detail|100"
  "/products/{id}/reviews|product_reviews|100"
  "/search|search_results|100"
  "/signup|signup|100"
  "/cart|cart|100"
  "/checkout|checkout|100"
  "/orders/special|order_special|120"
  "/orders/{id}|order_detail|100"
  "/account/profile|account_profile|120"
  "/account/orders|account_orders|120"
  "/account/{section}|account_section|100"
  "/account|account_home|100"
)

DEMO_EVENT_TYPE_MAPPINGS=(
  "page_view|PAGE_VIEW"
  "screen_view|PAGE_VIEW"
  "product_view|PAGE_VIEW"
  "cta_click|CTA_CLICK"
  "hero_cta_click|CTA_CLICK"
  "banner_click|CTA_CLICK"
  "search_submit|SEARCH"
  "search_execute|SEARCH"
  "cart_add|ADD_TO_CART"
  "add_to_cart_click|ADD_TO_CART"
  "checkout_start|START_CHECKOUT"
  "checkout_begin|START_CHECKOUT"
  "signup_submit|SIGN_UP"
  "register_submit|SIGN_UP"
  "join_complete|SIGN_UP"
  "purchase_complete|PURCHASE"
  "order_paid|PURCHASE"
  "checkout_success|PURCHASE"
  "login_submit|LOGIN"
  "signin_complete|LOGIN"
)

DEMO_BROWSE_PATHS=(
  "/"
  "/"
  "/"
  "/pricing"
  "/"
  "/products"
)

DEMO_PRODUCT_PATHS=(
  "/products"
  "/products/101"
  "/products/205"
  "/products/377"
  "/products/101/reviews"
  "/products/377/reviews"
)

DEMO_SEARCH_PATHS=(
  "/search"
  "/search"
  "/search"
)

DEMO_CONVERSION_PATHS=(
  "/signup"
  "/cart"
  "/checkout"
)

DEMO_ACCOUNT_PATHS=(
  "/account"
  "/account/profile"
  "/account/orders"
  "/account/security"
  "/orders/special"
  "/orders/5001"
  "/orders/5002"
)

DEMO_UNMATCHED_PATHS=(
  "/promo/spring-2026"
  "/collections/weekend-deals"
  "/support/contact"
  "/stories/customer-day"
)

DEMO_MAPPED_RAW_EVENT_TYPES=(
  "page_view"
  "screen_view"
  "product_view"
  "cta_click"
  "hero_cta_click"
  "banner_click"
  "search_submit"
  "search_execute"
  "cart_add"
  "add_to_cart_click"
  "checkout_start"
  "checkout_begin"
  "signup_submit"
  "register_submit"
  "join_complete"
  "purchase_complete"
  "order_paid"
  "checkout_success"
  "login_submit"
  "signin_complete"
)

DEMO_UNMAPPED_RAW_EVENT_TYPES=(
  "wishlist_add"
  "coupon_apply"
  "video_play"
)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_command docker
require_command mktemp
require_command sed
require_command date

post_json_allow_status() {
  local url="$1"
  local body="$2"
  shift 2

  local response status
  response=$(mktemp)
  status=$(curl -sS -o "${response}" -w "%{http_code}" \
    -H "Content-Type: application/json" \
    "$@" \
    -X POST "${url}" \
    -d "${body}")

  printf '%s|%s\n' "${status}" "${response}"
}

query_db() {
  local sql="$1"
  printf '%s\n' "${sql}" | docker compose exec -T "${POSTGRES_SERVICE}" bash -lc \
    'psql -qtAX -F "|" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | sed '/^$/d'
}

apply_sql_file() {
  local sql_file="$1"
  docker compose exec -T "${POSTGRES_SERVICE}" bash -lc \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${sql_file}"
}

sql_literal() {
  local value="$1"
  value=${value//\'/\'\'}
  printf "'%s'" "${value}"
}

sql_text_array() {
  local -n source_ref=$1
  local values=()
  local value

  for value in "${source_ref[@]}"; do
    values+=("$(sql_literal "${value}")")
  done

  local IFS=', '
  printf '%s' "${values[*]}"
}

build_route_template_values() {
  local org_id="$1"
  local created_at="$2"
  local values=()
  local item template route_key priority

  for item in "${DEMO_ROUTE_TEMPLATES[@]}"; do
    IFS='|' read -r template route_key priority <<<"${item}"
    values+=("(${org_id}, $(sql_literal "${template}"), $(sql_literal "${route_key}"), ${priority}, true, TIMESTAMPTZ $(sql_literal "${created_at}"), TIMESTAMPTZ $(sql_literal "${created_at}"))")
  done

  local IFS=$',\n'
  printf '%s' "${values[*]}"
}

build_event_type_mapping_values() {
  local org_id="$1"
  local created_at="$2"
  local values=()
  local item raw_event_type canonical_event_type

  for item in "${DEMO_EVENT_TYPE_MAPPINGS[@]}"; do
    IFS='|' read -r raw_event_type canonical_event_type <<<"${item}"
    values+=("(${org_id}, $(sql_literal "${raw_event_type}"), $(sql_literal "${canonical_event_type}"), true, TIMESTAMPTZ $(sql_literal "${created_at}"), TIMESTAMPTZ $(sql_literal "${created_at}"))")
  done

  local IFS=$',\n'
  printf '%s' "${values[*]}"
}

extract_access_token() {
  local response_file="$1"
  jq -r '.accessToken // empty' "${response_file}"
}

ensure_demo_owner_token() {
  local signup_body login_body signup_result signup_status signup_response login_result login_status login_response token

  login_body=$(jq -nc \
    --arg loginId "${DEMO_OWNER_LOGIN_ID}" \
    --arg password "${DEMO_OWNER_PASSWORD}" \
    '{loginId:$loginId,password:$password}')

  login_result=$(post_json_allow_status "${BASE_URL}/api/v1/admin/auth/login" "${login_body}")
  login_status="${login_result%%|*}"
  login_response="${login_result#*|}"

  if [[ "${login_status}" == "200" || "${login_status}" == "201" ]]; then
    token=$(extract_access_token "${login_response}")
    rm -f "${login_response}"
    if [[ -n "${token}" ]]; then
      printf '%s\n' "${token}"
      return
    fi
  fi

  rm -f "${login_response}"

  signup_body=$(jq -nc \
    --arg loginId "${DEMO_OWNER_LOGIN_ID}" \
    --arg password "${DEMO_OWNER_PASSWORD}" \
    '{loginId:$loginId,password:$password}')

  signup_result=$(post_json_allow_status "${BASE_URL}/api/v1/admin/auth/signup" "${signup_body}")
  signup_status="${signup_result%%|*}"
  signup_response="${signup_result#*|}"

  if [[ "${signup_status}" != "200" && "${signup_status}" != "201" && "${signup_status}" != "409" ]]; then
    echo "Failed to ensure demo owner account: ${signup_status}" >&2
    cat "${signup_response}" >&2 || true
    rm -f "${signup_response}"
    exit 1
  fi

  rm -f "${signup_response}"

  login_result=$(post_json_allow_status "${BASE_URL}/api/v1/admin/auth/login" "${login_body}")
  login_status="${login_result%%|*}"
  login_response="${login_result#*|}"

  if [[ "${login_status}" != "200" && "${login_status}" != "201" ]]; then
    echo "Failed to log in demo owner account: ${login_status}" >&2
    cat "${login_response}" >&2 || true
    rm -f "${login_response}"
    exit 1
  fi

  token=$(extract_access_token "${login_response}")
  rm -f "${login_response}"

  if [[ -z "${token}" ]]; then
    echo "Failed to parse demo owner access token" >&2
    exit 1
  fi

  printf '%s\n' "${token}"
}

ensure_demo_org() {
  local access_token="$1"
  local existing_id existing_prefix response_result response_status response_file response_org_id

  existing_id="$(query_db "select id from organizations where name = $(sql_literal "${DEMO_ORG_NAME}") limit 1;")"
  if [[ -n "${existing_id}" ]]; then
    printf '%s\n' "${existing_id}"
    return
  fi

  response_result=$(post_json_allow_status \
    "${BASE_URL}/api/v1/admin/organizations" \
    "$(jq -nc --arg name "${DEMO_ORG_NAME}" '{name:$name}')" \
    -H "Authorization: Bearer ${access_token}")
  response_status="${response_result%%|*}"
  response_file="${response_result#*|}"

  if [[ "${response_status}" != "200" && "${response_status}" != "201" ]]; then
    echo "Failed to create demo organization: ${response_status}" >&2
    cat "${response_file}" >&2 || true
    rm -f "${response_file}"
    exit 1
  fi

  response_org_id=$(jq -r '.organizationId // empty' "${response_file}")
  rm -f "${response_file}"

  if [[ -z "${response_org_id}" ]]; then
    echo "Failed to parse demo organization id" >&2
    exit 1
  fi

  printf '%s\n' "${response_org_id}"
}

generate_demo_sql() {
  local output_path="$1"
  local org_id="$2"
  local owner_account_id="$3"
  local created_at="$4"
  local anchor_date_kst="$5"

  cat > "${output_path}" <<SQL
BEGIN;

INSERT INTO organization_members (account_id, organization_id, role, created_at, updated_at)
VALUES (${owner_account_id}, ${org_id}, 'OWNER', TIMESTAMPTZ $(sql_literal "${created_at}"), TIMESTAMPTZ $(sql_literal "${created_at}"))
ON CONFLICT (account_id, organization_id) DO UPDATE SET role = EXCLUDED.role, updated_at = EXCLUDED.updated_at;

DELETE FROM events WHERE organization_id = ${org_id};
DELETE FROM route_templates WHERE organization_id = ${org_id};
DELETE FROM event_type_mappings WHERE organization_id = ${org_id};

INSERT INTO route_templates (organization_id, template, route_key, priority, active, created_at, updated_at)
VALUES
$(build_route_template_values "${org_id}" "${created_at}");

INSERT INTO event_type_mappings (organization_id, raw_event_type, canonical_event_type, active, created_at, updated_at)
VALUES
$(build_event_type_mapping_values "${org_id}" "${created_at}");

INSERT INTO users (organization_id, external_user_id, created_at, updated_at)
SELECT
  ${org_id},
  $(sql_literal "${DEMO_ORG_NAME}") || '-existing-user-' || lpad(gs::text, 5, '0'),
  TIMESTAMPTZ $(sql_literal "${created_at}"),
  TIMESTAMPTZ $(sql_literal "${created_at}")
FROM generate_series(1, ${DEMO_EXISTING_IDENTIFIED_USERS}) AS gs
ON CONFLICT (organization_id, external_user_id) DO NOTHING;

INSERT INTO users (organization_id, external_user_id, created_at, updated_at)
SELECT
  ${org_id},
  $(sql_literal "${DEMO_ORG_NAME}") || '-new-user-' || lpad(gs::text, 5, '0'),
  TIMESTAMPTZ $(sql_literal "${created_at}"),
  TIMESTAMPTZ $(sql_literal "${created_at}")
FROM generate_series(1, ${DEMO_NEW_IDENTIFIED_USERS}) AS gs
ON CONFLICT (organization_id, external_user_id) DO NOTHING;

WITH constants AS (
  SELECT
    ARRAY[$(sql_text_array DEMO_BROWSE_PATHS)]::text[] AS browse_paths,
    ARRAY[$(sql_text_array DEMO_PRODUCT_PATHS)]::text[] AS product_paths,
    ARRAY[$(sql_text_array DEMO_SEARCH_PATHS)]::text[] AS search_paths,
    ARRAY[$(sql_text_array DEMO_CONVERSION_PATHS)]::text[] AS conversion_paths,
    ARRAY[$(sql_text_array DEMO_ACCOUNT_PATHS)]::text[] AS account_paths,
    ARRAY[$(sql_text_array DEMO_UNMATCHED_PATHS)]::text[] AS unmatched_paths,
    ARRAY[$(sql_text_array DEMO_MAPPED_RAW_EVENT_TYPES)]::text[] AS mapped_event_types,
    ARRAY[$(sql_text_array DEMO_UNMAPPED_RAW_EVENT_TYPES)]::text[] AS unmapped_event_types,
    DATE $(sql_literal "${anchor_date_kst}") AS anchor_date_kst,
    $(sql_literal "${DEMO_ORG_NAME}")::text AS org_key
),
day_weights AS (
  SELECT
    gs AS day_offset,
    (c.anchor_date_kst - gs) AS local_date,
    CASE
      WHEN EXTRACT(ISODOW FROM (c.anchor_date_kst - gs)) IN (6, 7) THEN 7
      ELSE 4
    END
    + CASE
      WHEN gs IN (5, 6, 19, 20, 41, 42, 53) THEN 3
      ELSE 0
    END AS weight
  FROM generate_series(0, 59) AS gs
  CROSS JOIN constants AS c
),
day_slots AS (
  SELECT
    row_number() OVER (ORDER BY dw.local_date, slot) AS slot_index,
    dw.local_date
  FROM day_weights AS dw
  CROSS JOIN LATERAL generate_series(1, dw.weight) AS slot
),
day_slot_count AS (
  SELECT count(*) AS slot_count FROM day_slots
),
existing_day_weights AS (
  SELECT
    gs AS day_offset,
    (c.anchor_date_kst - gs) AS local_date,
    CASE
      WHEN gs <= 6 THEN 1
      WHEN gs <= 29 THEN 3
      ELSE 6
    END
    + CASE
      WHEN EXTRACT(ISODOW FROM (c.anchor_date_kst - gs)) IN (6, 7) THEN 1
      ELSE 0
    END AS weight
  FROM generate_series(0, 59) AS gs
  CROSS JOIN constants AS c
),
existing_day_slots AS (
  SELECT
    row_number() OVER (ORDER BY dw.local_date, slot) AS slot_index,
    dw.local_date
  FROM existing_day_weights AS dw
  CROSS JOIN LATERAL generate_series(1, dw.weight) AS slot
),
existing_day_slot_count AS (
  SELECT count(*) AS slot_count FROM existing_day_slots
),
new_user_day_slots AS (
  SELECT
    row_number() OVER (ORDER BY dw.local_date, slot) AS slot_index,
    dw.local_date
  FROM day_weights AS dw
  CROSS JOIN LATERAL generate_series(1, CASE
    WHEN dw.local_date >= (DATE $(sql_literal "${anchor_date_kst}") - 6) THEN 80
    WHEN dw.local_date >= (DATE $(sql_literal "${anchor_date_kst}") - 29) THEN 40
    WHEN dw.weight >= 7 THEN 24
    WHEN dw.weight >= 6 THEN 20
    WHEN dw.weight >= 5 THEN 18
    ELSE 14
  END) AS slot
),
new_user_day_slot_count AS (
  SELECT count(*) AS slot_count FROM new_user_day_slots
),
new_user_profiles AS (
  SELECT
    gs AS user_seq,
    $(sql_literal "${DEMO_ORG_NAME}") || '-new-user-' || lpad(gs::text, 5, '0') AS external_user_id,
    nuds.local_date AS first_seen_date,
    ((gs * 23) % 100) AS revisit_pct,
    CASE
      WHEN ((gs * 23) % 100) < 12 THEN 1
      WHEN ((gs * 23) % 100) < 32 THEN 4
      WHEN ((gs * 23) % 100) < 46 THEN 11
      WHEN ((gs * 23) % 100) < 58 THEN 24
      ELSE NULL
    END AS revisit_start_offset
  FROM generate_series(1, ${DEMO_NEW_IDENTIFIED_USERS}) AS gs
  CROSS JOIN new_user_day_slot_count AS nsc
  JOIN new_user_day_slots AS nuds
    ON nuds.slot_index = (((gs - 1) % nsc.slot_count) + 1)
),
funnel_success_users AS (
  SELECT
    gs AS user_seq,
    $(sql_literal "${DEMO_ORG_NAME}") || '-new-user-' || lpad(gs::text, 5, '0') AS external_user_id,
    DATE $(sql_literal "${anchor_date_kst}") - (((gs - 1) % 12) + 2) AS local_date
  FROM generate_series(1, 800) AS gs
),
funnel_signup_drop_users AS (
  SELECT
    gs AS user_seq,
    $(sql_literal "${DEMO_ORG_NAME}") || '-new-user-' || lpad((800 + gs)::text, 5, '0') AS external_user_id,
    DATE $(sql_literal "${anchor_date_kst}") - (((gs - 1) % 12) + 2) AS local_date
  FROM generate_series(1, 400) AS gs
),
funnel_cart_drop_users AS (
  SELECT
    gs AS user_seq,
    $(sql_literal "${DEMO_ORG_NAME}") || '-new-user-' || lpad((1200 + gs)::text, 5, '0') AS external_user_id,
    DATE $(sql_literal "${anchor_date_kst}") - (((gs - 1) % 12) + 2) AS local_date
  FROM generate_series(1, 400) AS gs
),
retention_pattern_users AS (
  SELECT
    gs AS user_seq,
    $(sql_literal "${DEMO_ORG_NAME}") || '-existing-user-' || lpad(gs::text, 5, '0') AS external_user_id,
    CASE ((gs - 1) % 6)
      WHEN 0 THEN 32
      WHEN 1 THEN 24
      WHEN 2 THEN 18
      WHEN 3 THEN 12
      WHEN 4 THEN 8
      ELSE 4
    END AS cohort_day_offset
  FROM generate_series(1, 1400) AS gs
),
retention_users AS (
  SELECT
    user_seq,
    external_user_id,
    cohort_day_offset,
    (DATE $(sql_literal "${anchor_date_kst}") - cohort_day_offset) AS local_date,
    ((user_seq * 17) % 100) AS return_pct
  FROM retention_pattern_users
),
special_events AS (
  SELECT
    50000 + user_seq AS seq,
    ((first_seen_date::timestamp + time '10:05:00') + (((user_seq * 97) % 1800) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul' AS occurred_at,
    CASE
      WHEN user_seq % 4 = 0 THEN '/'
      WHEN user_seq % 4 = 1 THEN '/products'
      WHEN user_seq % 4 = 2 THEN '/'
      ELSE '/search'
    END AS path,
    CASE
      WHEN user_seq % 4 = 0 THEN 'page_view'
      WHEN user_seq % 4 = 1 THEN 'product_view'
      WHEN user_seq % 4 = 2 THEN 'screen_view'
      ELSE 'search_submit'
    END AS event_type,
    external_user_id,
    'new'::text AS user_bucket
  FROM new_user_profiles
  UNION ALL
  SELECT
    (user_seq * 10) + 1 AS seq,
    ((local_date::timestamp + time '11:05:00') + (((user_seq * 211) % 1800) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul' AS occurred_at,
    CASE
      WHEN user_seq % 5 IN (0, 1, 2) THEN '/'::text
      WHEN user_seq % 5 = 3 THEN '/products'::text
      ELSE '/pricing'::text
    END AS path,
    'page_view'::text AS event_type,
    external_user_id,
    'new'::text AS user_bucket
  FROM funnel_success_users
  UNION ALL
  SELECT
    (user_seq * 10) + 2,
    ((local_date::timestamp + time '11:18:00') + (((user_seq * 157) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/signup',
    'signup_submit',
    external_user_id,
    'new'
  FROM funnel_success_users
  UNION ALL
  SELECT
    (user_seq * 10) + 3,
    (((local_date + 1)::timestamp + time '13:10:00') + (((user_seq * 193) % 1800) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/checkout',
    'purchase_complete',
    external_user_id,
    'new'
  FROM funnel_success_users
  UNION ALL
  SELECT
    100000 + (user_seq * 10) + 1,
    ((local_date::timestamp + time '12:15:00') + (((user_seq * 167) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    CASE
      WHEN user_seq % 4 IN (0, 1) THEN '/'
      WHEN user_seq % 4 = 2 THEN '/products'
      ELSE '/pricing'
    END,
    'page_view',
    external_user_id,
    'new'
  FROM funnel_signup_drop_users
  UNION ALL
  SELECT
    100000 + (user_seq * 10) + 2,
    ((local_date::timestamp + time '12:32:00') + (((user_seq * 181) % 900) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/signup',
    'register_submit',
    external_user_id,
    'new'
  FROM funnel_signup_drop_users
  UNION ALL
  SELECT
    200000 + (user_seq * 10) + 1,
    ((local_date::timestamp + time '18:10:00') + (((user_seq * 113) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/products/101',
    'product_view',
    external_user_id,
    'new'
  FROM funnel_cart_drop_users
  UNION ALL
  SELECT
    200000 + (user_seq * 10) + 2,
    ((local_date::timestamp + time '18:28:00') + (((user_seq * 149) % 900) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/cart',
    'cart_add',
    external_user_id,
    'new'
  FROM funnel_cart_drop_users
  UNION ALL
  SELECT
    300000 + (user_seq * 10) + 1,
    ((local_date::timestamp + time '10:20:00') + (((user_seq * 131) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/account',
    'login_submit',
    external_user_id,
    'existing'
  FROM retention_users
  UNION ALL
  SELECT
    300000 + (user_seq * 10) + 2,
    (((local_date + 1)::timestamp + time '09:40:00') + (((user_seq * 173) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/products/205',
    'product_view',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE return_pct < 35
    AND cohort_day_offset >= 1
  UNION ALL
  SELECT
    300000 + (user_seq * 10) + 3,
    (((local_date + 4)::timestamp + time '14:05:00') + (((user_seq * 191) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/search',
    'search_submit',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE return_pct >= 35
    AND return_pct < 55
    AND cohort_day_offset >= 4
  UNION ALL
  SELECT
    300000 + (user_seq * 10) + 4,
    (((local_date + 11)::timestamp + time '20:15:00') + (((user_seq * 223) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/cart',
    'add_to_cart_click',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE return_pct >= 55
    AND return_pct < 70
    AND cohort_day_offset >= 11
  UNION ALL
  SELECT
    300000 + (user_seq * 10) + 5,
    (((local_date + 24)::timestamp + time '21:25:00') + (((user_seq * 251) % 1200) * INTERVAL '1 second')) AT TIME ZONE 'Asia/Seoul',
    '/orders/5001',
    'purchase_complete',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE return_pct >= 70
    AND return_pct < 78
    AND cohort_day_offset >= 24
),
special_counts AS (
  SELECT
    count(*) AS total_special_events,
    count(*) FILTER (WHERE external_user_id IS NOT NULL) AS special_identified_events,
    count(*) FILTER (WHERE user_bucket = 'existing') AS special_existing_events
  FROM special_events
),
generic_seed AS (
  SELECT
    gs AS seq,
    CASE
      WHEN gs <= (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) THEN 'existing'
      WHEN gs <= (${DEMO_IDENTIFIED_EVENTS} - sc.special_identified_events) THEN 'new'
      ELSE 'anonymous'
    END AS user_bucket,
    CASE
      WHEN gs <= (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) THEN
        $(sql_literal "${DEMO_ORG_NAME}") || '-existing-user-' || lpad((1400 + (((gs - 1) % GREATEST(${DEMO_EXISTING_IDENTIFIED_USERS} - 1400, 1)) + 1))::text, 5, '0')
      WHEN gs <= (${DEMO_IDENTIFIED_EVENTS} - sc.special_identified_events) THEN
        $(sql_literal "${DEMO_ORG_NAME}") || '-new-user-' || lpad((((gs - (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) - 1) % ${DEMO_NEW_IDENTIFIED_USERS}) + 1)::text, 5, '0')
      ELSE NULL
    END AS external_user_id,
    CASE
      WHEN gs <= (${DEMO_IDENTIFIED_EVENTS} - sc.special_identified_events) AND gs > (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) THEN
        (((gs - (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) - 1) % ${DEMO_NEW_IDENTIFIED_USERS}) + 1)
      ELSE NULL
    END AS new_user_seq,
    CASE
      WHEN gs <= (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) THEN
        eds.local_date
      WHEN gs <= (${DEMO_IDENTIFIED_EVENTS} - sc.special_identified_events) AND gs > (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) THEN
        CASE
          WHEN nup.revisit_start_offset IS NULL THEN nup.first_seen_date
          ELSE GREATEST(
            ds.local_date,
            nup.first_seen_date + nup.revisit_start_offset
          )
        END
      ELSE ds.local_date
    END AS local_date,
    CASE
      WHEN (gs * 17) % 100 < 34 THEN 'browse'
      WHEN (gs * 17) % 100 < 54 THEN 'product'
      WHEN (gs * 17) % 100 < 69 THEN 'conversion'
      WHEN (gs * 17) % 100 < 84 THEN 'account'
      WHEN (gs * 17) % 100 < 95 THEN 'search'
      ELSE 'unmatched'
    END AS path_group,
    ((gs * 29) % 100) AS event_pct,
    ((gs * 41) % 100) AS hour_pct
  FROM generate_series(1, ${DEMO_TOTAL_EVENTS} - (SELECT total_special_events FROM special_counts)) AS gs
  CROSS JOIN special_counts AS sc
  CROSS JOIN day_slot_count AS dsc
  CROSS JOIN existing_day_slot_count AS edsc
  JOIN day_slots AS ds
    ON ds.slot_index = (((gs - 1) % dsc.slot_count) + 1)
  JOIN existing_day_slots AS eds
    ON eds.slot_index = (((gs - 1) % edsc.slot_count) + 1)
  LEFT JOIN new_user_profiles AS nup
    ON nup.user_seq = (((gs - (${DEMO_EXISTING_IDENTIFIED_EVENTS} - sc.special_existing_events) - 1) % ${DEMO_NEW_IDENTIFIED_USERS}) + 1)
),
generic_events AS (
  SELECT
    seq,
    (
      local_date::timestamp
      + CASE
          WHEN hour_pct < 55 THEN time '09:00:00' + (((seq * 37) % 32400) * INTERVAL '1 second')
          WHEN hour_pct < 85 THEN time '18:00:00' + (((seq * 53) % 21600) * INTERVAL '1 second')
          ELSE time '00:00:00' + (((seq * 71) % 32400) * INTERVAL '1 second')
        END
    ) AT TIME ZONE 'Asia/Seoul' AS occurred_at,
    external_user_id,
    CASE path_group
      WHEN 'browse' THEN c.browse_paths[((seq - 1) % array_length(c.browse_paths, 1)) + 1]
      WHEN 'product' THEN c.product_paths[((seq - 1) % array_length(c.product_paths, 1)) + 1]
      WHEN 'conversion' THEN c.conversion_paths[((seq - 1) % array_length(c.conversion_paths, 1)) + 1]
      WHEN 'account' THEN c.account_paths[((seq - 1) % array_length(c.account_paths, 1)) + 1]
      WHEN 'search' THEN c.search_paths[((seq - 1) % array_length(c.search_paths, 1)) + 1]
      ELSE c.unmatched_paths[((seq - 1) % array_length(c.unmatched_paths, 1)) + 1]
    END AS path,
    CASE
      WHEN path_group = 'browse' AND event_pct < 42 THEN 'page_view'
      WHEN path_group = 'browse' AND event_pct < 64 THEN 'screen_view'
      WHEN path_group = 'browse' AND event_pct < 82 THEN 'cta_click'
      WHEN path_group = 'browse' AND event_pct < 90 THEN 'banner_click'
      WHEN path_group = 'browse' AND event_pct < 95 THEN 'search_submit'
      WHEN path_group = 'browse' THEN 'wishlist_add'
      WHEN path_group = 'product' AND event_pct < 38 THEN 'product_view'
      WHEN path_group = 'product' AND event_pct < 60 THEN 'cta_click'
      WHEN path_group = 'product' AND event_pct < 78 THEN 'cart_add'
      WHEN path_group = 'product' AND event_pct < 90 THEN 'add_to_cart_click'
      WHEN path_group = 'product' AND event_pct < 96 THEN 'video_play'
      WHEN path_group = 'product' THEN 'coupon_apply'
      WHEN path_group = 'conversion' AND event_pct < 18 THEN 'page_view'
      WHEN path_group = 'conversion' AND event_pct < 40 THEN 'hero_cta_click'
      WHEN path_group = 'conversion' AND event_pct < 58 THEN 'signup_submit'
      WHEN path_group = 'conversion' AND event_pct < 74 THEN 'checkout_start'
      WHEN path_group = 'conversion' AND event_pct < 88 THEN 'checkout_begin'
      WHEN path_group = 'conversion' AND event_pct < 95 THEN 'purchase_complete'
      WHEN path_group = 'account' AND event_pct < 34 THEN 'login_submit'
      WHEN path_group = 'account' AND event_pct < 58 THEN 'signin_complete'
      WHEN path_group = 'account' AND event_pct < 74 THEN 'page_view'
      WHEN path_group = 'account' AND event_pct < 88 THEN 'screen_view'
      WHEN path_group = 'account' THEN 'order_paid'
      WHEN path_group = 'search' AND event_pct < 55 THEN 'search_submit'
      WHEN path_group = 'search' AND event_pct < 82 THEN 'search_execute'
      WHEN path_group = 'search' AND event_pct < 92 THEN 'page_view'
      WHEN path_group = 'search' THEN 'cta_click'
      WHEN event_pct < 55 THEN 'page_view'
      WHEN event_pct < 80 THEN 'cta_click'
      ELSE 'video_play'
    END AS event_type
  FROM generic_seed AS g
  CROSS JOIN constants AS c
),
all_events AS (
  SELECT seq, occurred_at, path, event_type, external_user_id
  FROM special_events
  UNION ALL
  SELECT 900000 + seq, occurred_at, path, event_type, external_user_id
  FROM generic_events
),
resolved_events AS (
  SELECT
    e.seq,
    e.occurred_at,
    e.path,
    e.event_type,
    u.id AS event_user_id
  FROM all_events AS e
  LEFT JOIN users AS u
    ON u.organization_id = ${org_id}
   AND u.external_user_id = e.external_user_id
)
INSERT INTO events (
  event_type,
  occurred_at,
  path,
  payload,
  event_user_id,
  organization_id,
  created_at,
  updated_at
)
SELECT
  event_type,
  occurred_at,
  path,
  NULL,
  event_user_id,
  ${org_id},
  occurred_at,
  occurred_at
FROM resolved_events
ORDER BY seq;

COMMIT;
SQL
}

main() {
  local access_token org_id owner_account_id created_at anchor_date_kst temp_sql summary_json

  access_token="$(ensure_demo_owner_token)"
  org_id="$(ensure_demo_org "${access_token}")"
  owner_account_id="$(query_db "select id from accounts where login_id = $(sql_literal "${DEMO_OWNER_LOGIN_ID}") limit 1;")"

  if [[ -z "${owner_account_id}" ]]; then
    echo "Failed to resolve demo owner account id" >&2
    exit 1
  fi

  created_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  anchor_date_kst="$(TZ=Asia/Seoul date +%F)"
  temp_sql="$(mktemp)"

  generate_demo_sql "${temp_sql}" "${org_id}" "${owner_account_id}" "${created_at}" "${anchor_date_kst}"
  apply_sql_file "${temp_sql}"
  rm -f "${temp_sql}"

  summary_json="$(
    jq -nc \
      --arg orgName "${DEMO_ORG_NAME}" \
      --arg ownerLoginId "${DEMO_OWNER_LOGIN_ID}" \
      --arg createdAt "${created_at}" \
      --arg anchorDateKst "${anchor_date_kst}" \
      --argjson organizationId "${org_id}" \
      --argjson totalEvents "$(query_db "select count(*) from events where organization_id = ${org_id};")" \
      --argjson identifiedEvents "$(query_db "select count(*) from events where organization_id = ${org_id} and event_user_id is not null;")" \
      --argjson anonymousEvents "$(query_db "select count(*) from events where organization_id = ${org_id} and event_user_id is null;")" \
      --argjson identifiedUsers "$(query_db "select count(*) from users where organization_id = ${org_id};")" \
      --argjson existingUsers "$(query_db "select count(*) from users where organization_id = ${org_id} and external_user_id like $(sql_literal "${DEMO_ORG_NAME}-existing-user-%");")" \
      --argjson newUsers "$(query_db "select count(*) from users where organization_id = ${org_id} and external_user_id like $(sql_literal "${DEMO_ORG_NAME}-new-user-%");")" \
      --argjson matchedRoutes "$(query_db "select count(*) from events e where e.organization_id = ${org_id} and exists (select 1 from route_templates rt where rt.organization_id = e.organization_id and rt.active = true and ((rt.template = e.path) or (rt.template = '/products/{id}' and e.path ~ '^/products/[0-9]+$') or (rt.template = '/products/{id}/reviews' and e.path ~ '^/products/[0-9]+/reviews$') or (rt.template = '/orders/{id}' and e.path ~ '^/orders/[0-9]+$')));")" \
      --argjson mappedEventTypes "$(query_db "select count(*) from events e where e.organization_id = ${org_id} and exists (select 1 from event_type_mappings etm where etm.organization_id = e.organization_id and etm.active = true and etm.raw_event_type = e.event_type);")" \
      '{
        organizationId: $organizationId,
        orgName: $orgName,
        ownerLoginId: $ownerLoginId,
        createdAt: $createdAt,
        anchorDateKst: $anchorDateKst,
        totals: {
          totalEvents: $totalEvents,
          identifiedEvents: $identifiedEvents,
          anonymousEvents: $anonymousEvents,
          identifiedUsers: $identifiedUsers,
          existingUsers: $existingUsers,
          newUsers: $newUsers
        },
        coverageProbe: {
          matchedRoutes: $matchedRoutes,
          mappedEventTypes: $mappedEventTypes
        }
      }'
  )"

  printf '%s\n' "${summary_json}" > "${OUTPUT_FILE}"
  echo "[demo-seed] ready"
  echo "[demo-seed] summary: ${OUTPUT_FILE}"
  jq . "${OUTPUT_FILE}"
}

main "$@"
