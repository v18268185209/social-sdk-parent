# 闲鱼管理器 · Android 版

基于 Capacitor 的跨平台方案，将 Vue 前端包装为 Android 原生应用，通过 REST/WebSocket 与后端通信。

## 架构说明

```
┌─────────────────────────┐
│ Android 原生壳(Capacitor)│
│  ┌─────────────────────┐│
│  │ WebView             ││  ← 运行 Vue 前端
│  └─────────────────────┘│
│         ↕ REST/WS       │
└─────────────────────────┘
         ↕ 网络
┌─────────────────────────┐
│ Spring Boot 后端 (JAR)   │   ← 独立部署在服务器
└─────────────────────────┘
```

> ⚠️ Android 应用本身**不包含**后端。后端需要部署在服务器，App 通过 API 地址连接。

## 目录结构

```
scripts/android/
├── build.sh                     # 打包入口
├── AndroidManifest.xml          # Android 应用配置模板
├── keystore.example.properties  # 签名配置模板
└── build/                       # 构建输出（APK / AAB）
```

## 使用流程

### 1. 环境准备

```bash
# 安装 JDK 17+
brew install openjdk@17  # macOS
# 或 sudo apt install openjdk-17-jdk

# 安装 Android Studio
# https://developer.android.com/studio

# 设置 ANDROID_HOME
echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
echo 'export ANDROID_SDK_ROOT=$ANDROID_HOME' >> ~/.bashrc
```

### 2. 初始化（首次）

```bash
./build.sh init
```

这会：
- 安装 Capacitor 依赖
- 创建 Android 工程
- 构建前端并同步

### 3. 日常构建

```bash
# 构建 Debug APK（无需签名）
./build.sh build

# 构建 AAB（Google Play 上架）
./build.sh bundle

# 打开 Android Studio
./build.sh open

# 仅同步前端
./build.sh sync

# 清理
./build.sh clean
```

## 签名配置

### 生成 Keystore

```bash
keytool -genkey -v \
    -keystore xianyu-manager.keystore \
    -alias xianyu \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "你的密钥库密码" \
    -keypass "你的密钥密码"
```

### 配置签名

```bash
cp keystore.example.properties keystore.properties
# 编辑 keystore.properties 填入 keystore 路径和密码
```

## 配置后端地址

修改 `android-project/capacitor.config.ts`：

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

- JDK 17+
- Android Studio Hedgehog (2023.1) 或更高
- Android SDK 33+
- Node.js 18+
- Gradle 8+（Android Studio 自带）

## 产物

| 产物 | 说明 |
|------|------|
| `app-debug.apk` | 可直接安装的测试版 APK |
| `app-release.apk` | 签名的发布版 APK |
| `app-release.aab` | Google Play 上架用的 App Bundle |
