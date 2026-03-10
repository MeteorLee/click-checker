#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/home/ubuntu/click-checker"
DEPLOY_SCRIPT="$APP_DIR/scripts/deploy-prod-blue-green.sh"

echo "[codedeploy:application-start] starting blue-green deploy"

cd "$APP_DIR"

if [ ! -x "$DEPLOY_SCRIPT" ]; then
  echo "[codedeploy:application-start] deploy script is not executable: $DEPLOY_SCRIPT" >&2
  exit 1
fi

"$DEPLOY_SCRIPT"

echo "[codedeploy:application-start] completed"
