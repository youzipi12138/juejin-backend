# Dev 环境 CI/CD 部署方案（GitHub + 4C4G 单机）

> 状态：方案待确认（尚未实施）
> 日期：2026-10-17
> 目标服务器：4 核 4G Linux 单机
> 代码托管：GitHub

## 1. 目标

- 代码推送到远程 `dev` 分支后，自动完成：构建 → 测试 → 打包镜像 → 部署到 dev 服务器。
- 部署过程可追溯（每次对应一个 commit SHA）。
- 部署失败能自动回滚到上一版本。
- 4C4G 服务器不承担 Maven/Docker 构建压力，只负责运行。
- 密钥不进入 Git 仓库；数据库/Redis 不暴露公网。
- 后续可以平滑增加生产环境（`main` 分支 + 手动审批）。

## 2. 推荐方案

**GitHub Actions（GitHub 托管 Runner） + GHCR + SSH + Docker Compose + Nginx**

```text
开发者 push dev
      │
      ▼
GitHub Actions（托管 Runner，负责构建，不占服务器资源）
  ├─ checkout
  ├─ Java 21 + Maven 缓存
  ├─ mvn clean verify
  ├─ docker build
  └─ push ghcr.io/youzipi12138/juejin-backend:dev-<sha>
      │
      ▼ SSH（部署专用账号）
4C4G 服务器 /opt/juejin
  ├─ deploy.sh <tag>
  ├─ docker compose pull app
  ├─ docker compose up -d app
  ├─ 健康检查 /actuator/health
  └─ 失败自动回滚上一 tag
      │
      ▼
Nginx :80/:443 → 127.0.0.1:8080 (app)
                     ├─ MySQL 8.4（仅容器内网）
                     └─ Redis 7（仅容器内网）
```

### 为什么这样选

| 方案 | 结论 | 原因 |
| --- | --- | --- |
| GitHub 托管 Runner + SSH | ✅ 推荐 | 构建不占 4C4G 内存；免费额度足够；实现简单 |
| 服务器自建 Runner | ❌ 不推荐 | Maven/Docker 构建会吃 1G+ 内存和 CPU，4G 机器风险大 |
| 服务器上 `git pull` + Maven 构建 | ❌ 不推荐 | 构建慢、依赖服务器环境、回滚困难 |
| GitHub Webhook + 自写脚本 | ❌ 不推荐 | 安全性、幂等性、可观测性都要自己实现 |
| Watchtower 自动拉取镜像 | ⚠️ 可选 | 无法做健康检查和自动回滚，不作为主方案 |
| 直接 scp jar + systemd | ⚠️ 备选 | 更轻量，但环境一致性和回滚不如容器方案 |

## 3. 服务器规划

### 3.1 资源预算（4C4G）

| 组件 | 建议限额 | 说明 |
| --- | --- | --- |
| 系统 + Docker | ~500 MB | 预留 |
| Nginx | ~50 MB | 宿主机安装，反向代理 |
| MySQL 8.4 | 1100 MB | `innodb_buffer_pool_size=256M`，关闭 performance_schema |
| Redis 7 | 192 MB | `maxmemory 128mb`，`volatile-lru` |
| Spring Boot 应用 | 900 MB | `-XX:MaxRAMPercentage=65`，堆约 580 MB |
| 余量 | ~1 GB | 给系统缓存和突发流量 |

额外建议：

- 增加 2G swap，防止瞬时内存峰值触发 OOM（不要依赖 swap 跑业务）。
- 关闭服务器上不必要的服务。
- Docker 日志限制 `max-size=10m`、`max-file=3`。
- **绝对不要在服务器上执行 `mvn package` 或 `docker build`**。
- 磁盘建议 ≥ 40G；定期清理旧镜像和 GHCR 旧版本。

### 3.2 目录结构

```text
/opt/juejin/
├── compose.yml          # app + mysql + redis
├── .env                 # 600 权限：应用密钥 + APP_IMAGE
├── deploy.sh            # 部署脚本（含健康检查与回滚）
├── .current_tag         # 当前生效版本
└── backups/             # 可选：数据库备份
```

