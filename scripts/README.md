# 闲鱼管理器 · 打包脚本

全平台打包方案：Electron 桌面应用、iOS/Android 移动应用、Docker 容器化。

## 快速开始

```bash
# 构建所有平台
./scripts/build.sh all

# 或按需构建
./scripts/build.sh electron    # Mac + Windows 桌面应用
./scripts/build.sh ios         # iOS 应用（需要 macOS + Xcode）
./scripts/build.sh android     # Android 应用
./scripts/build.sh docker      # Docker 镜像（默认 SQLite）
DB_MODE=mysql ./scripts/build.sh docker   # Docker + MySQL 8
```

## 目录结构

```
scripts/
├── build.sh            # Unix 统一构建入口
├── build.bat           # Windows 统一构建入口
├── common/             # 共享工具函数
│   ├── common.sh       # shell 版
│   └── common.bat      # Windows 版
├── electron/           # Electron 桌面应用（Mac + Windows）
├── ios/                # iOS 应用（Capacitor）
├── android/            # Android 应用（Capacitor）
└── docker/             # Docker 容器化（SQLite / MySQL 8）
```

## 支持的打包目标

| 目标 | 平台 | 产物 | 默认数据库 |
|------|------|------|-----------|
| Electron | Mac / Windows | dmg, zip, exe, portable | SQLite |
| iOS | iOS 15+ | .ipa | 无（需独立后端） |
| Android | Android 8+ | .apk, .aab | 无（需独立后端） |
| Docker | Linux / Mac / Windows | docker image | SQLite 或 MySQL 8 |

## 详细文档

- [Electron 桌面应用](scripts/electron/README.md)
- [iOS 应用](scripts/ios/README.md)
- [Android 应用](scripts/android/README.md)
- [Docker 容器化](scripts/docker/README.md)

## 前置要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 后端构建 |
| Maven | 3.6+ | 后端构建 |
| Node.js | 18+ | 前端 + Electron |
| Xcode | 15+ | iOS 打包（仅 macOS） |
| Android Studio | Hedgehog+ | Android 打包 |
| Docker | 20.10+ | 容器化 |
