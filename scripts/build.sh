#!/usr/bin/env bash
# ============================================================================
# build.sh — 闲鱼管理器 · 统一构建入口
#
# 支持的目标:
#   all       打包所有目标（elec/icon + ios + android + docker）
#   elec/icon 桌面应用（Electron，Mac + Windows）
#   ios       iOS 应用（Capacitor + Xcode）
#   android   Android 应用（Capacitor + Gradle）
#   docker    Docker 镜像（SQLite / MySQL 8）
#   jar       仅构建后端 JAR
#   frontend  仅构建前端
#   clean     清理所有构建产物
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# ── 默认配置 ───────────────────────────────────────────────────────────────
ACTION="${1:-all}"
DB_MODE="${DB_MODE:-sqlite}"
TAG="${TAG:-latest}"

# ── 颜色 ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_title() { echo -e "${BOLD}${CYAN}$*${NC}"; }

show_usage() {
    cat <<EOF
用法: ./build.sh <action> [options]

动作:
  all       打包所有目标（默认）
  electron  打包桌面应用（Mac + Windows）
  ios       打包 iOS 应用（需要 macOS + Xcode）
  android   打包 Android 应用（需要 Android Studio）
  docker    打包 Docker 镜像（SQLite 或 MySQL 8）
  jar       仅构建后端 JAR
  frontend  仅构建前端
  clean     清理所有构建产物
  help      查看帮助

环境变量:
  DB_MODE   docker 数据库模式: sqlite (默认) | mysql
  TAG       镜像标签 (默认: latest)
  SKIP_JAR 跳过 JAR 构建: true | false

示例:
  ./build.sh all                           # 打包所有目标
  ./build.sh electron                      # 仅打包桌面应用
  ./build.sh android                       # 仅打包 Android
  DB_MODE=mysql ./build.sh docker          # 打包 Docker (MySQL 8)
  SKIP_JAR=true ./build.sh electron        # 跳过 JAR 构建（后端已构建）
EOF
}

# ── 执行子模块构建 ─────────────────────────────────────────────────────────
run_subbuild() {
    local module="$1"
    shift
    local script="$SCRIPT_DIR/$module/build.sh"

    if [[ ! -f "$script" ]]; then
        log_error "模块构建脚本不存在: $script"
        return 1
    fi

    log_title ""
    log_title "══════════════════════════════════════════════════"
    log_title "  构建模块: ${module^^}"
    log_title "══════════════════════════════════════════════════"
    log_title ""

    # 传递 SKIP_JAR 等环境变量
    SKIP_JAR="${SKIP_JAR:-false}" \
    TAG="$TAG" \
    DB_MODE="$DB_MODE" \
    bash "$script" "$@" || return 1
}

# ── 各目标构建函数 **********************************************************

build_all() {
    log_title ""
    log_title "╔══════════════════════════════════════════════════╗"
    log_title "║     闲鱼管理器 · 全平台打包                      ║"
    log_title "╚══════════════════════════════════════════════════╝"
    log_title ""

    build_jar
    build_electron
    build_ios
    build_android
    build_docker

    show_summary
}

build_jar() {
    log_step "构建后端 JAR..."
    cd "$PROJECT_ROOT" || exit 1

    if [[ ! -f "$PROJECT_ROOT/social-sdk-xianyu-manager/target/social-sdk-xianyu-manager-0.0.1.jar" ]]; then
        mvn clean package -DskipTests -Dskip.frontend=false
    else
        log_info "JAR 已存在，跳过构建"
    fi
}

build_electron() {
    run_subbuild electron build
}

build_ios() {
    if [[ "$(uname)" != "Darwin" ]]; then
        log_warn "iOS 打包需要 macOS，跳过"
        return 0
    fi
    run_subbuild ios build
}

build_android() {
    run_subbuild android build
}

build_docker() {
    run_subbuild docker build
}

build_frontend() {
    log_step "构建前端..."
    cd "$PROJECT_ROOT/social-sdk-xianyu-manager-web" || exit 1
    if [[ ! -d node_modules ]]; then
        npm install --registry=https://registry.npmmirror.com
    fi
    npm run build
}

do_clean() {
    log_step "清理构建产物..."

    # Electron
    rm -rf "$SCRIPT_DIR/electron/dist_electron"
    rm -rf "$SCRIPT_DIR/electron/node_modules/.cache"

    # Docker
    rm -rf "$SCRIPT_DIR/docker/build"
    rm -rf "$SCRIPT_DIR/docker/data"
    rm -rf "$SCRIPT_DIR/docker/chrome-profiles"
    rm -rf "$SCRIPT_DIR/docker/logs"

    # Maven
    cd "$PROJECT_ROOT" && mvn clean -q 2>/dev/null || true

    log_info "清理完成"
}

show_summary() {
    echo ""
    log_title "╔══════════════════════════════════════════════════╗"
    log_title "║     构建完成！产物总结                           ║"
    log_title "╚══════════════════════════════════════════════════╝"
    echo ""

    # Electron
    if [[ -d "$SCRIPT_DIR/electron/dist_electron" ]]; then
        log_info "📦 Electron 桌面应用:"
        find "$SCRIPT_DIR/electron/dist_electron" -maxdepth 1 -type f \( -name "*.dmg" -o -name "*.exe" -o -name "*.zip" \) | while read f; do
            echo "   → $f ($(du -h "$f" | cut -f1))"
        done
    fi

    # Docker
    log_info "🐳 Docker 镜像:"
    docker images --format "   → {{.Repository}}:{{.Tag}} ({{.Size}})" | grep xianyu-manager || echo "   (无)"

    # Android
    if [[ -d "$SCRIPT_DIR/android/build" ]]; then
        log_info "🤖 Android:"
        find "$SCRIPT_DIR/android/build" -maxdepth 1 -type f \( -name "*.apk" -o -name "*.aab" \) | while read f; do
            echo "   → $f ($(du -h "$f" | cut -f1))"
        done
    fi

    echo ""
    log_info "详细使用说明: 参见各模块 README.md"
}

# ── 主流程 ─────────────────────────────────────────────────────────────────
main() {
    case "$ACTION" in
        all)        build_all ;;
        electron)   build_electron ;;
        ios)        build_ios ;;
        android)    build_android ;;
        docker)     build_docker ;;
        jar)        build_jar ;;
        frontend)   build_frontend ;;
        clean)      do_clean ;;
        help|-h|--help) show_usage ;;
        *)
            echo "未知动作: $ACTION"
            show_usage
            exit 1
            ;;
    esac
}

main
