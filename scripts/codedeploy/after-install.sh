#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/home/ubuntu/click-checker"
IMAGE_DETAIL_FILE="$APP_DIR/deployment/image-detail.env"
DEPLOY_ENV_FILE="$APP_DIR/.env.codedeploy"

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
require_file "$APP_DIR/scripts/deploy/deploy-prod-orchestrator.sh"
require_file "$APP_DIR/scripts/observability/deploy-observability-prod.sh"
require_file "$APP_DIR/scripts/observability/restart-observability-prod.sh"
require_file "$APP_DIR/scripts/observability/validate-observability-prod.sh"
require_file "$APP_DIR/scripts/deploy/blue-green-prod-lib.sh"
require_file "$APP_DIR/scripts/deploy/deploy-smoke.sh"
require_file "$APP_DIR/scripts/deploy/deploy-drain.sh"
require_file "$APP_DIR/scripts/deploy/deploy-frontend.sh"
require_file "$APP_DIR/scripts/deploy/recover-frontend.sh"
require_file "$APP_DIR/scripts/deploy/apply-public-nginx-config.sh"
require_file "$IMAGE_DETAIL_FILE"

chmod +x \
  "$APP_DIR/scripts/deploy/deploy-prod-orchestrator.sh" \
  "$APP_DIR/scripts/observability/deploy-observability-prod.sh" \
  "$APP_DIR/scripts/observability/restart-observability-prod.sh" \
  "$APP_DIR/scripts/observability/validate-observability-prod.sh" \
  "$APP_DIR/scripts/deploy/blue-green-prod-lib.sh" \
  "$APP_DIR/scripts/deploy/deploy-smoke.sh" \
  "$APP_DIR/scripts/deploy/deploy-drain.sh" \
  "$APP_DIR/scripts/deploy/deploy-frontend.sh" \
  "$APP_DIR/scripts/deploy/recover-frontend.sh" \
  "$APP_DIR/scripts/deploy/apply-public-nginx-config.sh"

APP_IMAGE=$(awk -F= '$1 == "APP_IMAGE" { print substr($0, index($0, "=") + 1) }' "$IMAGE_DETAIL_FILE")
FRONTEND_IMAGE=$(awk -F= '$1 == "FRONTEND_IMAGE" { print substr($0, index($0, "=") + 1) }' "$IMAGE_DETAIL_FILE")
SENTRY_RELEASE="${APP_IMAGE##*:}"

if [ -z "$APP_IMAGE" ]; then
  echo "[codedeploy:after-install] APP_IMAGE is missing in $IMAGE_DETAIL_FILE" >&2
  exit 1
fi

if [ -z "$FRONTEND_IMAGE" ]; then
  echo "[codedeploy:after-install] FRONTEND_IMAGE is missing in $IMAGE_DETAIL_FILE" >&2
  exit 1
fi

cat > "$DEPLOY_ENV_FILE" <<EOF
APP_IMAGE=$APP_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
SENTRY_RELEASE=$SENTRY_RELEASE
EOF

echo "[codedeploy:after-install] prepared deploy env with APP_IMAGE=$APP_IMAGE"
echo "[codedeploy:after-install] prepared deploy env with FRONTEND_IMAGE=$FRONTEND_IMAGE"
echo "[codedeploy:after-install] prepared deploy env with SENTRY_RELEASE=$SENTRY_RELEASE"
echo "[codedeploy:after-install] completed"
