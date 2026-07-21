#!/usr/bin/env bash
# ============================================================================
# common.sh — 所有打包脚本共享的工具函数与常量
# ============================================================================

# 脚本所在目录 → scripts/ → 项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$SCRIPTS_DIR")"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }
log_debug() { [[ "${DEBUG:-0}" == "1" ]] && echo -e "${BLUE}[DEBUG]${NC} $*" || true; }

# ── 项目元信息 ──────────────────────────────────────────────────────────────
PROJECT_VERSION="0.0.1"
PROJECT_GROUP="cn.net.rjnetwork"
PROJECT_NAME="social-sdk-xianyu-manager"
JAR_NAME="${PROJECT_NAME}-${PROJECT_VERSION}.jar"
JAR_PATH="${PROJECT_ROOT}/${PROJECT_NAME}/target/${JAR_NAME}"
FRONTEND_DIR="${PROJECT_ROOT}/${PROJECT_NAME}-web"
FRONTEND_DIST="${FRONTEND_DIR}/dist"

# ── 工具检查 ───────────────────────────────────────────────────────────────
require_tool() {
    local tool="$1"
    local hint="${2:-请安装 $tool}"
    if ! command -v "$tool" &>/dev/null; then
        log_error "$tool 未找到，$hint"
        return 1
    fi
}

require_java() {
    local java_version
    java_version=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d'.' -f1)
    if [[ "$java_version" -lt 17 ]]; then
        log_error "需要 Java 17+，当前版本: $(java -version 2>&1 | head -1)"
        return 1
    fi
    log_info "Java 版本: $(java -version 2>&1 | head -1)"
}

require_maven() {
    require_tool maven "请安装 Maven 3.6+ (brew install maven / scoop install maven)"
    log_info "Maven 版本: $(mvn --version | head -1)"
}

# ── 构建后端 JAR ────────────────────────────────────────────────────────────
build_jar() {
    local skip_frontend="${1:-false}"
    local mvn_args=("clean" "package" "-DskipTests")

    if [[ "$skip_frontend" == "true" ]]; then
        mvn_args+=("-Dskip.frontend=true")
        log_info "跳过前端构建"
    else
        log_info "包含前端构建"
    fi

    log_step "正在构建后端 JAR（含依赖）..."
    cd "$PROJECT_ROOT" || return 1
    mvn "${mvn_args[@]}" || return 1

    if [[ ! -f "$JAR_PATH" ]]; then
        log_error "JAR 文件未生成: $JAR_PATH"
        return 1
    fi
    log_info "JAR 构建成功: $JAR_PATH ($(du -h "$JAR_PATH" | cut -f1))"
}

# ── 构建前端 ───────────────────────────────────────────────────────────────
build_frontend() {
    require_node

    log_step "正在构建前端..."
    cd "$FRONTEND_DIR" || return 1

    if [[ ! -d node_modules ]]; then
        npm install --registry=https://registry.npmmirror.com
    fi

    npm run build || return 1

    if [[ ! -d "$FRONTEND_DIST" ]]; then
        log_error "前端构建产物目录不存在: $FRONTEND_DIST"
        return 1
    fi
    log_info "前端构建成功: $FRONTEND_DIST"
}

require_node() {
    require_tool node "请安装 Node.js 18+ (brew install node / volta install node)"
    local node_version
    node_version=$(node --version | cut -d'v' -f2 | cut -d'.' -f1)
    if [[ "$node_version" -lt 18 ]]; then
        log_error "需要 Node.js 18+，当前版本: $(node --version)"
        return 1
    fi
    log_info "Node.js 版本: $(node --version)"
}

# ── 创建标准数据目录 ────────────────────────────────────────────────────────
ensure_data_dirs() {
    local target_dir="$1"
    mkdir -p "$target_dir/data"
    mkdir -p "$target_dir/data/openlist"
    mkdir -p "$target_dir/chrome-profiles"
    mkdir -p "$target_dir/logs"
    mkdir -p "$target_dir/config"
    log_info "数据目录已准备: $target_dir"
}

# ── 复制 JAR 到目标位置 ────────────────────────────────────────────────────
copy_jar_to() {
    local target_dir="$1"
    log_step "复制 JAR 到 $target_dir ..."
    cp "$JAR_PATH" "$target_dir/server.jar" || return 1
    log_info "JAR 已复制为 server.jar"
}

# ── 写入标准 .env / application.env ────────────────────────────────────────
write_app_env() {
    local env_file="$1"
    local db_path="${2:-./data/xianyu-manager.db}"
    local extra="${3:-}"

    log_step "写入应用配置: $env_file"
    cat > "$env_file" <<ENVEOF
# ── 闲鱼管理器 · 应用配置 ──────────────────────────────────────────────────
# 自动生成，可手动修改

# 服务器端口
SERVER_PORT=8080

# 数据库配置（SQLite）
DB_PATH=${db_path}

# JWT 密钥（生产环境必须修改！）
XIANYU_JWT_SECRET=${JWT_SECRET:-$(openssl rand -base64 48 2>/dev/null || head -c 64 /dev/urandom | base64)}

# Cookie 加密密钥
XIANYU_CRYPTO_SECRET=${CRYPTO_SECRET:-$(openssl rand -base64 32 2>/dev/null || head -c 48 /dev/urandom | base64)}

# 日志级别
LOG_LEVEL=INFO

# 时区
TZ=Asia/Shanghai

${extra}
ENVEOF
    log_info "配置已写入"
}

# ── 写入说明文件 ─────────────────────────────────────────────────────────────
write_readme() {
    local target_dir="$1"
    local platform="$2"

    log_step "写入说明文件..."
    cat > "$target_dir/README.md" <<READMEEOF
# 闲鱼管理器 · ${platform} 版

## 目录结构

\`\`\`
.
├── server.jar        # 后端 JAR（含前端静态资源）
├── config/           # 配置目录
│   └── application.env
├── data/             # 数据库 & 上传文件
│   └── xianyu-manager.db  （首次启动自动创建）
├── chrome-profiles/  # Chrome 容器隔离配置
└── logs/             # 日志目录
\`\`\`

## 运行

\`\`\`bash
java -jar server.jar
\`\`\`

打开浏览器访问: <http://localhost:8080>

## 默认账号

- 用户名: \`admin\`
- 密码: \`admin123\`

> ⚠️ 首次登录后请立即修改默认密码！

## 数据备份

只需备份 \`data/xianyu-manager.db\` 文件即可。

READMEEOF
}