### 3.3 端口与网络

| 端口 | 暴露范围 | 用途 |
| --- | --- | --- |
| 22 | 公网（建议限制来源 IP） | SSH |
| 80 / 443 | 公网 | Nginx |
| 8080 | 仅 127.0.0.1 | 应用 |
| 3306 | 不暴露 | MySQL，仅 Docker 内网 |
| 6379 | 不暴露 | Redis，仅 Docker 内网 |

> 注意：Docker published ports 可能绕过 UFW 规则，因此数据库端口不要做 `ports` 映射，只使用内部网络。

## 4. 应用侧改造清单

实施 CI/CD 前需要给项目补充：

1. **Dockerfile**：运行镜像基于 `eclipse-temurin:21-jre`，非 root 用户运行。
2. **`.dockerignore`**：排除 `.git`、`docs`、`target` 之外无关文件等。
3. **Actuator 健康检查**：增加 `spring-boot-starter-actuator`，只暴露 `health`，
   供 Docker healthcheck 与部署脚本使用；Nginx 不对外代理 `/actuator`。
4. **Flyway（建议）**：数据库结构变更随应用版本自动迁移，避免手工执行 SQL。
   - 首次引入时 baseline，现有表结构作为 `V1`；
   - 手机号变更为 `V2`；
   - dev 环境开启 `spring.flyway.enabled=true`。
5. **统一配置**：MySQL/Redis 地址、密码、JWT 密钥全部取自环境变量。
6. **日志**：输出到 stdout，由 Docker 收集和轮转。

### Dockerfile 示例

```dockerfile
FROM eclipse-temurin:21-jre

RUN useradd -r -u 1001 app
WORKDIR /app

COPY target/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=65", \
  "-XX:InitialRAMPercentage=25", \
  "-Duser.timezone=Asia/Shanghai", \
  "-jar", "/app/app.jar"]
```

CI 中先执行 `mvn clean verify` 生成 jar，再由 Docker 构建运行镜像，避免重复构建。

## 5. 服务器 Docker Compose

示例（实施时替换镜像地址和密码）：

```yaml
services:
  app:
    image: ${APP_IMAGE:?APP_IMAGE is required}
    env_file: .env
    ports:
      - "127.0.0.1:8080:8080"
    restart: unless-stopped
    mem_limit: 900m
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '\"status\":\"UP\"'"]
      interval: 15s
      timeout: 3s
      retries: 5
      start_period: 40s
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"

  mysql:
    image: mysql:8.4
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_DATABASE: juejin
      TZ: Asia/Shanghai
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --innodb-buffer-pool-size=256M
      - --max-connections=100
      - --performance-schema=OFF
    volumes:
      - mysql-data:/var/lib/mysql
    mem_limit: 1100m
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: ["redis-server", "--appendonly", "no", "--maxmemory", "128mb", "--maxmemory-policy", "volatile-lru"]
    volumes:
      - redis-data:/data
    mem_limit: 192m
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

volumes:
  mysql-data:
  redis-data:
```

`.env`（服务器本地，权限 600，**不要提交 Git**）：

```dotenv
APP_IMAGE=ghcr.io/youzipi12138/juejin-backend:dev-xxxxxxxx

MYSQL_URL=jdbc:mysql://mysql:3306/juejin?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
MYSQL_USERNAME=root
MYSQL_PASSWORD=change-me
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_SECRET=至少32字节的随机字符串
SMS_PEPPER=随机字符串
SMS_MOCK=true
SMS_EXPOSE_CODE=false
```

如果服务器上已有 MySQL/Redis，也可以改为连接宿主机服务：

- 在 compose 中增加 `extra_hosts: ["host.docker.internal:host-gateway"]`；
- `MYSQL_URL` / `REDIS_HOST` 指向 `host.docker.internal`；
- 不启动 mysql/redis 两个容器，节省约 1.2G 内存。

## 6. GitHub Actions 工作流

`.github/workflows/deploy-dev.yml` 示例：

