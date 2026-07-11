#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VUE_DIR="$ROOT_DIR/social-sdk-spring-boot-starter/xianyu-vue"
DEMO_STATIC_DIR="$ROOT_DIR/social-sdk-demo/src/main/resources/static"

if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found in PATH" >&2
  exit 1
fi

pushd "$VUE_DIR" >/dev/null
if [ ! -d "$VUE_DIR/node_modules" ]; then
  npm install
fi
npm run build
popd >/dev/null

if [ -f "$DEMO_STATIC_DIR/index.html" ]; then
  cp "$DEMO_STATIC_DIR/index.html" "$DEMO_STATIC_DIR/legacy-demo.html"
fi

rm -rf "$DEMO_STATIC_DIR/assets"
cp -R "$VUE_DIR/dist"/* "$DEMO_STATIC_DIR"/

echo "Synced xianyu-vue dist to $DEMO_STATIC_DIR"
