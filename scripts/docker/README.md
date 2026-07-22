# 闲鱼管理器 · Docker 版

基于 Docker Compose 的容器化方案，支持 **SQLite（默认）** 和 **MySQL 8** 两种数据库模式。

## 架构说明

### SQLite 模式（单容器）

```
┌─────────────────────────┐
│ xianyu-manager          │
│ Spring Boot + SQLite    │
│ Chromium (滑块验证)     │
└─────────────────────────┘
```

### MySQL 8 模式（双容器）

```
┌─────────────────────────┐     ┌─────────────────────────┐
│ xianyu-manager          │ ──→ │ MySQL 8                 │
│ Spring Boot + MyBatis   │ ←── │ (持久化卷)              │
│ Chromium (滑块验证)     │     └─────────────────────────┘
└─────────────────────────┘
```

## 目录结构

```
scripts/docker/
├── build.sh                    # 构建 & 启动脚本
├── Dockerfile                  # 多阶段构建 Dockerfile
├── docker-compose.yml          # SQLite 版 docker-compose
├── docker-compose.mysql.yml    # MySQL 版 docker-compose（扩展）
├── mysql-init/                 # MySQL 初始化脚本
│   └── 01-init.sql
├── mysql-config/               # MySQL 自定义配置
│   └── custom.cnf
├── .env.example                # 环境变量模板
└── build/                      # 构建输出
```

## 快速开始

### 1. SQLite 模式（最简单）

```bash
cd scripts/docker

# 构建镜像
./build.sh build

# 启动服务
./build.sh compose
# 或: docker compose -f docker-compose.yml up -d

# 访问
open http://localhost:8080
```

### 2. MySQL 8 模式

```bash
cd scripts/docker

# 配置环境变量
cp .env.example .env
# 编辑 .env，设置 MySQL 密码

# 构建 & 启动
DB_MODE=mysql ./build.sh all
# 或: docker compose -f docker-compose.yml -f docker-compose.mysql.yml up -d

# 访问
open http://localhost:8080
```

## 自定义配置

### 通过 .env 文件

```bash
cp .env.example .env
# 编辑 .env 修改端口、密码等

# 使用自定义 .env
docker compose --env-file .env up -d
```

### 通过 application.yml 挂载

```yaml
# docker-compose.yml
volumes:
  - ./config/application.yml:/app/config/application.yml:ro
```

### 通过命令行参数

```bash
docker run -d \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host:3306/xianyu \
  -e XIANYU_JWT_SECRET=my-secret \
  -v /my/data:/app/data \
  xianyu-manager:latest
```

## 数据持久化

| 数据 | 宿主机路径 | 容器路径 |
|------|-----------|---------|
| SQLite DB | `./data/` | `/app/data/` |
| Chrome 配置 | `./chrome-profiles/` | `/app/chrome-profiles/` |
| 日志 | `./logs/` | `/app/logs/` |
| 配置 | `./config/` | `/app/config/` |
| MySQL 数据 | `mysql-data` 卷 | `/var/lib/mysql/` |

## 健康检查

```bash
# 查看容器状态
docker compose ps

# 查看日志
docker compose logs -f xianyu-manager

# 健康检查
docker inspect --format='{{.State.Health.Status}}' xianyu-manager
```

## 常用命令

```bash
# 启动
docker compose up -d

# 停止
docker compose down

# 重启
docker compose restart

# 查看日志
docker compose logs -f --tail=100

# 进入容器
docker exec -it xianyu-manager bash

# 备份 SQLite
docker exec xianyu-manager tar czf - /app/data > backup-$(date +%Y%m%d).tar.gz

# 备份 MySQL
docker exec xianyu-mysql mysqldump -u root -p xianyu_manager > backup-$(date +%Y%m%d).sql

# 清理（保留数据卷）
docker compose down --rmi local

# 完全清理
docker compose down -v --rmi local
```

## 与现有 Dockerfile/docker-compose 的关系

