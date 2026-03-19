#!/usr/bin/env bash
set -euo pipefail

META_PATH="${META_PATH:-${META:-${1:-}}}"
GRAFANA_BASE_URL="${GRAFANA_BASE_URL:-http://localhost:3000}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
GRAFANA_ORG_ID="${GRAFANA_ORG_ID:-1}"
GRAFANA_TZ="${GRAFANA_TZ:-Asia/Seoul}"
CAPTURE_PADDING_SEC="${CAPTURE_PADDING_SEC:-120}"
DASHBOARD_UID="${DASHBOARD_UID:-click-checker-overview}"
DASHBOARD_SLUG="${DASHBOARD_SLUG:-click-checker-overview}"
DASHBOARD_WIDTH="${DASHBOARD_WIDTH:-1800}"
DASHBOARD_HEIGHT="${DASHBOARD_HEIGHT:-1400}"
PANEL_WIDTH="${PANEL_WIDTH:-1400}"
PANEL_HEIGHT="${PANEL_HEIGHT:-500}"
FROM_MS="${FROM_MS:-}"
TO_MS="${TO_MS:-}"

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
  local tmp
  tmp=$(mktemp)
  jq \
    --arg captureStatus "${capture_status}" \
    --arg generatedAt "$(date --iso-8601=seconds)" \
    --argjson fromMs "${from_ms}" \
    --argjson toMs "${to_ms}" \
    '.capture.mode = "scripted"
    | .capture.status = $captureStatus
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
    "${GRAFANA_BASE_URL}/render/d/${DASHBOARD_UID}/${DASHBOARD_SLUG}?orgId=${GRAFANA_ORG_ID}&from=${from_ms}&to=${to_ms}&width=${DASHBOARD_WIDTH}&height=${DASHBOARD_HEIGHT}&tz=${GRAFANA_TZ}"
}

render_panel() {
  local file_path="$1"
  local panel_id="$2"
  local from_ms="$3"
  local to_ms="$4"

  curl -fsS \
    -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
    -o "${file_path}" \
    "${GRAFANA_BASE_URL}/render/d-solo/${DASHBOARD_UID}/${DASHBOARD_SLUG}?orgId=${GRAFANA_ORG_ID}&panelId=${panel_id}&from=${from_ms}&to=${to_ms}&width=${PANEL_WIDTH}&height=${PANEL_HEIGHT}&tz=${GRAFANA_TZ}"
}

main() {
  require_command jq
  require_command curl
  require_command date

  if [[ -z "${META_PATH}" ]]; then
    echo "Usage: META_PATH=artifacts/perf/<scenario>/<runId>/meta.json scripts/perf/capture-grafana-render.sh" >&2
    exit 1
  fi

  if [[ ! -f "${META_PATH}" ]]; then
    echo "META_PATH not found: ${META_PATH}" >&2
    exit 1
  fi

  local out_dir run_started_at run_completed_at start_ms end_ms from_ms to_ms
  out_dir=$(jq -r '.artifacts.outDir' "${META_PATH}")
  run_started_at=$(jq -r '.run.startedAt // .timestamp' "${META_PATH}")
  run_completed_at=$(jq -r '.run.completedAt // .updatedAt // .timestamp' "${META_PATH}")

  if [[ -n "${FROM_MS}" && -n "${TO_MS}" ]]; then
    from_ms="${FROM_MS}"
    to_ms="${TO_MS}"
  else
    start_ms=$(iso_to_ms "${run_started_at}")
    end_ms=$(iso_to_ms "${run_completed_at}")
    from_ms=$((start_ms - (CAPTURE_PADDING_SEC * 1000)))
    to_ms=$((end_ms + (CAPTURE_PADDING_SEC * 1000)))
  fi

  trap 'update_capture_meta "failed" "${from_ms}" "${to_ms}"' ERR

  mkdir -p "${out_dir}"

  render_dashboard "${out_dir}/dashboard-overview.png" "${from_ms}" "${to_ms}"
  render_panel "${out_dir}/ingest-rps.png" 2 "${from_ms}" "${to_ms}"
  render_panel "${out_dir}/ingest-latency.png" 4 "${from_ms}" "${to_ms}"
  render_panel "${out_dir}/db-pool.png" 7 "${from_ms}" "${to_ms}"
  render_panel "${out_dir}/db-connections.png" 8 "${from_ms}" "${to_ms}"

  update_capture_meta "success" "${from_ms}" "${to_ms}"
  trap - ERR
  echo "[grafana-capture] saved dashboard and core panels to ${out_dir}"
}

main "$@"
