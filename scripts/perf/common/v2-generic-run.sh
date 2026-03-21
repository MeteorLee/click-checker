#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/v2-dataset-lib.sh"

META_PATH="${META_PATH:-${META:-${1:-}}}"
SCENARIO_CODE="${SCENARIO_CODE:-}"
K6_IMAGE="${K6_IMAGE:-grafana/k6}"
K6_NETWORK="${K6_NETWORK:-click-checker_default}"
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

flatten_r6_profiles() {
  local request_json="$1"
  jq -nc \
    --argjson request "${request_json}" \
    '
    ($request.familyWeights.r4 // 0) as $r4Weight
    | ($request.familyWeights.r5 // 0) as $r5Weight
    | ([($request.r4Profiles // [])[] | . + {weight: ((.weight * $r4Weight) / 100)}]
      + [($request.r5Profiles // [])[] | . + {weight: ((.weight * $r5Weight) / 100)}])
    '
}

build_command_file() {
  local out_dir="$1"
  local command_file="${out_dir}/command.txt"
  cat > "${command_file}" <<EOF
META_PATH=${META_PATH}
SCENARIO_CODE=${SCENARIO_CODE}
K6_IMAGE=${K6_IMAGE}
K6_NETWORK=${K6_NETWORK}
scripts/perf/common/v2-generic-run.sh
EOF
}

update_dataset_reset_state() {
  local status="$1"
  local state_mutation dataset_meta_path run_id

  state_mutation="$(jq -r '.stateMutation // "read_only"' "${META_PATH}")"
  if [[ "${state_mutation}" != "write_overlay" ]]; then
    return
  fi

  dataset_meta_path="$(jq -r '.dataset.metaPath // empty' "${META_PATH}")"
  run_id="$(jq -r '.runId' "${META_PATH}")"
  if [[ -z "${dataset_meta_path}" || ! -f "${dataset_meta_path}" ]]; then
    return
  fi

  v2_mark_reset_state_dirty "${dataset_meta_path}" "run" "${SCENARIO_CODE}" "${run_id}" "${status}"
}

run_w2() {
  local phase="$1"
  local duration="$2"
  local thresholds_enabled="$3"
  local summary_path="$4"
  local base_url run_id rate pre_allocated_vus max_vus p95_ms p99_ms orgs_json request_json

  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  rate=$(jq -r '.load.rate' "${META_PATH}")
  pre_allocated_vus=$(jq -r '.load.preAllocatedVUs' "${META_PATH}")
  max_vus=$(jq -r '.load.maxVUs' "${META_PATH}")
  p95_ms=$(jq -r '.load.p95Ms' "${META_PATH}")
  p99_ms=$(jq -r '.load.p99Ms' "${META_PATH}")
  orgs_json=$(jq -c '.auth.orgs' "${META_PATH}")
  request_json=$(jq -c '.request.write // .request' "${META_PATH}")

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
    -e SCENARIO="${SCENARIO_CODE}" \
    -e RUN_ID="${run_id}" \
    -e RUN_PHASE="${phase}" \
    -e RATE="${rate}" \
    -e DURATION="${duration}" \
    -e PRE_ALLOCATED_VUS="${pre_allocated_vus}" \
    -e MAX_VUS="${max_vus}" \
    -e THRESHOLDS_ENABLED="${thresholds_enabled}" \
    -e P95_THRESHOLD_MS="${p95_ms}" \
    -e P99_THRESHOLD_MS="${p99_ms}" \
    -e ORGS_JSON="${orgs_json}" \
    -e REQUEST_JSON="${request_json}" \
    "${summary_args[@]}" \
    k6/v2/write-multi-tenant.js
}

run_read_mix() {
  local phase="$1"
  local duration="$2"
  local thresholds_enabled="$3"
  local summary_path="$4"
  local base_url run_id rate pre_allocated_vus max_vus p95_ms p99_ms orgs_json profiles_json request_json

  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  rate=$(jq -r '.load.rate' "${META_PATH}")
  pre_allocated_vus=$(jq -r '.load.preAllocatedVUs' "${META_PATH}")
  max_vus=$(jq -r '.load.maxVUs' "${META_PATH}")
  p95_ms=$(jq -r '.load.p95Ms' "${META_PATH}")
  p99_ms=$(jq -r '.load.p99Ms' "${META_PATH}")
  orgs_json=$(jq -c '.auth.orgs' "${META_PATH}")

  request_json=$(jq -c '.request' "${META_PATH}")
  profiles_json=$(
    jq -e '.request.profiles' "${META_PATH}" >/dev/null 2>&1 \
      && jq -c '.request.profiles' "${META_PATH}" \
      || flatten_r6_profiles "${request_json}"
  )

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
    -e SCENARIO="${SCENARIO_CODE}" \
    -e RUN_ID="${run_id}" \
    -e RUN_PHASE="${phase}" \
    -e RATE="${rate}" \
    -e DURATION="${duration}" \
    -e PRE_ALLOCATED_VUS="${pre_allocated_vus}" \
    -e MAX_VUS="${max_vus}" \
    -e THRESHOLDS_ENABLED="${thresholds_enabled}" \
    -e P95_THRESHOLD_MS="${p95_ms}" \
    -e P99_THRESHOLD_MS="${p99_ms}" \
    -e ORGS_JSON="${orgs_json}" \
    -e PROFILES_JSON="${profiles_json}" \
    "${summary_args[@]}" \
    k6/v2/read-mix.js
}

run_m2() {
  local phase="$1"
  local duration="$2"
  local thresholds_enabled="$3"
  local summary_path="$4"
  local base_url run_id orgs_json write_request_json read_request_json read_profiles_json
  local write_rate read_rate write_pre_allocated_vus write_max_vus read_pre_allocated_vus read_max_vus
  local write_p95_ms write_p99_ms read_p95_ms read_p99_ms

  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  orgs_json=$(jq -c '.auth.orgs' "${META_PATH}")
  write_request_json=$(jq -c '.request.write' "${META_PATH}")
  read_request_json=$(jq -c '.request.read' "${META_PATH}")
  read_profiles_json=$(flatten_r6_profiles "${read_request_json}")
  write_rate=$(jq -r '.load.writeRate' "${META_PATH}")
  read_rate=$(jq -r '.load.readRate' "${META_PATH}")
  write_pre_allocated_vus=$(jq -r '.load.writePreAllocatedVUs' "${META_PATH}")
  write_max_vus=$(jq -r '.load.writeMaxVUs' "${META_PATH}")
  read_pre_allocated_vus=$(jq -r '.load.readPreAllocatedVUs' "${META_PATH}")
  read_max_vus=$(jq -r '.load.readMaxVUs' "${META_PATH}")
  write_p95_ms=$(jq -r '.load.writeP95Ms' "${META_PATH}")
  write_p99_ms=$(jq -r '.load.writeP99Ms' "${META_PATH}")
  read_p95_ms=$(jq -r '.load.readP95Ms' "${META_PATH}")
  read_p99_ms=$(jq -r '.load.readP99Ms' "${META_PATH}")

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
    -e SCENARIO="${SCENARIO_CODE}" \
    -e RUN_ID="${run_id}" \
    -e RUN_PHASE="${phase}" \
    -e DURATION="${duration}" \
    -e WRITE_RATE="${write_rate}" \
    -e READ_RATE="${read_rate}" \
    -e WRITE_PRE_ALLOCATED_VUS="${write_pre_allocated_vus}" \
    -e WRITE_MAX_VUS="${write_max_vus}" \
    -e READ_PRE_ALLOCATED_VUS="${read_pre_allocated_vus}" \
    -e READ_MAX_VUS="${read_max_vus}" \
    -e WRITE_P95_THRESHOLD_MS="${write_p95_ms}" \
    -e WRITE_P99_THRESHOLD_MS="${write_p99_ms}" \
    -e READ_P95_THRESHOLD_MS="${read_p95_ms}" \
    -e READ_P99_THRESHOLD_MS="${read_p99_ms}" \
    -e THRESHOLDS_ENABLED="${thresholds_enabled}" \
    -e ORGS_JSON="${orgs_json}" \
    -e WRITE_REQUEST_JSON="${write_request_json}" \
    -e READ_PROFILES_JSON="${read_profiles_json}" \
    "${summary_args[@]}" \
    k6/v2/mixed.js
}

main() {
  require_command docker
  require_command jq

  if [[ -z "${META_PATH}" ]]; then
    echo "META_PATH is required" >&2
    exit 1
  fi

  if [[ ! -f "${META_PATH}" ]]; then
    echo "META_PATH not found: ${META_PATH}" >&2
    exit 1
  fi

  local meta_scenario out_dir warmup duration cooldown capture_mode status main_status=0
  meta_scenario=$(jq -r '.scenario' "${META_PATH}")
  if [[ -n "${SCENARIO_CODE}" && "${SCENARIO_CODE}" != "${meta_scenario}" ]]; then
    echo "SCENARIO_CODE (${SCENARIO_CODE}) does not match meta scenario (${meta_scenario})" >&2
    exit 1
  fi
  SCENARIO_CODE="${meta_scenario}"

  out_dir=$(jq -r '.artifacts.outDir' "${META_PATH}")
  warmup=$(jq -r '.load.warmup' "${META_PATH}")
  duration=$(jq -r '.load.duration' "${META_PATH}")
  cooldown=$(jq -r '.load.cooldown' "${META_PATH}")
  capture_mode=$(jq -r '.capture.mode // "manual"' "${META_PATH}")

  mkdir -p "${out_dir}"
  chmod 700 "${out_dir}"
  build_command_file "${out_dir}"

  update_meta_status "running"

  if [[ "${warmup}" != "0" && "${warmup}" != "0s" ]]; then
    echo "[${SCENARIO_CODE,,}-run] warmup start (${warmup})"
    case "${SCENARIO_CODE}" in
      W2) run_w2 "warmup" "${warmup}" "false" "" | tee "${out_dir}/warmup-console.log" ;;
      R4|R5|R6) run_read_mix "warmup" "${warmup}" "false" "" | tee "${out_dir}/warmup-console.log" ;;
      M2) run_m2 "warmup" "${warmup}" "false" "" | tee "${out_dir}/warmup-console.log" ;;
      *)
        echo "Unsupported scenario: ${SCENARIO_CODE}" >&2
        exit 1
        ;;
    esac
  fi

  echo "[${SCENARIO_CODE,,}-run] main start (${duration})"
  set +e
  case "${SCENARIO_CODE}" in
    W2) run_w2 "main" "${duration}" "true" "/work/${out_dir}/summary.json" | tee "${out_dir}/console.log" ;;
    R4|R5|R6) run_read_mix "main" "${duration}" "true" "/work/${out_dir}/summary.json" | tee "${out_dir}/console.log" ;;
    M2) run_m2 "main" "${duration}" "true" "/work/${out_dir}/summary.json" | tee "${out_dir}/console.log" ;;
    *)
      echo "Unsupported scenario: ${SCENARIO_CODE}" >&2
      exit 1
      ;;
  esac
  main_status=${PIPESTATUS[0]}
  set -e

  if [[ "${cooldown}" != "0" && "${cooldown}" != "0s" ]]; then
    echo "[${SCENARIO_CODE,,}-run] cooldown start (${cooldown})"
    sleep "${cooldown}"
  fi

  if [[ ${main_status} -eq 0 ]]; then
    status="success"
  elif [[ -f "${out_dir}/summary.json" ]]; then
    status="threshold_fail"
  else
    status="aborted"
  fi

  update_meta_status "${status}"
  update_dataset_reset_state "${status}"
  echo "[${SCENARIO_CODE,,}-run] status=${status}"

  if [[ "${capture_mode}" == "scripted" ]]; then
    echo "[${SCENARIO_CODE,,}-run] grafana capture start"
    META_PATH="${META_PATH}" "${CAPTURE_SCRIPT}"
  fi

  if [[ ${main_status} -ne 0 ]]; then
    exit "${main_status}"
  fi
}

main "$@"
