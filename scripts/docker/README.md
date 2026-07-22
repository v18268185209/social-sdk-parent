# 闲鱼管理器 · Docker 版

基于 Docker / Docker Compose 的容器化部署方案，支持三种数据库模式：

- **SQLite**：最简单，单容器启动，推荐新手/单机部署。
- **MySQL 8**：应用容器 + MySQL 容器。
- **PostgreSQL**：应用容器 + PostgreSQL 容器。

## 镜像仓库信息

当前项目镜像发布到阿里云 ACR：

| 配置项 | 值 |
|---|---|
| ACR_REGISTRY | `registry.cn-hangzhou.aliyuncs.com` |
| ACR_NAMESPACE | `eqadmin` |
| IMAGE_NAME | `xianyu-manager` |
| TAG | `0.0.2` |

三种镜像地址：

```text
registry.cn-hangzhou.aliyuncs.com/eqadmin/xianyu-manager:sqlite-0.0.2
registry.cn-hangzhou.aliyuncs.com/eqadmin/xianyu-manager:mysql-0.0.2
registry.cn-hangzhou.aliyuncs.com/eqadmin/xianyu-manager:postgres-0.0.2
```

## 目录结构

```text
scripts/docker/
├── Dockerfile                    # 多阶段构建 Dockerfile
├── README.md                     # Docker 使用说明
├── .env.example                  # 环境变量模板，复制为 .env 后使用
├── build.sh                      # Linux/macOS 构建 & 启动脚本
├── publish-acr.sh                # Linux/macOS 构建并推送到 ACR
├── publish-acr.bat               # Windows 构建并推送到 ACR
├── docker-compose.yml            # SQLite 版 docker-compose
├── docker-compose.mysql.yml      # MySQL 版 docker-compose 扩展
├── docker-compose.postgres.yml   # PostgreSQL 版 docker-compose 扩展
├── mysql-init/                   # MySQL 初始化脚本
├── mysql-config/                 # MySQL 自定义配置
├── data/                         # SQLite 数据、上传文件等运行时数据
├── chrome-profiles/              # Chromium 登录态/用户数据
├── logs/                         # 应用日志
└── config/                       # 外部配置目录
```

## 一、从 ACR 拉取镜像部署（推荐普通用户）

### 1. 复制环境变量文件

Windows PowerShell / CMD：

```bat
cd scripts\docker
copy .env.example .env
```

Linux / macOS：

```bash
cd scripts/docker
cp .env.example .env
```

如果需要修改端口、数据库密码，可以编辑 `.env`。默认配置已经可以直接启动。

> 普通用户只需要 `docker-compose.yml` 和 `.env`。不要复制/引用 `docker-compose.build.yml`，它是源码构建用的，会查找本地 `Dockerfile`。

### 2. 启动 SQLite 镜像（最简单，会自动从 ACR 拉取）

Windows：

```bat
set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
set ACR_NAMESPACE=eqadmin
set IMAGE_NAME=xianyu-manager
set TAG=0.0.2

docker compose --env-file .env -f docker-compose.yml up -d
```

Linux / macOS：

```bash
export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
export ACR_NAMESPACE=eqadmin
export IMAGE_NAME=xianyu-manager
export TAG=0.0.2

docker compose --env-file .env -f docker-compose.yml up -d
```

访问：

```text
http://localhost:8080
```

服务器部署时访问：

```text
http://服务器IP:8080
```

### 3. 启动 MySQL 镜像（会自动从 ACR 拉取）

Windows：

```bat
set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
set ACR_NAMESPACE=eqadmin
set IMAGE_NAME=xianyu-manager
set TAG=0.0.2

docker compose --env-file .env -f docker-compose.yml -f docker-compose.mysql.yml up -d
```

Linux / macOS：

```bash
export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
export ACR_NAMESPACE=eqadmin
export IMAGE_NAME=xianyu-manager
export TAG=0.0.2

docker compose --env-file .env -f docker-compose.yml -f docker-compose.mysql.yml up -d
```

### 4. 启动 PostgreSQL 镜像（会自动从 ACR 拉取）

Windows：

```bat
set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
set ACR_NAMESPACE=eqadmin
set IMAGE_NAME=xianyu-manager
set TAG=0.0.2

docker compose --env-file .env -f docker-compose.yml -f docker-compose.postgres.yml up -d
```

Linux / macOS：

```bash
export ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
export ACR_NAMESPACE=eqadmin
export IMAGE_NAME=xianyu-manager
export TAG=0.0.2

docker compose --env-file .env -f docker-compose.yml -f docker-compose.postgres.yml up -d
```

## 二、本地源码构建部署（开发者使用）

### SQLite 模式

```bash
cd scripts/docker
cp .env.example .env
DB_MODE=sqlite docker compose --env-file .env -f docker-compose.yml -f docker-compose.build.yml up -d --build
```

### MySQL 模式

```bash
cd scripts/docker
cp .env.example .env
DB_MODE=mysql docker compose --env-file .env -f docker-compose.yml -f docker-compose.mysql.yml -f docker-compose.build.yml up -d --build
```

### PostgreSQL 模式

```bash
cd scripts/docker
cp .env.example .env
DB_MODE=postgres docker compose --env-file .env -f docker-compose.yml -f docker-compose.postgres.yml -f docker-compose.build.yml up -d --build
```

也可以使用脚本：

```bash
# SQLite
./build.sh compose

# MySQL
DB_MODE=mysql ./build.sh compose

# PostgreSQL
DB_MODE=postgres ./build.sh compose
```

