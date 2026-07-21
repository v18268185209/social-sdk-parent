# 闲鱼管理器 · iOS 版

基于 Capacitor 的跨平台方案，将 Vue 前端包装为 iOS 原生应用，通过 REST/WebSocket 与后端通信。

## 架构说明

```
┌─────────────────────────┐
│  iOS 原生壳 (Capacitor)  │
│  ┌─────────────────────┐│
│  │ WKWebView           ││  ← 运行 Vue 前端
│  └─────────────────────┘│
│         ↕ REST/WS       │
└─────────────────────────┘
         ↕ 网络
┌─────────────────────────┐
│ Spring Boot 后端 (JAR)   │   ← 独立部署在服务器
└─────────────────────────┘
```

> ⚠️ iOS 应用本身**不包含**后端。后端需要部署在服务器，App 通过 API 地址连接。

## 目录结构

```
scripts/ios/
├── build.sh              # 打包入口
├── Info.plist            # iOS 应用配置模板
├── ExportOptions.plist   # IPA 导出配置（自动生成）
└── build/                # 构建输出
```

## 使用流程

### 1. 初始化（首次）

```bash
./build.sh init
```

这会：
- 安装 Capacitor 依赖
- 创建 iOS 工程
- 构建前端并同步

### 2. 日常构建

```bash
# 构建 IPA
./build.sh build

# 打开 Xcode 手动签名
./build.sh open

# 仅同步前端
./build.sh sync

# 清理
./build.sh clean
```

## 签名配置

### 自动签名（推荐）

1. 在 Xcode 中选择 `App` → `Signing & Capabilities`
2. 勾选 `Automatically manage signing`
3. 选择你的 Team

### 手动签名

编辑 `build/ExportOptions.plist`：
```xml
<key>teamID</key>
<string>你的 Team ID</key>
<key>method</key>
<string>app-store</string>  <!-- 或 ad-hoc / enterprise -->
```

## 配置后端地址

修改 `ios-project/capacitor.config.ts`：

```typescript
server: {
    url: 'https://your-server.com:8080',
    cleartext: true  // 允许 HTTP
}
```

或通过环境变量：

```bash
export XIANYU_API_BASE=https://your-server.com:8080
./build.sh build
```

## 要求

- macOS 13+
- Xcode 15+
- CocoaPods
- Node.js 18+
- Apple Developer 账号（真机运行/发布）

## 产物

| 产物 | 说明 |
|------|------|
| `App.xcarchive` | Xcode 归档 |
| `App.ipa` | 可安装的 IPA 文件 |
