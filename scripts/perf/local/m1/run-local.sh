#!/usr/bin/env bash
set -euo pipefail

META_PATH="${META_PATH:-${META:-${1:-}}}"
K6_IMAGE="${K6_IMAGE:-grafana/k6}"
K6_NETWORK="${K6_NETWORK:-click-checker_default}"
K6_SCRIPT="${K6_SCRIPT:-k6/m1/write-plus-overview-mixed.js}"
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

  local base_url api_key run_id
  local write_rate read_rate
  local write_pre_allocated_vus write_max_vus read_pre_allocated_vus read_max_vus
  local write_p95_threshold_ms write_p99_threshold_ms read_p95_threshold_ms read_p99_threshold_ms
  local write_path_prefix write_event_type write_path_count write_existing_user_pool_size write_existing_user_ratio
  local read_from read_to read_external_user_id read_event_type

  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  api_key=$(jq -r '.auth.apiKey' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  write_rate=$(jq -r '.load.writeRate' "${META_PATH}")
  read_rate=$(jq -r '.load.readRate' "${META_PATH}")
  write_pre_allocated_vus=$(jq -r '.load.writePreAllocatedVUs' "${META_PATH}")
  write_max_vus=$(jq -r '.load.writeMaxVUs' "${META_PATH}")
  read_pre_allocated_vus=$(jq -r '.load.readPreAllocatedVUs' "${META_PATH}")
  read_max_vus=$(jq -r '.load.readMaxVUs' "${META_PATH}")
  write_p95_threshold_ms=$(jq -r '.load.writeP95ThresholdMs' "${META_PATH}")
  write_p99_threshold_ms=$(jq -r '.load.writeP99ThresholdMs' "${META_PATH}")
  read_p95_threshold_ms=$(jq -r '.load.readP95ThresholdMs' "${META_PATH}")
  read_p99_threshold_ms=$(jq -r '.load.readP99ThresholdMs' "${META_PATH}")
  write_path_prefix=$(jq -r '.request.write.pathPrefix' "${META_PATH}")
  write_event_type=$(jq -r '.request.write.eventType' "${META_PATH}")
  write_path_count=$(jq -r '.request.write.pathCount' "${META_PATH}")
  write_existing_user_pool_size=$(jq -r '.request.write.existingUserPoolSize' "${META_PATH}")
  write_existing_user_ratio=$(jq -r '.request.write.existingUserRatio' "${META_PATH}")
  read_from=$(jq -r '.request.read.from' "${META_PATH}")
  read_to=$(jq -r '.request.read.to' "${META_PATH}")
  read_external_user_id=$(jq -r '.request.read.externalUserId // ""' "${META_PATH}")
  read_event_type=$(jq -r '.request.read.eventType // ""' "${META_PATH}")

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
    -e SCENARIO="M1" \
    -e RUN_ID="${run_id}" \
    -e RUN_PHASE="${phase}" \
    -e DURATION="${duration}" \
    -e WRITE_RATE="${write_rate}" \
    -e READ_RATE="${read_rate}" \
    -e WRITE_PRE_ALLOCATED_VUS="${write_pre_allocated_vus}" \
    -e WRITE_MAX_VUS="${write_max_vus}" \
    -e READ_PRE_ALLOCATED_VUS="${read_pre_allocated_vus}" \
    -e READ_MAX_VUS="${read_max_vus}" \
    -e WRITE_PATH_PREFIX="${write_path_prefix}" \
    -e WRITE_EVENT_TYPE="${write_event_type}" \
    -e WRITE_PATH_COUNT="${write_path_count}" \
    -e WRITE_EXISTING_USER_POOL_SIZE="${write_existing_user_pool_size}" \
    -e WRITE_EXISTING_USER_RATIO="${write_existing_user_ratio}" \
    -e READ_FROM="${read_from}" \
    -e READ_TO="${read_to}" \
    -e READ_EXTERNAL_USER_ID="${read_external_user_id}" \
    -e READ_EVENT_TYPE_FILTER="${read_event_type}" \
    -e THRESHOLDS_ENABLED="${thresholds_enabled}" \
    -e WRITE_P95_THRESHOLD_MS="${write_p95_threshold_ms}" \
    -e WRITE_P99_THRESHOLD_MS="${write_p99_threshold_ms}" \
    -e READ_P95_THRESHOLD_MS="${read_p95_threshold_ms}" \
    -e READ_P99_THRESHOLD_MS="${read_p99_threshold_ms}" \
    "${summary_args[@]}" \
    "${K6_SCRIPT}"
}

write_command_file() {
  local out_dir="$1"
  local command_file="${out_dir}/command.txt"
  local base_url run_id warmup duration cooldown write_rate read_rate read_from read_to
  base_url=$(jq -r '.env.baseUrl' "${META_PATH}")
  run_id=$(jq -r '.runId' "${META_PATH}")
  warmup=$(jq -r '.load.warmup' "${META_PATH}")
  duration=$(jq -r '.load.duration' "${META_PATH}")
  cooldown=$(jq -r '.load.cooldown' "${META_PATH}")
  write_rate=$(jq -r '.load.writeRate' "${META_PATH}")
  read_rate=$(jq -r '.load.readRate' "${META_PATH}")
  read_from=$(jq -r '.request.read.from' "${META_PATH}")
  read_to=$(jq -r '.request.read.to' "${META_PATH}")

  cat > "${command_file}" <<EOF
docker run --rm --network ${K6_NETWORK} -v "\$PWD":/work -w /work ${K6_IMAGE} run \
  -e BASE_URL=${base_url} \
  -e API_KEY=[REDACTED] \
  -e SCENARIO=M1 \
  -e RUN_ID=${run_id} \
  -e WRITE_RATE=${write_rate} \
  -e READ_RATE=${read_rate} \
  -e READ_FROM=${read_from} \
  -e READ_TO=${read_to} \
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
    echo "Usage: META_PATH=artifacts/perf/local/m1/<runId>/meta.json scripts/perf/local/m1/run-local.sh" >&2
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
    echo "[m1-run] warmup start (${warmup})"
    build_k6_command "warmup" "${warmup}" "false" "" \
      | tee "${out_dir}/warmup-console.log"
  fi

  echo "[m1-run] main start (${duration})"
  local main_status=0
  set +e
  build_k6_command "main" "${duration}" "true" "/work/${out_dir}/summary.json" \
    | tee "${out_dir}/console.log"
  main_status=${PIPESTATUS[0]}
  set -e

  if [[ "${cooldown}" != "0" && "${cooldown}" != "0s" ]]; then
    echo "[m1-run] cooldown start (${cooldown})"
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
  echo "[m1-run] status=${status}"

  local capture_mode
  capture_mode=$(jq -r '.capture.mode // "manual"' "${META_PATH}")
  if [[ "${capture_mode}" == "scripted" ]]; then
    echo "[m1-run] grafana capture start"
    META_PATH="${META_PATH}" "${CAPTURE_SCRIPT}"
  fi

  if [[ ${main_status} -ne 0 ]]; then
    exit "${main_status}"
  fi
}

main "$@"
