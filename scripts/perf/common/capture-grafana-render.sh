#!/usr/bin/env bash
set -euo pipefail

META_PATH="${META_PATH:-${META:-${1:-}}}"
GRAFANA_BASE_URL="${GRAFANA_BASE_URL:-http://localhost:3000}"
GRAFANA_CAPTURE_BASE_URL="${GRAFANA_CAPTURE_BASE_URL:-${GRAFANA_BASE_URL}}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
GRAFANA_ORG_ID="${GRAFANA_ORG_ID:-1}"
GRAFANA_TZ="${GRAFANA_TZ:-Asia/Seoul}"
CAPTURE_PADDING_SEC="${CAPTURE_PADDING_SEC:-120}"
DASHBOARD_UID="${DASHBOARD_UID:-click-checker-overview}"
DASHBOARD_SLUG="${DASHBOARD_SLUG:-click-checker-overview}"
CAPTURE_PROFILE="${CAPTURE_PROFILE:-}"
DASHBOARD_WIDTH="${DASHBOARD_WIDTH:-1800}"
DASHBOARD_HEIGHT="${DASHBOARD_HEIGHT:-1400}"
PANEL_WIDTH="${PANEL_WIDTH:-1400}"
PANEL_HEIGHT="${PANEL_HEIGHT:-500}"
FROM_MS="${FROM_MS:-}"
TO_MS="${TO_MS:-}"
CAPTURE_SKIP_DASHBOARD="${CAPTURE_SKIP_DASHBOARD:-false}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

iso_to_ms() {
  local iso="$1"
  date -d "${iso}" +%s000
}

update_capture_meta() {
  local capture_status="$1"
  local from_ms="$2"
  local to_ms="$3"
  local capture_profile="$4"
  local tmp
  tmp=$(mktemp)
  jq \
    --arg captureStatus "${capture_status}" \
    --arg generatedAt "$(date --iso-8601=seconds)" \
    --arg captureProfile "${capture_profile}" \
    --argjson fromMs "${from_ms}" \
    --argjson toMs "${to_ms}" \
    '.capture.mode = "scripted"
    | .capture.status = $captureStatus
    | .capture.profile = $captureProfile
    | .capture.generatedAt = $generatedAt
    | .capture.fromMs = $fromMs
    | .capture.toMs = $toMs' \
    "${META_PATH}" > "${tmp}"
  mv "${tmp}" "${META_PATH}"
  chmod 600 "${META_PATH}"
}

render_dashboard() {
  local file_path="$1"
  local from_ms="$2"
  local to_ms="$3"

  curl -fsS \
    -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
    -o "${file_path}" \
    "${GRAFANA_CAPTURE_BASE_URL}/render/d/${DASHBOARD_UID}/${DASHBOARD_SLUG}?orgId=${GRAFANA_ORG_ID}&from=${from_ms}&to=${to_ms}&width=${DASHBOARD_WIDTH}&height=${DASHBOARD_HEIGHT}&tz=${GRAFANA_TZ}"
}

render_panel() {
  local file_path="$1"
  local panel_id="$2"
  local from_ms="$3"
  local to_ms="$4"

  curl -fsS \
    -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
    -o "${file_path}" \
    "${GRAFANA_CAPTURE_BASE_URL}/render/d-solo/${DASHBOARD_UID}/${DASHBOARD_SLUG}?orgId=${GRAFANA_ORG_ID}&panelId=${panel_id}&from=${from_ms}&to=${to_ms}&width=${PANEL_WIDTH}&height=${PANEL_HEIGHT}&tz=${GRAFANA_TZ}"
}

resolve_capture_profile() {
  if [[ -n "${CAPTURE_PROFILE}" ]]; then
    echo "${CAPTURE_PROFILE}"
    return
  fi

  local meta_profile
  meta_profile=$(jq -r '.capture.profile // empty' "${META_PATH}")
  if [[ -n "${meta_profile}" && "${meta_profile}" != "null" ]]; then
    echo "${meta_profile}"
    return
  fi

  local scenario
  scenario=$(jq -r '.scenario // empty' "${META_PATH}" | tr '[:upper:]' '[:lower:]')
  case "${scenario}" in
    w1|r1|m1|m2)
      echo "${scenario}"
      ;;
    *)
      echo "w1"
      ;;
  esac
}