项目根目录已有的 `Dockerfile` 和 `docker-compose.yml` 是早期版本。本目录提供：
- 更完善的多阶段构建
- MySQL 8 支持
- 独立的构建脚本
- 更好的配置管理

## 构建镜像

```bash
# 本地构建
./build.sh build

# 构建 & 推送
TAG=v1.0.0 REGISTRY=registry.example.com/xianyu ./build.sh push
```

## 要求

- Docker Engine 20.10+
- Docker Compose v2+
- macOS / Linux / Windows (WSL2)



### 推送方式

支持构建并推送三种镜像：

    sqlite                                                                                                                                                                                                                          
    mysql                                                                                                                                                                                                                           
    postgres                                                                                                                                                                                                                        

镜像标签格式：

    <ACR_REGISTRY>/<ACR_NAMESPACE>/<IMAGE_NAME>:sqlite-<TAG>                                                                                                                                                                        
    <ACR_REGISTRY>/<ACR_NAMESPACE>/<IMAGE_NAME>:mysql-<TAG>                                                                                                                                                                         
    <ACR_REGISTRY>/<ACR_NAMESPACE>/<IMAGE_NAME>:postgres-<TAG>                                                                                                                                                                      

例如：

    registry.cn-hangzhou.aliyuncs.com/your-namespace/xianyu-manager:sqlite-v1.0.0                                                                                                                                                   
    registry.cn-hangzhou.aliyuncs.com/your-namespace/xianyu-manager:mysql-v1.0.0                                                                                                                                                    
    registry.cn-hangzhou.aliyuncs.com/your-namespace/xianyu-manager:postgres-v1.0.0                                                                                                                                                 

Windows 使用方式：

    set ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com                                                                                                                                                                              
    set ACR_NAMESPACE=你的命名空间                                                                                                                                                                                                  
    set TAG=v1.0.0                                                                                                                                                                                                                  
    set IMAGE_NAME=xianyu-manager                                                                                                                                                                                                   
                                                                                                                                                                                                                                    
    scripts\docker\publish-acr.bat                                                                                                                                                                                                  

如果需要脚本自动登录 ACR：

    set ACR_USERNAME=你的阿里云ACR用户名                                                                                                                                                                                            
    set ACR_PASSWORD=你的阿里云ACR密码或访问凭证                                                                                                                                                                                    
    scripts\docker\publish-acr.bat                                                                                                                                                                                                  

只构建不推送：

    scripts\docker\publish-acr.bat --build-only                                                                                                                                                                                     

只构建某一种：

    scripts\docker\publish-acr.bat --mode sqlite --build-only                                                                                                                                                                       
    scripts\docker\publish-acr.bat --mode mysql --build-only                                                                                                                                                                        
    scripts\docker\publish-acr.bat --mode postgres --build-only                                                                                                                                                                     

Linux/macOS 使用方式：

    ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com \                                                                                                                                                                                
    ACR_NAMESPACE=你的命名空间 \                                                                                                                                                                                                    
    TAG=v1.0.0 \                                                                                                                                                                                                                    
    IMAGE_NAME=xianyu-manager \                                                                                                                                                                                                     
    bash scripts/docker/publish-acr.sh                                                                                                                                                                                              

如果需要自动登录：

    ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com \                                                                                                                                                                                
    ACR_NAMESPACE=你的命名空间 \                                                                                                                                                                                                    
    ACR_USERNAME=你的阿里云ACR用户名 \                                                                                                                                                                                              
    ACR_PASSWORD=你的阿里云ACR密码或访问凭证 \                                                                                                                                                                                      
    TAG=v1.0.0 \                                                                                                                                                                                                                    
    bash scripts/docker/publish-acr.sh                                                                                                                                                                                              

已验证：

    bash -n scripts/docker/publish-acr.sh                                                                                                                                                                                           
    bash scripts/docker/publish-acr.sh --help                                                                                                                                                                                       
    cmd.exe //c "scripts\\docker\\publish-acr.bat --help"                                                                                                                                                                           
    cmd.exe //c "scripts\\docker\\publish-acr.bat --mode bad --build-only"    
