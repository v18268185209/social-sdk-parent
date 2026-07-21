#!/usr/bin/env bash
# ============================================================================
# generate-icons.sh — 从 SVG 源生成 Electron 所需的图标文件
#
# 源文件: icons/icon.svg
# 产物:
#   icon.icns  (macOS 应用图标)
#   icon.ico   (Windows 应用图标)
#   icon.png   (Linux / 通用 512x512)
#   tray.png   (系统托盘，22x22)
#
# 要求:
#   macOS:  iconutil (Xcode CLI), rsvg-convert (brew install librsvg)
#   Windows: png2ico (choco install png2ico) 或在线转换
#   Linux:   rsvg-convert (apt install librsvg2-bin), convert (imagemagick)
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICON_DIR="$SCRIPT_DIR"
SVG_SRC="$ICON_DIR/icon.svg"

GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── 检查源文件 ───────────────────────────────────────────────────────────────
if [[ ! -f "$SVG_SRC" ]]; then
    log_warn "未找到 SVG 源文件: $SVG_SRC"
    log_info "正在生成默认 SVG 图标..."
    generate_default_svg
fi

# ── 生成默认 SVG ─────────────────────────────────────────────────────────────
generate_default_svg() {
    cat > "$SVG_SRC" <<'SVGEOF'
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#667eea"/>
      <stop offset="100%" style="stop-color:#764ba2"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="96" fill="url(#bg)"/>
  <text x="256" y="320" font-size="280" text-anchor="middle" fill="#fff" font-family="Arial,sans-serif">🐟</text>
</svg>
SVGEOF
    log_info "默认 SVG 图标已生成: $SVG_SRC"
}

# ── 生成 PNG ────────────────────────────────────────────────────────────────
generate_pngs() {
    local sizes=(256 512)

    if command -v rsvg-convert &>/dev/null; then
        for size in "${sizes[@]}"; do
            rsvg-convert -w "$size" -h "$size" "$SVG_SRC" -o "$ICON_DIR/icon-${size}.png"
            log_info "生成 icon-${size}.png"
        done
        # 托盘图标
        rsvg-convert -w 22 -h 22 "$SVG_SRC" -o "$ICON_DIR/tray.png"
        log_info "生成 tray.png"
        # 标准 icon.png
        rsvg-convert -w 512 -h 512 "$SVG_SRC" -o "$ICON_DIR/icon.png"
    elif command -v convert &>/dev/null; then
        for size in "${sizes[@]}"; do
            convert -background none -resize "${size}x${size}" "$SVG_SRC" "$ICON_DIR/icon-${size}.png"
            log_info "生成 icon-${size}.png"
        done
        convert -background none -resize "22x22" "$SVG_SRC" "$ICON_DIR/tray.png"
        convert -background none -resize "512x512" "$SVG_SRC" "$ICON_DIR/icon.png"
    elif command -v sips &>/dev/null; then
        # macOS sips 不支持 SVG，跳过
        log_warn "sips 不支持 SVG 转换，请用 rsvg-convert"
        return 1
    else
        log_error "未找到 SVG 转换工具 (rsvg-convert / convert)"
        log_info "  macOS:  brew install librsvg"
        log_info "  Linux:  apt install librsvg2-bin"
        return 1
    fi
}

# ── 生成 ICNS (macOS) ─────────────────────────────────────────────────────
generate_icns() {
    local iconset="$ICON_DIR/icon.iconset"
    mkdir -p "$iconset"

    # 生成所有尺寸
    local sizes=(16 32 64 128 256 512 1024)
    for size in "${sizes[@]}"; do
        local half=$((size / 2))
        if command -v rsvg-convert &>/dev/null; then
            rsvg-convert -w "$size" -h "$size" "$SVG_SRC" -o "$iconset/icon_${size}x${size}.png"
            if [[ $half -ge 16 ]]; then
                rsvg-convert -w "$half" -h "$half" "$SVG_SRC" -o "$iconset/icon_${size}x${size}@2x.png"
            fi
        elif command -v convert &>/dev/null; then
            convert -background none -resize "${size}x${size}" "$SVG_SRC" "$iconset/icon_${size}x${size}.png"
            if [[ $half -ge 16 ]]; then
                convert -background none -resize "${half}x${half}" "$SVG_SRC" "$iconset/icon_${size}x${size}@2x.png"
            fi
        fi
    done

    if command -v iconutil &>/dev/null; then
        iconutil -c icns "$iconset" -o "$ICON_DIR/icon.icns"
        log_info "生成 icon.icns"
    else
        log_warn "iconutil 不可用（仅 macOS Xcode 提供），跳过 .icns"
        log_info "可在 macOS 上手动执行: iconutil -c icns icon.iconset -o icon.icns"
    fi

    # 清理
    rm -rf "$iconset"
}

# ── 生成 ICO (Windows) ────────────────────────────────────────────────────
generate_ico() {
    if command -v png2ico &>/dev/null; then
        png2ico "$ICON_DIR/icon.ico" "$ICON_DIR/icon-256.png" "$ICON_DIR/icon-128.png" "$ICON_DIR/icon-64.png" "$ICON_DIR/icon-32.png"
        log_info "生成 icon.ico"
    elif command -v convert &>/dev/null; then
        convert "$ICON_DIR/icon-256.png" "$ICON_DIR/icon-128.png" "$ICON_DIR/icon-64.png" "$ICON_DIR/icon-32.png" "$ICON_DIR/icon.ico"
        log_info "生成 icon.ico"
    else
        log_warn "未找到 png2ico / convert，跳过 .ico"
        log_info "  Windows: png2ico icon.ico icon-256.png icon-128.png icon-64.png icon-32.png"
        log_info "  或使用在线转换: https://icoconvert.com/"
    fi
}

# ── 主流程 ─────────────────────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     Electron · 图标生成                         ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
    echo ""

    generate_pngs
    generate_icns
    generate_ico

    echo ""
    log_info "图标生成完成！产物列表:"
    ls -lh "$ICON_DIR"/icon.* "$ICON_DIR"/tray.* 2>/dev/null | awk '{print "  →", $NF, "(" $5 ")"}'
}

main
