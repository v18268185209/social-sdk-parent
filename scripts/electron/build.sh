#!/usr/bin/env bash
# ============================================================================
# build.sh — Electron 打包入口（支持 macOS / Windows）
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../common/common.sh"

# ── 参数解析 ───────────────────────────────────────────────────────────────
PLATFORM="${1:-}"
ACTION="${2:-build}"
SKIP_JAR="${SKIP_JAR:-false}"

show_usage() {
    cat <<EOF
用法: build.sh <platform> [action]

平台:
  mac      打包 macOS 版（dmg + zip）
  win      打包 Windows 版（nsis + portable）
  all      同时打包两个平台

动作:
  build   打包 Electron 应用（默认）
  dev     以开发模式启动 Electron
  clean   清理构建产物

示例:
  ./build.sh mac          # 构建 macOS 版
  ./build.sh win          # 构建 Windows 版
  SKIP_JAR=true ./build.sh mac   # 跳过 JAR 构建
EOF
}

# ── 前置检查 ───────────────────────────────────────────────────────────────
preflight_check() {
    log_step "执行前置检查..."
    require_java
    require_maven
    require_node

    if [[ ! -d "$SCRIPT_DIR/node_modules" ]]; then
        log_step "安装 Electron 依赖..."
        cd "$SCRIPT_DIR" && npm install
    fi

    if [[ ! -f "$JAR_PATH" && "$SKIP_JAR" != "true" ]]; then
        build_jar || return 1
    elif [[ "$SKIP_JAR" == "true" ]]; then
        if [[ ! -f "$JAR_PATH" ]]; then
            log_error "未找到 JAR 文件且设置了 SKIP_JAR=true: $JAR_PATH"
            return 1
        fi
        log_info "跳过 JAR 构建，使用现有: $JAR_PATH"
    fi
}

# ── 运行 Electron 打包 ─────────────────────────────────────────────────────
run_electron_builder() {
    local platform="$1"
    local arch="${2:-}"

    log_step "开始 Electron 打包 ($platform)..."

    cd "$SCRIPT_DIR"

    local eb_args=()
    case "$platform" in
        mac)
            eb_args+=("--mac")
            [[ -n "$arch" ]] && eb_args+=("--$arch")
            ;;
        win)
            eb_args+=("--win")
            [[ -n "$arch" ]] && eb_args+=("--$arch")
            ;;
        *)
            log_error "未知平台: $platform"
            return 1
            ;;
    esac

    log_debug "electron-builder 参数: ${eb_args[*]}"
    npx electron-builder "${eb_args[@]}" || return 1

    log_info "Electron 打包完成！"
    log_info "输出目录: $SCRIPT_DIR/dist_electron"
}

# ── 开发模式 ───────────────────────────────────────────────────────────────
run_dev() {
    log_step "启动 Electron 开发模式..."
    cd "$SCRIPT_DIR"
    # 开发模式下直接启动 Electron，不打包
    export ELECTRON_DEV=1
    npx electron .
}

# ── 清理 ───────────────────────────────────────────────────────────────────
do_clean() {
    log_step "清理 Electron 构建产物..."
    rm -rf "$SCRIPT_DIR/dist_electron"
    rm -rf "$SCRIPT_DIR/node_modules/.cache"
    log_info "清理完成"
}

# ── 主流程 ─────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     闲鱼管理器 · Electron 打包脚本               ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""

    case "$ACTION" in
        build)
            preflight_check || exit 1
            case "$PLATFORM" in
                mac)  run_electron_builder "mac" ;;
                win)  run_electron_builder "win" ;;
                all)
                    run_electron_builder "mac" || exit 1
                    run_electron_builder "win" || exit 1
                    ;;
                *)
                    log_error "请指定平台: mac | win | all"
                    show_usage
                    exit 1
                    ;;
            esac
            ;;
        dev)
            preflight_check || exit 1
            run_dev
            ;;
        clean)
            do_clean
            ;;
        *)
            show_usage
            exit 1
            ;;
    esac
}

main