```yaml
name: Deploy Dev

on:
  push:
    branches: [dev]
  workflow_dispatch:

permissions:
  contents: read
  packages: write

concurrency:
  group: deploy-dev
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: temurin
          cache: maven

      - name: Build and test
        run: mvn -B -ntp clean verify

      - name: Lowercase image name
        run: echo "IMAGE_NAME=ghcr.io/${GITHUB_REPOSITORY,,}" >> "$GITHUB_ENV"

      - uses: docker/setup-buildx-action@v3

      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          platforms: linux/amd64
          tags: |
            ${{ env.IMAGE_NAME }}:dev
            ${{ env.IMAGE_NAME }}:dev-${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    needs: build
    runs-on: ubuntu-latest
    timeout-minutes: 10
    environment: dev
    steps:
      - name: Deploy over SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.DEV_HOST }}
          username: ${{ secrets.DEV_USER }}
          key: ${{ secrets.DEV_SSH_KEY }}
          script_stop: true
          script: |
            cd /opt/juejin
            ./deploy.sh dev-${{ github.sha }}
```

要点：

- `concurrency`：同一分支只保留最新一次部署，避免并发发布。
- 镜像同时打 `dev-<sha>`（不可变、可回滚）和 `dev`（最新）两个 tag。
- `environment: dev` 使用 GitHub Environment 管理 dev 的 secrets。
- 构建测试失败时不会进入部署阶段。
- 后续生产环境可增加 `main` 分支 + `environment: production` + 手动审批，复用同一镜像。

## 7. 部署脚本（含健康检查与回滚）

`/opt/juejin/deploy.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail
cd /opt/juejin

TAG="${1:?usage: deploy.sh <tag>}"
IMAGE="ghcr.io/youzipi12138/juejin-backend:${TAG}"   # 与 workflow 中的镜像名保持一致
PREV_TAG="$(cat .current_tag 2>/dev/null || true)"

echo "==> deploy ${IMAGE}"

if grep -q '^APP_IMAGE=' .env; then
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=${IMAGE}|" .env
else
  echo "APP_IMAGE=${IMAGE}" >> .env
fi

docker compose pull app
docker compose up -d --no-deps app

for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "${TAG}" > .current_tag
    echo "==> deploy success: ${TAG}"
    docker image prune -f --filter "until=72h" >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 2
done

echo "==> health check failed"
if [ -n "${PREV_TAG}" ]; then
  echo "==> rollback to ${PREV_TAG}"
  sed -i "s|^APP_IMAGE=.*|APP_IMAGE=ghcr.io/youzipi12138/juejin-backend:${PREV_TAG}|" .env
  docker compose up -d --no-deps app
fi
exit 1
```

回滚方式：

1. 自动：健康检查 60 秒不通过，自动切回 `.current_tag` 记录的上一版本；
2. 手动：`./deploy.sh dev-<上一个 commit sha>`；
3. 在 GitHub Actions 中重跑历史成功 workflow。

## 8. Nginx 与 HTTPS

宿主机安装 Nginx，反向代理到 `127.0.0.1:8080`：

```nginx
server {
    listen 80;
    server_name dev.example.com;

    # 健康检查接口不对外暴露
    location /actuator/ {
        deny all;
    }

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
```

- 有域名：用 certbot 申请证书并自动续期：

```bash
certbot --nginx -d dev.example.com
```

- 没有域名：可先用 `http://服务器IP`，但 JWT 和验证码走明文 HTTP 不安全，建议尽快加域名和 HTTPS。
- 应用侧开启 `server.forward-headers-strategy: native`，保证 IP 限流拿到真实客户端 IP。

## 9. GitHub 与服务器初始化

### 9.1 服务器

```bash
# 1. 创建部署用户并加入 docker 组
adduser deploy
usermod -aG docker deploy

# 2. 防火墙
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable

# 3. 工作目录
mkdir -p /opt/juejin
chown deploy:deploy /opt/juejin

# 4. 安装 Docker Engine + compose plugin（按发行版官方文档）
# 5. 安装 Nginx / certbot
# 6. 登录 GHCR（使用只读 package 权限的 PAT）
docker login ghcr.io -u <github-user>
```

