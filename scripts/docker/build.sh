#!/usr/bin/env bash
# ============================================================================
# build.sh — Docker 镜像构建 & 推送
#
# 支持三种数据库模式：
#   sqlite (默认) — 单容器，使用内嵌 SQLite
#   mysql         — 双容器（应用 + MySQL 8），支持自定义配置
#   postgres      — 双容器（应用 + PostgreSQL），支持自定义配置
#
# 支持自定义配置：通过 docker-compose.override.yml 或环境变量文件
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

# ── 默认配置 ───────────────────────────────────────────────────────────────
DB_MODE="${DB_MODE:-sqlite}"
TAG="${TAG:-latest}"
PUSH="${PUSH:-false}"
REGISTRY="${REGISTRY:-}"
COMPOSE_ACTION="${COMPOSE_ACTION:-up}"

show_usage() {
    cat <<EOF
用法: build.sh [options] [action]

数据库模式（环境变量）:
  DB_MODE=sqlite    使用 SQLite（单容器，默认）
  DB_MODE=mysql     使用 MySQL 8（双容器）
  DB_MODE=postgres  使用 PostgreSQL（双容器）

动作:
  build     构建 Docker 镜像
  compose   使用 docker-compose 启动
  push      构建并推送到仓库
  clean     清理本地镜像和生成物
  all       构建镜像 + 启动

选项:
  TAG=版本号        镜像标签 (默认: latest)
  REGISTRY=仓库     镜像仓库地址 (如 registry.example.com/xianyu)
  PUSH=true         推送镜像
  COMPOSE_ACTION=   compose 动作 (up/down/build)

示例:
  ./build.sh build                          # 构建 SQLite 版
  DB_MODE=mysql ./build.sh build            # 构建 MySQL 版
  DB_MODE=postgres ./build.sh build         # 构建 PostgreSQL 版
  DB_MODE=mysql ./build.sh compose          # 使用 MySQL 启动
  DB_MODE=postgres ./build.sh compose       # 使用 PostgreSQL 启动
  DB_MODE=mysql COMPOSE_ACTION=up ./build.sh all
  TAG=v1.0.0 REGISTRY=registry.example.com/xianyu ./build.sh push
EOF
}

# ── 解析参数 ───────────────────────────────────────────────────────────────
ACTION="${1:-build}"

preflight_check() {
    log_step "执行前置检查..."
    command -v docker &>/dev/null || { log_error "Docker 未安装"; exit 1; }
    command -v docker-compose &>/dev/null || docker compose version &>/dev/null || { log_error "Docker Compose 未安装"; exit 1; }
    log_info "Docker: $(docker --version)"
    log_info "数据库模式: $DB_MODE"
}

# ── 优先构建 JAR ────────────────────────────────────────────────────────────
build_jar_if_needed() {
    local jar_path="$PROJECT_ROOT/social-sdk-xianyu-manager/target/social-sdk-xianyu-manager-0.0.1.jar"

    if [[ ! -f "$jar_path" ]]; then
        log_step "JAR 文件不存在，开始构建..."
        cd "$PROJECT_ROOT" || exit 1
        mvn clean package -DskipTests -Dskip.frontend=false || exit 1
    else
        log_info "JAR 文件已存在: $jar_path"
    fi
}

# ── 构建镜像 ───────────────────────────────────────────────────────────────
build_image() {
    local image_name="xianyu-manager"
    local image_tag="${image_name}:${TAG}"

    if [[ -n "$REGISTRY" ]]; then
        image_tag="${REGISTRY}/${image_tag}"
    fi

    log_step "构建 Docker 镜像: $image_tag (DB_MODE=$DB_MODE)"

    # 构建参数
    local build_args=(
        "--build-arg" "DB_MODE=$DB_MODE"
        "--build-arg" "JAR_PATH=social-sdk-xianyu-manager/target/social-sdk-xianyu-manager-0.0.1.jar"
        "-t" "$image_tag"
        "-f" "$SCRIPT_DIR/Dockerfile"
    )

    # 根据数据库模式添加不同的标签
    build_args+=("-t" "${image_name}:${DB_MODE}-${TAG}")
    [[ -n "$REGISTRY" ]] && build_args+=("-t" "${REGISTRY}/${image_name}:${DB_MODE}-${TAG}")

    cd "$PROJECT_ROOT"
    docker build "${build_args[@]}" || exit 1

    log_info "镜像构建成功: $image_tag"
    log_info "镜像大小: $(docker image inspect --format='{{.Size}}' "$image_tag" | awk '{printf "%.1f MB", $1/1024/1024}')"
}

