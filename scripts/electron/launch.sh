#!/usr/bin/env bash
# ============================================================================
# launch.sh — Electron 启动后端 JAR 的脚本（跨平台）
# ============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_DIR="$SCRIPT_DIR/app"
JAR_FILE=""

# 查找 JAR 文件
for f in "$JAR_DIR"/*.jar; do
    if [[ -f "$f" ]]; then
        JAR_FILE="$f"
        break
    fi
done

if [[ -z "$JAR_FILE" ]]; then
    echo "ERROR: No JAR file found in $JAR_DIR"
    exit 1
fi

# 创建数据目录
mkdir -p "$SCRIPT_DIR/data"
mkdir -p "$SCRIPT_DIR/data/openlist"
mkdir -p "$SCRIPT_DIR/chrome-profiles"
mkdir -p "$SCRIPT_DIR/logs"

# 启动 Spring Boot
exec java \
    -Xmx512m \
    -Xms256m \
    -Dfile.encoding=UTF-8 \
    -Duser.dir="$SCRIPT_DIR" \
    -jar "$JAR_FILE" \
    "$@"