### 9.2 GitHub

1. 仓库 Settings → Environments → 新建 `dev`。
2. 添加 Environment secrets：
   - `DEV_HOST`：服务器 IP/域名
   - `DEV_USER`：`deploy`
   - `DEV_SSH_KEY`：部署专用私钥
3. 本地生成专用的 ed25519 密钥，把公钥加到服务器 `deploy` 用户的 `authorized_keys`。
4. 建议对 `dev` 分支开启保护：至少要求 CI 通过。
5. GitHub Container Registry 中设置旧版本清理策略，避免镜像无限增长。
6. 私有镜像拉取：服务器使用只读 `read:packages` PAT 登录 GHCR；
   不要把 PAT 提交到仓库。

## 10. 首次上线步骤

1. 应用改造：Dockerfile、Actuator、Flyway、环境变量配置。
2. 本地验证镜像：`docker build` + `docker run` 连接测试库。
3. 准备服务器：Docker、Nginx、目录、`.env`、GHCR 登录。
4. 将 `compose.yml`、`deploy.sh`、Nginx 配置部署到服务器。
5. 推送代码到 `dev`，观察 Actions 构建和 SSH 部署。
6. 验证健康检查、登录接口、SMS 接口。
7. 人为制造一次失败（例如改错健康检查），验证自动回滚。
8. 补充备份和监控告警。

## 11. 监控与备份

- **应用**：Docker healthcheck + `/actuator/health`（仅本机访问）。
- **部署**：GitHub Actions 状态页；可加飞书/Lark webhook 通知。
- **服务器**：`docker stats`、磁盘使用率、Nginx 5xx；配置简单的 Cron 巡检或外部 Uptime 监控。
- **数据库**：`mysqldump` 每日备份到 `/opt/juejin/backups`，并定期同步到对象存储。
- **Redis**：当前只存 token/验证码，可丢失，不强制备份。
- **日志**：`docker compose logs -f app`，配合 Docker 日志轮转。

## 12. 安全设计

- CI/CD 专用 SSH 密钥，仅用于部署；可进一步限制 `authorized_keys` 的 `command`。
- 服务器只开放 22/80/443；数据库与 Redis 不映射公网端口。
- 应用端口绑定 `127.0.0.1`，只允许 Nginx 访问。
- 应用密钥放服务器 `.env`（600），GitHub 只保存部署凭据。
- GHCR 使用最小权限 token（`read:packages`）。
- Actions 使用最小 `permissions`，第三方 Action 建议固定版本（长期可固定到 commit SHA）。
- 建议安装 fail2ban，SSH 禁用密码登录，只允许密钥。
- 生产环境（后续）增加 GitHub Environment 手动审批。

## 13. 已知限制

- 单机部署，服务器宕机即服务不可用；dev 环境可接受。
- 当前方案是滚动重启，重启期间有数秒不可用；如果后续需要零停机，可升级为蓝绿部署（两个 app 容器 + Nginx upstream 切换，4G 内存需重新评估）。
- MySQL/Redis 与应用同机，资源竞争明显；后续可拆到独立实例或托管数据库。
- 未包含多环境配置中心、分布式追踪、Prometheus 等重型组件。
- Flyway 需要首次引入时做好 baseline，避免对已有表重复执行 `CREATE TABLE`。

## 14. 实施前需要确认

1. 服务器操作系统与 CPU 架构（`uname -m`、`cat /etc/os-release`），是否为 `linux/amd64`。
2. 是否有可用的 dev 域名（用于 HTTPS）。
3. 服务器上是否已有 MySQL/Redis：
   - 已有：直接复用，需提供连接方式；
   - 没有：使用本文的 Docker Compose 一并启动（推荐）。
4. GitHub 仓库是 public 还是 private，dev 分支名是否固定为 `dev`。
5. 是否同意本次一并加入 Actuator 健康检查和 Flyway 数据库迁移。

## 15. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-10-17 | 初版 CI/CD 方案 |
