#!/usr/bin/env bash
# ============================================================================
# build.sh — Android 应用打包（基于 Capacitor）
#
# 工作原理：
#   1. 构建 Vue 前端 → dist/
#   2. 使用 Capacitor 将 dist/ 包装为 Android 原生工程
#   3. 使用 Gradle 构建 APK / AAB
#
# 要求: Android Studio / JDK 17+ + Node.js 18+
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# ── 颜色 ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

show_usage() {
    cat <<EOF
用法: build.sh [action]

动作:
  init      初始化 Capacitor 项目 & Android 工程
  sync      同步前端构建到 Android 工程
  build     构建前端 + 同步 + 生成 APK
  bundle    生成 AAB（Google Play 上架用）
  open      打开 Android Studio
  clean     清理构建产物

示例:
  ./build.sh init      # 首次运行，初始化工程
  ./build.sh build     # 构建 APK
  ./build.sh bundle    # 生成 AAB
  ./build.sh open      # 在 Android Studio 中打开
EOF
}

# ── 解析参数 ───────────────────────────────────────────────────────────────
ACTION="${1:-build}"
FRONTEND_DIR="$PROJECT_ROOT/social-sdk-xianyu-manager-web"
CAPACITOR_DIR="$SCRIPT_DIR/android-project"
ANDROID_PLATFORM_DIR="$CAPACITOR_DIR/android"

preflight_check() {
    log_step "执行前置检查..."

    command -v node &>/dev/null || { log_error "Node.js 未安装"; exit 1; }
    command -v npm &>/dev/null || { log_error "npm 未安装"; exit 1; }

    # 检查 Java
    if ! command -v java &>/dev/null; then
        log_error "Java 未安装，需要 JDK 17+"
        exit 1
    fi

    local java_version
    java_version=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d'.' -f1)
    if [[ "$java_version" -lt 17 ]]; then
        log_error "需要 Java 17+，当前版本: $(java -version 2>&1 | head -1)"
        exit 1
    fi

    # 检查 ANDROID_HOME
    if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
        log_warn "ANDROID_HOME 或 ANDROID_SDK_ROOT 未设置"
        log_warn "请设置: export ANDROID_HOME=\$Android/Sdk"
        # 不退出，因为环境变量可能是通过其他方式配置的
    fi

    log_info "Node.js: $(node --version)"
    log_info "Java: $(java -version 2>&1 | head -1)"
    [[ -n "${ANDROID_HOME:-}" ]] && log_info "ANDROID_HOME: $ANDROID_HOME"
    [[ -n "${ANDROID_SDK_ROOT:-}" ]] && log_info "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
}

# ── 构建前端 ───────────────────────────────────────────────────────────────
build_frontend() {
    log_step "构建 Vue 前端..."
    cd "$FRONTEND_DIR" || exit 1

    if [[ ! -d node_modules ]]; then
        npm install --registry=https://registry.npmmirror.com
    fi

    # 设置生产环境 API 地址
    export VITE_API_BASE="${XIANYU_API_BASE:-http://localhost:8080}"
    npm run build
    log_info "前端构建完成: $(du -sh dist | cut -f1)"
}

# ── 初始化 Capacitor ────────────────────────────────────────────────────────
init_capacitor() {
    log_step "初始化 Capacitor 项目..."

    mkdir -p "$CAPACITOR_DIR"
    cd "$CAPACITOR_DIR" || exit 1

    # 初始化 package.json
    if [[ ! -f package.json ]]; then
        cat > package.json <<'PKGJSON'
{
    "name": "xianyu-manager-android",
    "version": "0.0.1",
    "description": "闲鱼管理器 Android 版",
    "private": true,
    "scripts": {
        "sync": "cap sync android",
        "open": "cap open android",
        "build": "cap sync android"
    },
    "dependencies": {
        "@capacitor/core": "^5.7.0",
        "@capacitor/android": "^5.7.0",
        "@capacitor/app": "^5.0.7",
        "@capacitor/browser": "^5.2.0",
        "@capacitor/status-bar": "^5.0.7",
        "@capacitor/splash-screen": "^5.0.7",
        "@capacitor-community/http": "^1.4.1"
    },
    "devDependencies": {
        "@capacitor/cli": "^5.7.0"
    }
}
PKGJSON
        npm install
    fi

    # 初始化 Capacitor 配置
    if [[ ! -f capacitor.config.ts ]]; then
        cat > capacitor.config.ts <<CAPEOF
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'cn.net.rjnetwork.xianyu.manager',
    appName: '闲鱼管理器',
    webDir: 'dist',
    server: {
        // 生产环境：加载远程 URL
        // url: 'https://your-server.com:8080',
        // cleartext: true
    },
    plugins: {
        SplashScreen: {
            launchShowDuration: 2000,
            backgroundColor: "#667eea",
            showSpinner: true,
            spinnerColor: "#ffffff"
        },
        StatusBar: {
            style: 'dark'
        }
    }
};

export default config;
CAPEOF
    fi

    # 添加 Android 平台
    if [[ ! -d "$ANDROID_PLATFORM_DIR" ]]; then
        log_step "添加 Android 平台..."
        npx cap add android
    else
        log_info "Android 平台已存在"
    fi

    # 构建前端并同步
    build_frontend
    sync_android

    log_info "Capacitor Android 工程初始化完成！"
    log_info "工程路径: $ANDROID_PLATFORM_DIR"
}