# ── 推送镜像 ───────────────────────────────────────────────────────────────
push_image() {
    build_image

    if [[ -z "$REGISTRY" ]]; then
        log_error "推送需要指定 REGISTRY 环境变量"
        log_info "示例: REGISTRY=registry.example.com/xianyu ./build.sh push"
        exit 1
    fi

    local image_tag="${REGISTRY}/xianyu-manager:${TAG}"

    log_step "推送镜像到仓库: $image_tag"
    docker push "$image_tag"

    docker push "${REGISTRY}/xianyu-manager:${DB_MODE}-${TAG}"

    log_info "镜像推送成功"
}

# ── Compose 启动 ───────────────────────────────────────────────────────────
compose_up() {
    log_step "启动 Docker Compose (DB_MODE=$DB_MODE)..."

    cd "$SCRIPT_DIR" || exit 1

    local compose_file="docker-compose.yml"
    local override_file=""
    if [[ "$DB_MODE" == "mysql" ]]; then
        override_file="docker-compose.mysql.yml"
    elif [[ "$DB_MODE" == "postgres" ]]; then
        override_file="docker-compose.postgres.yml"
    fi

    # 构建镜像先
    build_image

    # 启动。docker-compose.build.yml 必须最后叠加，用本地源码构建配置覆盖 ACR 镜像配置。
    if [[ -n "$override_file" && -f "$override_file" ]]; then
        DB_MODE="$DB_MODE" TAG="$TAG" docker compose -f "$compose_file" -f "$override_file" -f docker-compose.build.yml up -d --build
    else
        DB_MODE="$DB_MODE" TAG="$TAG" docker compose -f "$compose_file" -f docker-compose.build.yml up -d --build
    fi

    log_info "服务已启动"
    show_status
}

show_status() {
    echo ""
    log_step "服务状态:"
    echo ""
    docker compose -f "$SCRIPT_DIR/docker-compose.yml" ps 2>/dev/null || true

    echo ""
    log_info "访问地址:"
    log_info "  Web UI:    http://localhost:8080"
    if [[ "$DB_MODE" == "mysql" ]]; then
        log_info "  MySQL:     localhost:${MYSQL_PORT:-3306}"
        log_info "  MySQL 用户: xianyu (密码见 docker-compose.mysql.yml)"
    elif [[ "$DB_MODE" == "postgres" ]]; then
        log_info "  PostgreSQL: localhost:${POSTGRES_PORT:-5432}"
        log_info "  PostgreSQL 用户: xianyu (密码见 docker-compose.postgres.yml)"
    else
        log_info "  SQLite DB: /app/data/xianyu-manager.db"
    fi
    echo ""
    log_info "查看日志: docker compose -f scripts/docker/docker-compose.yml logs -f"
}

# ── 清理 ───────────────────────────────────────────────────────────────────
do_clean() {
    log_step "清理 Docker 构建产物..."

    cd "$SCRIPT_DIR" || exit 1

    # 停止 compose
    docker compose -f docker-compose.yml down 2>/dev/null || true
    docker compose -f docker-compose.mysql.yml down 2>/dev/null || true

    # 删除镜像
    docker rmi -f xianyu-manager:latest 2>/dev/null || true
    docker rmi -f xianyu-manager:sqlite-latest 2>/dev/null || true
    docker rmi -f xianyu-manager:mysql-latest 2>/dev/null || true

    # 删除构建缓存
    docker builder prune -f 2>/dev/null || true

    log_info "清理完成"
}

# ── 主流程 ─────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     闲鱼管理器 · Docker 打包脚本                  ║${NC}"
    echo -e "${CYAN}║     数据库模式: ${DB_MODE^^}$(printf '%*s' $((12 - ${#DB_MODE})) '')${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""

    case "$ACTION" in
        build)
            preflight_check
            build_image
            ;;
        push)
            preflight_check
            push_image
            ;;
        compose)
            preflight_check
            compose_up
            ;;
        all)
            preflight_check
            compose_up
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
