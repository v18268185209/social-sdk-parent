#!/usr/bin/env bash
# Build sqlite/mysql/postgres Docker images and optionally push them to Alibaba Cloud ACR.
#
# Required for push:
#   ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
#   ACR_NAMESPACE=your-namespace
# Optional:
#   IMAGE_NAME=xianyu-manager
#   TAG=latest
#   ACR_USERNAME=xxx
#   ACR_PASSWORD=xxx
#
# Usage:
#   ./publish-acr.sh --help
#   ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com ACR_NAMESPACE=ns TAG=v1.0.0 ./publish-acr.sh
#   ./publish-acr.sh --build-only
#   ./publish-acr.sh --mode sqlite --push

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKERFILE="$SCRIPT_DIR/Dockerfile"

ACR_REGISTRY="${ACR_REGISTRY:-}"
ACR_NAMESPACE="${ACR_NAMESPACE:-}"
IMAGE_NAME="${IMAGE_NAME:-xianyu-manager}"
TAG="${TAG:-latest}"
PUSH="${PUSH:-true}"
BUILD="${BUILD:-true}"
MODE="all"
PLATFORM="${PLATFORM:-}"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

show_usage() {
  cat <<EOF
Usage: publish-acr.sh [options]

Build three Docker image variants and push them to Alibaba Cloud ACR.

Options:
  --mode MODE       sqlite | mysql | postgres | all (default: all)
  --tag TAG         Image tag (default: env TAG or latest)
  --registry URL    ACR registry, e.g. registry.cn-hangzhou.aliyuncs.com
  --namespace NS    ACR namespace
  --image NAME      Image repository name (default: xianyu-manager)
  --build-only      Build images only, do not push
  --push-only       Push existing images only, do not build
  --push            Build and push (default)
  --platform P      docker build --platform value, e.g. linux/amd64
  -h, --help        Show help

Environment variables:
  ACR_REGISTRY      Alibaba Cloud ACR registry host
  ACR_NAMESPACE     Alibaba Cloud ACR namespace
  IMAGE_NAME        ACR repository/image name, default xianyu-manager
  TAG               Image tag, default latest
  ACR_USERNAME      If set with ACR_PASSWORD, script runs docker login first
  ACR_PASSWORD      ACR password/token
  PLATFORM          Optional docker build platform

Tags produced for TAG=v1.0.0:
  <registry>/<namespace>/<image>:sqlite-v1.0.0
  <registry>/<namespace>/<image>:mysql-v1.0.0
  <registry>/<namespace>/<image>:postgres-v1.0.0
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode) MODE="${2:-}"; shift 2 ;;
    --tag) TAG="${2:-}"; shift 2 ;;
    --registry) ACR_REGISTRY="${2:-}"; shift 2 ;;
    --namespace) ACR_NAMESPACE="${2:-}"; shift 2 ;;
    --image) IMAGE_NAME="${2:-}"; shift 2 ;;
    --build-only) BUILD="true"; PUSH="false"; shift ;;
    --push-only) BUILD="false"; PUSH="true"; shift ;;
    --push) BUILD="true"; PUSH="true"; shift ;;
    --platform) PLATFORM="${2:-}"; shift 2 ;;
    -h|--help) show_usage; exit 0 ;;
    *) log_error "Unknown option: $1"; show_usage; exit 1 ;;
  esac
done

case "$MODE" in
  sqlite|mysql|postgres) MODES=("$MODE") ;;
  all) MODES=(sqlite mysql postgres) ;;
  *) log_error "Invalid mode: $MODE"; exit 1 ;;
esac

if [[ "$PUSH" == "true" ]]; then
  [[ -n "$ACR_REGISTRY" ]] || { log_error "ACR_REGISTRY is required when pushing"; exit 1; }
  [[ -n "$ACR_NAMESPACE" ]] || { log_error "ACR_NAMESPACE is required when pushing"; exit 1; }
fi

command -v docker >/dev/null 2>&1 || { log_error "Docker is not installed or not in PATH"; exit 1; }
[[ -f "$DOCKERFILE" ]] || { log_error "Dockerfile not found: $DOCKERFILE"; exit 1; }

REMOTE_PREFIX="${ACR_REGISTRY%/}/${ACR_NAMESPACE}/${IMAGE_NAME}"
LOCAL_PREFIX="${IMAGE_NAME}"

login_if_needed() {
  if [[ "$PUSH" != "true" ]]; then return 0; fi
  if [[ -n "${ACR_USERNAME:-}" && -n "${ACR_PASSWORD:-}" ]]; then
    log_step "Logging in to ACR: $ACR_REGISTRY"
    printf '%s' "$ACR_PASSWORD" | docker login --username "$ACR_USERNAME" --password-stdin "$ACR_REGISTRY"
  else
    log_warn "ACR_USERNAME/ACR_PASSWORD not set; assuming docker is already logged in to $ACR_REGISTRY"
  fi
}

build_one() {
  local db_mode="$1"
  local local_tag="${LOCAL_PREFIX}:${db_mode}-${TAG}"
  local remote_tag="${REMOTE_PREFIX}:${db_mode}-${TAG}"
  local args=(build --build-arg "DB_MODE=${db_mode}" -f "$DOCKERFILE" -t "$local_tag")
  [[ -n "$ACR_REGISTRY" && -n "$ACR_NAMESPACE" ]] && args+=(-t "$remote_tag")
  [[ -n "$PLATFORM" ]] && args+=(--platform "$PLATFORM")
  args+=("$PROJECT_ROOT")

  log_step "Building ${db_mode} image: $local_tag"
  docker "${args[@]}"
  if [[ -n "$ACR_REGISTRY" && -n "$ACR_NAMESPACE" ]]; then
    log_info "Tagged remote image: $remote_tag"
  fi
}

push_one() {
  local db_mode="$1"
  local remote_tag="${REMOTE_PREFIX}:${db_mode}-${TAG}"
  log_step "Pushing ${db_mode} image: $remote_tag"
  docker push "$remote_tag"
}

main() {
  echo "=================================================="
  echo " social-sdk-parent Docker ACR publisher"
  echo " Modes : ${MODES[*]}"
  echo " Tag   : $TAG"
  echo " Image : ${ACR_REGISTRY:+$REMOTE_PREFIX}${ACR_REGISTRY:-$LOCAL_PREFIX}"
  echo "=================================================="

  login_if_needed

  for mode in "${MODES[@]}"; do
    [[ "$BUILD" == "true" ]] && build_one "$mode"
    [[ "$PUSH" == "true" ]] && push_one "$mode"
  done

  log_info "Done."
}

main