## 三、构建并推送镜像到 ACR（维护者使用）

如果拉取后的容器日志出现：

```text
no main manifest attribute, in /app/server.jar
```

说明旧镜像里复制进容器的不是 Spring Boot 可执行 JAR。请使用最新 `scripts/docker/Dockerfile` 重新构建并覆盖推送 `0.0.2` 镜像，然后在部署机器执行：

```bash
docker compose --env-file .env -f docker-compose.yml pull
docker compose --env-file .env -f docker-compose.yml up -d --force-recreate
```

### Windows 推送

```bat
set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
set ACR_NAMESPACE=eqadmin
set TAG=0.0.2
set IMAGE_NAME=xianyu-manager

scripts\docker\publish-acr.bat
```

如果需要脚本自动登录 ACR：

```bat
set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
set ACR_NAMESPACE=eqadmin
set TAG=0.0.2
set IMAGE_NAME=xianyu-manager
set ACR_USERNAME=你的阿里云ACR用户名
set ACR_PASSWORD=你的阿里云ACR密码或访问凭证

scripts\docker\publish-acr.bat
```

只构建不推送：

```bat
scripts\docker\publish-acr.bat --build-only
```

只构建或推送某一种数据库镜像：

```bat
scripts\docker\publish-acr.bat --mode sqlite
scripts\docker\publish-acr.bat --mode mysql
scripts\docker\publish-acr.bat --mode postgres
```

### Linux / macOS 推送

```bash
ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com \
ACR_NAMESPACE=eqadmin \
TAG=0.0.2 \
IMAGE_NAME=xianyu-manager \
bash scripts/docker/publish-acr.sh
```

如果需要自动登录 ACR：

```bash
ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com \
ACR_NAMESPACE=eqadmin \
TAG=0.0.2 \
IMAGE_NAME=xianyu-manager \
ACR_USERNAME=你的阿里云ACR用户名 \
ACR_PASSWORD=你的阿里云ACR密码或访问凭证 \
bash scripts/docker/publish-acr.sh
```

只构建不推送：

```bash
bash scripts/docker/publish-acr.sh --build-only
```

只构建或推送某一种数据库镜像：

```bash
bash scripts/docker/publish-acr.sh --mode sqlite
bash scripts/docker/publish-acr.sh --mode mysql
bash scripts/docker/publish-acr.sh --mode postgres
```

## 四、常用运维命令

### 查看服务状态

SQLite：

```bash
docker compose --env-file .env -f docker-compose.yml ps
```

MySQL：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.mysql.yml ps
```

PostgreSQL：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.postgres.yml ps
```

### 查看日志

```bash
docker compose --env-file .env -f docker-compose.yml logs -f xianyu-manager
```

### 健康检查

```bash
curl http://localhost:8080/api/system/health
```

容器健康状态：

```bash
docker inspect --format='{{.State.Health.Status}}' xianyu-manager
```

### 停止服务

SQLite：

```bash
docker compose --env-file .env -f docker-compose.yml down
```

MySQL：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.mysql.yml down
```

PostgreSQL：

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.postgres.yml down
```

### 重启服务

```bash
docker compose --env-file .env -f docker-compose.yml restart xianyu-manager
```

### 进入应用容器

```bash
docker exec -it xianyu-manager bash
```

## 五、数据持久化

| 数据 | 宿主机路径/卷 | 容器路径 |
|---|---|---|
| SQLite 数据库、上传文件 | `./data/` | `/app/data/` |
| Chrome 登录态/用户数据 | `./chrome-profiles/` | `/app/chrome-profiles/` |
| 应用日志 | `./logs/` | `/app/logs/` |
| 外部配置 | `./config/` | `/app/config/` |
| MySQL 数据 | `mysql-data` Docker 卷 | `/var/lib/mysql/` |
| PostgreSQL 数据 | `postgres-data` Docker 卷 | `/var/lib/postgresql/data/` |

### 备份 SQLite 数据

```bash
docker exec xianyu-manager tar czf - /app/data > backup-sqlite.tar.gz
```

### 备份 MySQL 数据

```bash
docker exec xianyu-mysql mysqldump -u root -p xianyu_manager > backup-mysql.sql
```

### 备份 PostgreSQL 数据

```bash
docker exec xianyu-postgres pg_dump -U xianyu xianyu_manager > backup-postgres.sql
```

## 六、注意事项

1. 生产环境请修改 `.env` 中的安全密钥：

   ```text
   XIANYU_JWT_SECRET
   XIANYU_CRYPTO_SECRET
   ```

2. 如果宿主机端口被占用，修改 `.env`：

   ```text
   APP_PORT=18080
   MYSQL_PORT=13306
   POSTGRES_PORT=15432
   ```

3. Docker/Linux 环境默认使用无头浏览器：

   ```text
   CHROME_HEADLESS=true
   CHROME_HEADLESS_MODE=new
   ```

4. SQLite 最省事；MySQL/PostgreSQL 更适合多人、多数据量或长期生产部署。

5. 如果 ACR 镜像是私有仓库，拉取前需要先登录：

   ```bash
   docker login registry.cn-hangzhou.aliyuncs.com
   ```

## 七、环境要求

- Docker Engine 20.10+
- Docker Compose v2+
- Java / Maven 仅源码构建镜像时需要；单纯拉取 ACR 镜像运行不需要本机安装 Java / Maven。
