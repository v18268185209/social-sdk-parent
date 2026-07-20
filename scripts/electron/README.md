# 闲鱼管理器 · Electron 桌面版

基于 Electron 28 的跨平台桌面应用，自动管理后端 Spring Boot 进程。

## 功能特性

- 🚀 **自动管理后端** — 启动时自动拉起 Spring Boot JAR，关闭时清理进程
- 📌 **系统托盘** — 最小化到托盘，双击打开主界面
- 🌐 **内嵌 WebView** — 直接在内嵌浏览器中显示 Vue 前端
- 🔄 **自动重连** — 后端就绪后自动加载 Web UI
- 📝 **日志管理** — 内置日志查看，一键打开日志目录
- ⚙️ **设置面板** — 端口、Java 路径、自启等可视化配置

## 目录结构

```
scripts/electron/
├── build.sh              # 打包入口脚本
├── package.json          # Electron 依赖 & 打包配置
├── main.js               # Electron 主进程
├── preload.js            # 预加载脚本（IPC 桥）
├── launch.sh             # 后端启动脚本（macOS/Linux）
├── launch.bat            # 后端启动脚本（Windows）
├── renderer/             # 渲染进程页面
│   ├── loading.html      # 启动 loading 页
│   └── settings.html     # 设置面板
├── icons/                # 应用图标
├── build/                # electron-builder 资源
└── dist_electron/        # 打包输出目录
```

## 快速开始

### 开发模式

```bash
# 先构建 JAR
cd ../..
mvn clean package -DskipTests

# 启动 Electron
cd scripts/electron
npm install
npm run start
```

### 打包发布

```bash
# macOS
./build.sh mac    # → dist_electron/闲鱼管理器-0.0.1.dmg

# Windows
./build.sh win    # → dist_electron/闲鱼管理器-Setup-0.0.1.exe

# 双平台
./build.sh all
```

### 跳过 JAR 构建（后端已构建时）

```bash
SKIP_JAR=true ./build.sh mac
```

## 构建产物说明

| 平台 | 产物 | 说明 |
|------|------|------|
| macOS | `.dmg` | 标准安装镜像 |
| macOS | `.zip` | 便携压缩包 |
| Windows | `-Setup-.exe` | NSIS 安装向导 |
| Windows | `-Portable-.exe` | 免安装便携版 |

## 数据目录

- **macOS**: `~/Library/Application Support/xianyu-manager-desktop/`
- **Windows**: `%APPDATA%\xianyu-manager-desktop\`

## 自定义配置

编辑 `package.json` > `build` 可自定义：
- `appId` — 应用标识
- `productName` — 显示名称
- `icon` — 应用图标路径
- `nsis` — 安装向导行为
- `target` — 打包目标格式

## 前置要求

- Java 17+
- Maven 3.6+
- Node.js 18+
- (Windows) Visual Studio Build Tools 或 electron-builder 自动下载
