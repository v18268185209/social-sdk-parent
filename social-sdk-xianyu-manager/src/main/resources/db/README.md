# 数据库三选一配置指南

本平台支持 **SQLite / MySQL8 / PostgreSQL** 三种数据库，**三选一**（不并存），默认 SQLite。

## 切换方式

通过 Spring profile 切换，对应三个配置文件：

| profile | 配置文件 | schema 文件 | 适用场景 |
|---|---|---|---|
| `sqlite`（默认） | `application-sqlite.yml` | `db/schema-sqlite.sql` | 单机/开发/小规模 |
| `mysql` | `application-mysql.yml` | `db/schema-mysql.sql` | 中大规模生产 |
| `postgres` | `application-postgres.yml` | `db/schema-postgres.sql` | 中大规模生产 |

## 启动参数

### SQLite（默认）
```bash
java -jar xianyu-manager.jar
# 或显式：
java -jar xianyu-manager.jar --spring.profiles.active=sqlite
```

### MySQL8
```bash
# 先用 Docker 部署 MySQL8
docker run -d --name xianyu-mysql \
  -e MYSQL_ROOT_PASSWORD=xianyu123 \
  -e MYSQL_DATABASE=xianyu_manager \
  -p 3306:3306 mysql:8

# 启动应用
java -jar xianyu-manager.jar \
  --spring.profiles.active=mysql \
  --DB_URL=jdbc:mysql://localhost:3306/xianyu_manager?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true \
  --DB_USERNAME=root \
  --DB_PASSWORD=xianyu123
```

### PostgreSQL
```bash
# 先用 Docker 部署 PostgreSQL
docker run -d --name xianyu-pg \
  -e POSTGRES_USER=xianyu \
  -e POSTGRES_PASSWORD=xianyu123 \
  -e POSTGRES_DB=xianyu_manager \
  -p 5432:5432 postgres:16

# 启动应用
java -jar xianyu-manager.jar \
  --spring.profiles.active=postgres \
  --DB_URL=jdbc:postgresql://localhost:5432/xianyu_manager \
  --DB_USERNAME=xianyu \
  --DB_PASSWORD=xianyu123
```

## 环境变量兜底

`application.yml` 里的 datasource 用 `${DB_*:-default}` 占位，三种方式都可覆盖：

| 变量 | 默认值（SQLite） | 作用 |
|---|---|---|
| `DB_DRIVER` | `org.sqlite.JDBC` | JDBC 驱动类 |
| `DB_URL` | `jdbc:sqlite:./data/xianyu-manager.db` | 数据库 URL |
| `DB_USERNAME` | 空 | 用户名 |
| `DB_PASSWORD` | 空 | 密码 |

> profile 文件会自动设好 `DB_DRIVER` / `DB_URL`，用户只需补 `DB_USERNAME` / `DB_PASSWORD`。

## 内部实现

- **`DatabaseProvider` 接口**（`config/db/`）：方言抽象，定义 `schemaFile()` / `connectionInitSqls()` / `maxActive()` / `validationQuery()` 等。
- **三实现**：`SqliteProvider`（`@Profile("sqlite")`）/ `MysqlProvider`（`@Profile("mysql")`）/ `PostgresProvider`（`@Profile("postgres")`）。
- **`DruidConfig`**：从 `DatabaseProvider` 拿连接初始化 SQL 和 maxActive，SQLite 单连接最优，MySQL/PG 并发 20。
- **`DatabaseInitializer`**：按 profile 加载对应 schema 文件，启动时自动建表。
- **三套 schema**：方言差异已处理（SQLite `INTEGER PRIMARY KEY` / MySQL `BIGINT AUTO_INCREMENT` / PG `BIGSERIAL`；`DATETIME` → `TIMESTAMP`；外键引用列类型对齐）。

## 已实测

- SQLite：默认 schema 加载通过。
- MySQL8：Docker `mysql:8` 容器，schema-mysql.sql 全表建成（45 张表）。
- PostgreSQL：Docker `postgres:16` 容器，schema-postgres.sql 全表建成（44 张表）。
