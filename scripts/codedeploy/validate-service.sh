#!/usr/bin/env bash
set -euo pipefail

APP_DOMAIN="clickchecker.dev"

echo "[codedeploy:validate-service] checking public health"

if ! curl --max-time 5 --resolve "${APP_DOMAIN}:443:127.0.0.1" -fsS \
  "https://${APP_DOMAIN}/actuator/health" | grep -q '"status":"UP"'; then
  echo "[codedeploy:validate-service] public health check failed" >&2
  exit 1
fi

echo "[codedeploy:validate-service] completed"
