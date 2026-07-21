#!/usr/bin/env bash
# ============================================================================
# build.sh — iOS 应用打包（基于 Capacitor）
#
# 工作原理：
#   1. 构建 Vue 前端 → dist/
#   2. 使用 Capacitor 将 dist/ 包装为 iOS 原生工程
#   3. 打开 Xcode 工程，生成 .ipa
#
# 要求: macOS + Xcode + CocoaPods + Node.js 18+
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
  init      初始化 Capacitor 项目 & iOS 工程
  sync      同步前端构建到 iOS 工程
  build     构建前端 + 同步 + 生成 .ipa
  open      打开 Xcode 工程
  clean     清理构建产物

示例:
  ./build.sh init      # 首次运行，初始化工程
  ./build.sh build     # 构建 .ipa
  ./build.sh open      # 在 Xcode 中打开
EOF
}

# ── 前置检查 ───────────────────────────────────────────────────────────────
ACTION="${1:-build}"
FRONTEND_DIR="$PROJECT_ROOT/social-sdk-xianyu-manager-web"
CAPACITOR_DIR="$SCRIPT_DIR/ios-project"
IOS_PLATFORM_DIR="$CAPACITOR_DIR/ios"

preflight_check() {
    log_step "执行前置检查..."

    # 必须在 macOS 上运行
    if [[ "$(uname)" != "Darwin" ]]; then
        log_error "iOS 打包必须在 macOS 上运行（需要 Xcode）"
        exit 1
    fi

    command -v node &>/dev/null || { log_error "Node.js 未安装"; exit 1; }
    command -v npm &>/dev/null || { log_error "npm 未安装"; exit 1; }
    command -v xcodebuild &>/dev/null || { log_error "Xcode 未安装 (xcodebuild not found)"; exit 1; }
    command -v pod &>/dev/null || { log_error "CocoaPods 未安装: sudo gem install cocoapods"; exit 1; }

    log_info "Node.js: $(node --version)"
    log_info "Xcode: $(xcodebuild -version | head -1)"
    log_info "CocoaPods: $(pod --version)"
}

# ── 构建前端 ───────────────────────────────────────────────────────────────
build_frontend() {
    log_step "构建 Vue 前端..."
    cd "$FRONTEND_DIR" || exit 1

    if [[ ! -d node_modules ]]; then
        npm install --registry=https://registry.npmmirror.com
    fi

    # 设置生产环境 API 地址（指向后端服务器）
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
    "name": "xianyu-manager-ios",
    "version": "0.0.1",
    "description": "闲鱼管理器 iOS 版",
    "private": true,
    "scripts": {
        "sync": "cap sync ios",
        "open": "cap open ios",
        "build": "cap sync ios"
    },
    "dependencies": {
        "@capacitor/core": "^5.7.0",
        "@capacitor/ios": "^5.7.0",
        "@capacitor/app": "^5.0.7",
        "@capacitor/browser": "^5.2.0",
        "@capacitor/status-bar": "^5.0.7",
        "@capacitor/splash-screen": "^5.0.7"
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
        // 生产环境：加载远程 URL（注释掉则使用本地文件）
        // url: 'https://your-server.com',
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

    # 添加 iOS 平台
    if [[ ! -d "$IOS_PLATFORM_DIR" ]]; then
        log_step "添加 iOS 平台..."
        npx cap add ios
    else
        log_info "iOS 平台已存在"
    fi

    # 构建前端并同步
    build_frontend
    sync_ios

    log_info "Capacitor iOS 工程初始化完成！"
    log_info "工程路径: $IOS_PLATFORM_DIR"
}

# ── 同步前端到 iOS ──────────────────────────────────────────────────────────
sync_ios() {
    log_step "同步前端构建到 iOS 工程..."

    # 检查 Capacitor 项目是否存在
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

    # 更新 iOS 工程
    if [[ -d "$IOS_PLATFORM_DIR" ]]; then
        npx cap sync ios
    else
        npx cap add ios
    fi

    log_info "iOS 工程同步完成"
}

# ── 构建 .ipa ─────────────────────────────────────────────────────────────
build_ipa() {
    log_step "构建 iOS .ipa..."

    cd "$CAPACITOR_DIR" || exit 1

    if [[ ! -d "$IOS_PLATFORM_DIR" ]]; then
        log_error "iOS 工程不存在，请先运行: ./build.sh init"
        exit 1
    fi

    # 确保 pod 依赖已安装
    cd "$IOS_PLATFORM_DIR/App" || exit 1
    if [[ ! -d Pods ]]; then
        log_step "安装 CocoaPods 依赖..."
        pod install --repo-update
    fi
    cd "$CAPACITOR_DIR" || exit 1

    # 使用 xcodebuild 打包
    local scheme="App"
    local archivePath="$SCRIPT_DIR/build/App.xcarchive"
    local ipaPath="$SCRIPT_DIR/build"

    mkdir -p "$SCRIPT_DIR/build"

    log_step "生成 Archive..."
    cd "$IOS_PLATFORM_DIR/App" || exit 1
    xcodebuild -workspace App.xcworkspace \
        -scheme "$scheme" \
        -configuration Release \
        -destination 'generic/platform=iOS' \
        -archivePath "$archivePath" \
        archive \
        CODE_SIGNING_ALLOWED=NO \
        CODE_SIGN_IDENTITY="" \
        CODE_SIGNING_REQUIRED=NO

    # 导出 IPA（需要有效的签名配置）
    log_step "导出 IPA..."

    # 检查是否有签名证书
    local signingIdentity=$(security find-identity -v -p codesigning | grep "iPhone Distribution" | head -1 | awk -F'"' '{print $2}')

    if [[ -n "$signingIdentity" ]]; then
        cat > "$SCRIPT_DIR/build/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>app-store</string>
    <key>teamID</key>
    <string>YOUR_TEAM_ID</string>
    <key>uploadBitcode</key>
    <false/>
    <key>uploadSymbols</key>
    <true/>
</dict>
</plist>
PLIST
        xcodebuild -exportArchive \
            -archivePath "$archivePath" \
            -exportPath "$ipaPath" \
            -exportOptionsPlist "$SCRIPT_DIR/build/ExportOptions.plist"

        log_info "IPA 已生成: $ipaPath/App.ipa"
    else
        log_warn "未找到有效的代码签名证书"
        log_warn "Archive 已生成: $archivePath"
        log_warn "请在 Xcode 中手动签名和导出："
        log_warn "  1. 运行: ./build.sh open"
        log_warn "  2. 选择 Product → Archive"
        log_warn "  3. Distribute App → App Store Connect / Ad Hoc"
    fi
}

# ── 打开 Xcode ──────────────────────────────────────────────────────────
open_xcode() {
    if [[ -d "$IOS_PLATFORM_DIR/App/App.xcworkspace" ]]; then
        log_step "打开 Xcode..."
        npx cap open ios
    else
        log_error "iOS 工程不存在，请先运行: ./build.sh init"
        exit 1
    fi
}

# ── 清理 ───────────────────────────────────────────────────────────────────
do_clean() {
    log_step "清理 iOS 构建产物..."
    rm -rf "$SCRIPT_DIR/build"
    rm -rf "$CAPACITOR_DIR/dist"
    log_info "清理完成"
}

# ── 主流程 ─────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     闲鱼管理器 · iOS 打包脚本 (Capacitor)        ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""

    case "$ACTION" in
        init)
            preflight_check
            init_capacitor
            ;;
        sync)
            preflight_check
            sync_ios
            ;;
        build)
            preflight_check
            build_frontend
            sync_ios
            build_ipa
            ;;
        open)
            open_xcode
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
