#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/home/ubuntu/click-checker"

require_file() {
  local path="$1"
  if [ ! -f "$path" ]; then
    echo "[codedeploy:after-install] missing file: $path" >&2
    exit 1
  fi
}

echo "[codedeploy:after-install] checking deployment directory"
[ -d "$APP_DIR" ] || {
  echo "[codedeploy:after-install] missing directory: $APP_DIR" >&2
  exit 1
}

cd "$APP_DIR"

require_file "$APP_DIR/docker-compose.yml"
require_file "$APP_DIR/docker-compose.prod.yml"
require_file "$APP_DIR/scripts/deploy-prod-blue-green.sh"
require_file "$APP_DIR/scripts/blue-green-prod-switch.sh"

chmod +x \
  "$APP_DIR/scripts/deploy-prod-blue-green.sh" \
  "$APP_DIR/scripts/blue-green-prod-switch.sh"

echo "[codedeploy:after-install] completed"
