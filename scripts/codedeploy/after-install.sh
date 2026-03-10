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
require_file "$APP_DIR/scripts/deploy-prod-blue-green.sh"
require_file "$APP_DIR/scripts/blue-green-prod-switch.sh"
require_file "$IMAGE_DETAIL_FILE"

chmod +x \
  "$APP_DIR/scripts/deploy-prod-blue-green.sh" \
  "$APP_DIR/scripts/blue-green-prod-switch.sh"

APP_IMAGE=$(awk -F= '$1 == "APP_IMAGE" { print substr($0, index($0, "=") + 1) }' "$IMAGE_DETAIL_FILE")

if [ -z "$APP_IMAGE" ]; then
  echo "[codedeploy:after-install] APP_IMAGE is missing in $IMAGE_DETAIL_FILE" >&2
  exit 1
fi

cat > "$DEPLOY_ENV_FILE" <<EOF
APP_IMAGE=$APP_IMAGE
EOF

echo "[codedeploy:after-install] prepared deploy env with APP_IMAGE=$APP_IMAGE"
echo "[codedeploy:after-install] completed"