# ── 同步前端到 Android ─────────────────────────────────────────────────────
sync_android() {
    log_step "同步前端构建到 Android 工程..."

    if [[ ! -d "$CAPACITOR_DIR" ]]; then
        log_error "Capacitor 项目未初始化，请先运行: ./build.sh init"
        exit 1
    fi

    cd "$CAPACITOR_DIR" || exit 1

    # 将 dist 复制到 Capacitor webDir
    if [[ -d "$FRONTEND_DIR/dist" ]]; then
        rm -rf dist
        cp -R "$FRONTEND_DIR/dist" ./dist
        log_info "dist 已同步"
    else
        log_warn "前端 dist 不存在，尝试构建..."
        build_frontend
        rm -rf dist
        cp -R "$FRONTEND_DIR/dist" ./dist
    fi

    # 更新 Android 工程
    if [[ -d "$ANDROID_PLATFORM_DIR" ]]; then
        npx cap sync android
    else
        npx cap add android
    fi

    log_info "Android 工程同步完成"
}

# ── 构建 APK ─────────────────────────────────────────────────────────────
build_apk() {
    log_step "构建 Android APK..."

    cd "$ANDROID_PLATFORM_DIR" || exit 1

    # 构建 debug APK（无需签名配置，自动使用 debug keystore）
    log_step "构建 Debug APK..."
    ./gradlew assembleDebug

    local apkPath="$ANDROID_PLATFORM_DIR/app/build/outputs/apk/debug/app-debug.apk"
    if [[ -f "$apkPath" ]]; then
        mkdir -p "$SCRIPT_DIR/build"
        cp "$apkPath" "$SCRIPT_DIR/build/闲鱼管理器-Android-debug.apk"
        log_info "Debug APK 已生成: $SCRIPT_DIR/build/闲鱼管理器-Android-debug.apk"
        log_info "APK 大小: $(du -h "$apkPath" | cut -f1)"
    else
        log_error "APK 构建失败，请检查 Gradle 输出"
        exit 1
    fi

    # 尝试构建 release（如果有签名配置）
    if [[ -f "$SCRIPT_DIR/keystore.properties" ]]; then
        log_step "构建 Release APK..."
        ./gradlew assembleRelease

        local releaseApk="$ANDROID_PLATFORM_DIR/app/build/outputs/apk/release/app-release.apk"
        if [[ -f "$releaseApk" ]]; then
            cp "$releaseApk" "$SCRIPT_DIR/build/闲鱼管理器-Android-release.apk"
            log_info "Release APK 已生成"
        fi
    else
        log_info "未提供签名配置 (keystore.properties)，跳过 Release 构建"
        log_info "生产环境请配置 keystore 后重新运行"
    fi
}

# ── 构建 AAB（Android App Bundle）─────────────────────────────────────────
build_bundle() {
    log_step "构建 Android App Bundle (AAB)..."

    cd "$ANDROID_PLATFORM_DIR" || exit 1

    # AAB 需要签名配置
    if [[ ! -f "$SCRIPT_DIR/keystore.properties" ]]; then
        log_error "AAB 需要签名配置: $SCRIPT_DIR/keystore.properties"
        log_info "请参考 scripts/android/keystore.example.properties 创建"
        exit 1
    fi

    ./gradlew bundleRelease

    local aabPath="$ANDROID_PLATFORM_DIR/app/build/outputs/bundle/release/app-release.aab"
    if [[ -f "$aabPath" ]]; then
        mkdir -p "$SCRIPT_DIR/build"
        cp "$aabPath" "$SCRIPT_DIR/build/闲鱼管理器-Android-release.aab"
        log_info "AAB 已生成: $SCRIPT_DIR/build/闲鱼管理器-Android-release.aab"
        log_info "AAB 大小: $(du -h "$aabPath" | cut -f1)"
    else
        log_error "AAB 构建失败"
        exit 1
    fi
}

# ── 打开 Android Studio ────────────────────────────────────────────────────
open_studio() {
    if [[ -d "$ANDROID_PLATFORM_DIR" ]]; then
        log_step "打开 Android Studio..."
        npx cap open android
    else
        log_error "Android 工程不存在，请先运行: ./build.sh init"
        exit 1
    fi
}

# ── 清理 ───────────────────────────────────────────────────────────────────
do_clean() {
    log_step "清理 Android 构建产物..."
    rm -rf "$SCRIPT_DIR/build"
    rm -rf "$CAPACITOR_DIR/dist"
    if [[ -d "$ANDROID_PLATFORM_DIR" ]]; then
        cd "$ANDROID_PLATFORM_DIR" && ./gradlew clean
    fi
    log_info "清理完成"
}

# ── 主流程 ─────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     闲鱼管理器 · Android 打包脚本 (Capacitor)   ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""

    case "$ACTION" in
        init)
            preflight_check
            init_capacitor
            ;;
        sync)
            preflight_check
            sync_android
            ;;
        build)
            preflight_check
            build_frontend
            sync_android
            build_apk
            ;;
        bundle)
            preflight_check
            build_frontend
            sync_android
            build_bundle
            ;;
        open)
            open_studio
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