configure_profile_panels() {
  local profile="$1"

  case "${profile}" in
    w1)
      PANEL_SPECS=(
        "ingest-rps.png:2"
        "ingest-latency.png:4"
        "http-error-ratio.png:6"
        "db-pool.png:7"
        "db-connections.png:8"
        "jvm-heap-by-app.png:9"
      )
      ;;
    r1)
      PANEL_SPECS=(
        "analytics-rps.png:3"
        "analytics-latency.png:5"
        "http-error-ratio.png:6"
        "db-pool.png:7"
        "db-connections.png:8"
        "jvm-heap-by-app.png:9"
      )
      ;;
    m1|m2)
      PANEL_SPECS=(
        "api-request-mix.png:1"
        "ingest-latency.png:4"
        "analytics-latency.png:5"
        "http-error-ratio.png:6"
        "db-pool.png:7"
        "db-connections.png:8"
        "jvm-heap-by-app.png:9"
      )
      ;;
    *)
      echo "Unknown capture profile: ${profile}" >&2
      exit 1
      ;;
  esac
}

clear_known_panel_outputs() {
  local out_dir="$1"

  rm -f \
    "${out_dir}/dashboard-overview.png" \
    "${out_dir}/api-request-mix.png" \
    "${out_dir}/ingest-rps.png" \
    "${out_dir}/ingest-latency.png" \
    "${out_dir}/analytics-rps.png" \
    "${out_dir}/analytics-latency.png" \
    "${out_dir}/http-error-ratio.png" \
    "${out_dir}/db-pool.png" \
    "${out_dir}/db-connections.png" \
    "${out_dir}/jvm-heap-by-app.png"
}

main() {
  require_command jq
  require_command curl
  require_command date

  if [[ -z "${META_PATH}" ]]; then
    echo "Usage: META_PATH=artifacts/perf/<scenario>/<runId>/meta.json scripts/perf/common/capture-grafana-render.sh" >&2
    exit 1
  fi

  if [[ ! -f "${META_PATH}" ]]; then
    echo "META_PATH not found: ${META_PATH}" >&2
    exit 1
  fi

  local out_dir run_started_at run_completed_at start_ms end_ms from_ms to_ms capture_profile
  out_dir=$(jq -r '.artifacts.outDir' "${META_PATH}")
  run_started_at=$(jq -r '.run.startedAt // .timestamp' "${META_PATH}")
  run_completed_at=$(jq -r '.run.completedAt // .updatedAt // .timestamp' "${META_PATH}")
  capture_profile=$(resolve_capture_profile)
  configure_profile_panels "${capture_profile}"

  if [[ -n "${FROM_MS}" && -n "${TO_MS}" ]]; then
    from_ms="${FROM_MS}"
    to_ms="${TO_MS}"
  else
    start_ms=$(iso_to_ms "${run_started_at}")
    end_ms=$(iso_to_ms "${run_completed_at}")
    from_ms=$((start_ms - (CAPTURE_PADDING_SEC * 1000)))
    to_ms=$((end_ms + (CAPTURE_PADDING_SEC * 1000)))
  fi

  trap 'update_capture_meta "failed" "${from_ms}" "${to_ms}" "'"${capture_profile}"'"' ERR

  mkdir -p "${out_dir}"
  clear_known_panel_outputs "${out_dir}"

  if [[ "${CAPTURE_SKIP_DASHBOARD}" != "true" ]]; then
    if ! render_dashboard "${out_dir}/dashboard-overview.png" "${from_ms}" "${to_ms}"; then
      echo "[grafana-capture] warning: full dashboard render failed; continuing with panel-only capture" >&2
      rm -f "${out_dir}/dashboard-overview.png"
    fi
  fi
  local panel_spec panel_file panel_id
  for panel_spec in "${PANEL_SPECS[@]}"; do
    panel_file="${panel_spec%%:*}"
    panel_id="${panel_spec##*:}"
    render_panel "${out_dir}/${panel_file}" "${panel_id}" "${from_ms}" "${to_ms}"
  done

  update_capture_meta "success" "${from_ms}" "${to_ms}" "${capture_profile}"
  trap - ERR
  echo "[grafana-capture] profile=${capture_profile} saved core panels to ${out_dir}"
}

main "$@"
