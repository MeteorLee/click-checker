#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/home/ubuntu/click-checker"
DEPLOY_SCRIPT="$APP_DIR/scripts/deploy-prod-blue-green.sh"
DEPLOY_ENV_FILE="$APP_DIR/.env.codedeploy"

require_file() {
  local path="$1"
  if [ ! -f "$path" ]; then
    echo "[codedeploy:application-start] missing file: $path" >&2
    exit 1
  fi
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "[codedeploy:application-start] required command not found: $name" >&2
    exit 1
  fi
}

echo "[codedeploy:application-start] starting blue-green deploy"

cd "$APP_DIR"

require_command aws
require_command docker
require_file "$DEPLOY_ENV_FILE"

if [ ! -x "$DEPLOY_SCRIPT" ]; then
  echo "[codedeploy:application-start] deploy script is not executable: $DEPLOY_SCRIPT" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$DEPLOY_ENV_FILE"
set +a

if [ -z "${APP_IMAGE:-}" ]; then
  echo "[codedeploy:application-start] APP_IMAGE is empty" >&2
  exit 1
fi

REGISTRY_HOST="${APP_IMAGE%%/*}"

echo "[codedeploy:application-start] logging in to ECR registry"
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin "$REGISTRY_HOST"

echo "[codedeploy:application-start] pulling image $APP_IMAGE"
docker pull "$APP_IMAGE"

"$DEPLOY_SCRIPT"

echo "[codedeploy:application-start] completed"
