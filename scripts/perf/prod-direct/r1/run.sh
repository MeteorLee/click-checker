#!/usr/bin/env bash
set -euo pipefail

META_PATH="${META_PATH:-${META:-${1:-}}}"
K6_IMAGE="${K6_IMAGE:-grafana/k6}"
K6_NETWORK="${K6_NETWORK:-click-checker_default}"
K6_SCRIPT="${K6_SCRIPT:-k6/r1/overview-read-heavy.js}"
CAPTURE_SCRIPT="${CAPTURE_SCRIPT:-scripts/perf/common/capture-grafana-render.sh}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

update_meta_status() {
  local status="$1"
  local tmp
  tmp=$(mktemp)
  jq --arg status "${status}" --arg updatedAt "$(date --iso-8601=seconds)" \
    '
    .status = $status
    | .updatedAt = $updatedAt
    | if $status == "running" then
        .run.startedAt = (.run.startedAt // $updatedAt)
      elif ($status == "success" or $status == "threshold_fail" or $status == "aborted") then
        .run.completedAt = $updatedAt
      else
        .
      end
    ' \
    "${META_PATH}" > "${tmp}"
  mv "${tmp}" "${META_PATH}"
  chmod 600 "${META_PATH}"
}

build_k6_command() {
  local phase="$1"
  local duration="$2"
  local thresholds_enabled="$3"
  local summary_path="$4"

  local base_url api_key run_id rate pre_allocated_vus max_vus p95_threshold_ms p99_threshold_ms
  local from to external_user_id event_type

  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  api_key=$(jq -r '.auth.apiKey' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  rate=$(jq -r '.load.rate' "${META_PATH}")
  pre_allocated_vus=$(jq -r '.load.preAllocatedVUs' "${META_PATH}")
  max_vus=$(jq -r '.load.maxVUs' "${META_PATH}")
  p95_threshold_ms=$(jq -r '.load.p95ThresholdMs' "${META_PATH}")
  p99_threshold_ms=$(jq -r '.load.p99ThresholdMs' "${META_PATH}")
  from=$(jq -r '.request.from' "${META_PATH}")
  to=$(jq -r '.request.to' "${META_PATH}")
  external_user_id=$(jq -r '.request.externalUserId // ""' "${META_PATH}")
  event_type=$(jq -r '.request.eventType // ""' "${META_PATH}")

  local summary_args=()
  if [[ -n "${summary_path}" ]]; then
    summary_args=(--summary-export "${summary_path}")
  fi

  docker run --rm \
    --network "${K6_NETWORK}" \
    --user "$(id -u):$(id -g)" \
    -v "$PWD":/work \
    -w /work \
    "${K6_IMAGE}" run \
    -e BASE_URL="${base_url}" \
    -e API_KEY="${api_key}" \
    -e SCENARIO="R1" \
    -e RUN_ID="${run_id}" \
    -e RUN_PHASE="${phase}" \
    -e RATE="${rate}" \
    -e DURATION="${duration}" \
    -e PRE_ALLOCATED_VUS="${pre_allocated_vus}" \
    -e MAX_VUS="${max_vus}" \
    -e FROM="${from}" \
    -e TO="${to}" \
    -e EXTERNAL_USER_ID="${external_user_id}" \
    -e EVENT_TYPE_FILTER="${event_type}" \
    -e THRESHOLDS_ENABLED="${thresholds_enabled}" \
    -e P95_THRESHOLD_MS="${p95_threshold_ms}" \
    -e P99_THRESHOLD_MS="${p99_threshold_ms}" \
    "${summary_args[@]}" \
    "${K6_SCRIPT}"
}

write_command_file() {
  local out_dir="$1"
  local command_file="${out_dir}/command.txt"
  local base_url run_id rate warmup duration cooldown from to
  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  rate=$(jq -r '.load.rate' "${META_PATH}")
  warmup=$(jq -r '.load.warmup' "${META_PATH}")
  duration=$(jq -r '.load.duration' "${META_PATH}")
  cooldown=$(jq -r '.load.cooldown' "${META_PATH}")
  from=$(jq -r '.request.from' "${META_PATH}")
  to=$(jq -r '.request.to' "${META_PATH}")

  cat > "${command_file}" <<EOF
docker run --rm --network ${K6_NETWORK} -v "\$PWD":/work -w /work ${K6_IMAGE} run \
  -e BASE_URL=${base_url} \
  -e API_KEY=[REDACTED] \
  -e SCENARIO=R1 \
  -e RUN_ID=${run_id} \
  -e RATE=${rate} \
  -e FROM=${from} \
  -e TO=${to} \
  -e WARMUP=${warmup} \
  -e DURATION=${duration} \
  -e COOLDOWN=${cooldown} \
  ${K6_SCRIPT}
EOF
}

main() {
  require_command docker
  require_command jq

  if [[ -z "${META_PATH}" ]]; then
    echo "Usage: META_PATH=artifacts/perf/prod-direct/r1/<runId>/meta.json scripts/perf/prod-direct/r1/run.sh" >&2
    exit 1
  fi

  if [[ ! -f "${META_PATH}" ]]; then
    echo "META_PATH not found: ${META_PATH}" >&2
    exit 1
  fi

  local out_dir warmup duration cooldown
  out_dir=$(jq -r '.artifacts.outDir' "${META_PATH}")
  warmup=$(jq -r '.load.warmup' "${META_PATH}")
  duration=$(jq -r '.load.duration' "${META_PATH}")
  cooldown=$(jq -r '.load.cooldown' "${META_PATH}")

  mkdir -p "${out_dir}"
  chmod 700 "${out_dir}"
  write_command_file "${out_dir}"

  update_meta_status "running"

  if [[ "${warmup}" != "0" && "${warmup}" != "0s" ]]; then
    echo "[r1-run] warmup start (${warmup})"
    build_k6_command "warmup" "${warmup}" "false" "" \
      | tee "${out_dir}/warmup-console.log"
  fi

  echo "[r1-run] main start (${duration})"
  local main_status=0
  set +e
  build_k6_command "main" "${duration}" "true" "/work/${out_dir}/summary.json" \
    | tee "${out_dir}/console.log"
  main_status=${PIPESTATUS[0]}
  set -e

  if [[ "${cooldown}" != "0" && "${cooldown}" != "0s" ]]; then
    echo "[r1-run] cooldown start (${cooldown})"
    sleep "${cooldown}"
  fi

  local status
  if [[ ${main_status} -eq 0 ]]; then
    status="success"
  elif [[ -f "${out_dir}/summary.json" ]]; then
    status="threshold_fail"
  else
    status="aborted"
  fi

  update_meta_status "${status}"
  echo "[r1-run] status=${status}"

  local capture_mode
  capture_mode=$(jq -r '.capture.mode // "manual"' "${META_PATH}")
  if [[ "${capture_mode}" == "scripted" ]]; then
    echo "[r1-run] grafana capture start"
    META_PATH="${META_PATH}" "${CAPTURE_SCRIPT}"
  fi

  if [[ ${main_status} -ne 0 ]]; then
    exit "${main_status}"
  fi
}

main "$@"
