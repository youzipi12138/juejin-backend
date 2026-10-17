# Dev 环境 CI/CD 部署方案

> 状态：已实施并上线
> 日期：2026-10-17
> 代码仓库：`youzipi12138/juejin-backend`
> 服务器：腾讯云 **175**（`49.233.89.175`，`ap-beijing`，4C/3.6G，Ubuntu 24.04）
> Dev 访问地址：http://49.233.89.175

## 1. 最终架构

```text
开发者 push dev
      │
      ▼
GitHub Actions（GitHub 托管 Runner）
  ├─ checkout
  ├─ Java 21 + Maven
  ├─ mvn clean verify
  └─ SSH 到 175，执行 deploy.sh <commit-sha>
      │
      ▼
175 服务器 /opt/juejin
  ├─ git fetch + checkout 指定 SHA
  ├─ docker compose stop app        # 构建时释放内存
  ├─ mvn clean package              # 服务器本地构建
  ├─ docker build -t juejin-backend:local-<sha>
  ├─ docker compose up -d
  ├─ /actuator/health 健康检查
  └─ 失败自动回滚上一个本地镜像
      │
      ▼
Nginx :80 → 127.0.0.1:8080 (app)
              ├─ MySQL 8.4（仅 Docker 内网）
              └─ Redis 7（仅 Docker 内网）
```

**核心原则：构建和运行都在国内服务器完成，避免任何跨境镜像传输。**

## 2. 方案演进与结论

| 方案 | 实测结果 | 结论 |
| --- | --- | --- |
| GitHub Actions 构建 → GHCR → 服务器拉取 | 服务器到 GHCR 只有约 100 KB/s，10 分钟只拉了 22MB，deploy 超时 | ❌ 放弃 |
| GitHub Actions 构建 → 腾讯云 TCR → 服务器拉取 | TCR 个人版免费且已开通，但 GitHub 海外 Runner 推送镜像到广州 TCR 超过 12 分钟 | ❌ 放弃 |
| GitHub Actions 校验 + SSH 触发；175 本地 Maven/Docker 构建 | Run #3 约 80 秒完成部署 | ✅ 采用 |
| 175 安装 GitHub self-hosted runner | 可行，但配置更复杂，服务器资源占用更长期 | ⏸ 备选 |

网络实测：

- 腾讯云国内镜像：**40 MB/s**
- 服务器到 GitHub：**约 100 KB/s**
- 服务器到腾讯云 TCR：**约 87ms 延迟**

因此最终把“镜像传输”这个跨环节彻底去掉。

## 3. 服务器与环境

### 3.1 基础信息

| 项 | 值 |
| --- | --- |
| 主机 | `49.233.89.175` / `VM-0-15-ubuntu` |
| 地域 | `ap-beijing` |
| 配置 | 4C / 3.6G，40G 系统盘 |
| OS | Ubuntu 24.04 LTS |
| Docker | 27.5.1 |
| Docker Compose | v2.32.4 |
| JDK | OpenJDK 21.0.12 |
| Maven | 3.8.7 |
| Nginx | 1.24.0 |
| Swap | 系统已有 `/swap.img` 约 1.9G |
| 部署用户 | `ubuntu`（已加入 docker 组，免密 sudo） |

### 3.2 目录结构

```text
/opt/juejin/
├── compose.yml          # app + mysql + redis
├── .env                 # 600 权限，应用密钥 + APP_IMAGE
├── deploy.sh            # 拉代码、构建、启动、健康检查、回滚
├── .current_tag         # 当前生效的本地镜像 tag
└── repo/                # dev 分支的浅克隆
```

### 3.3 端口

| 端口 | 暴露范围 | 用途 |
| --- | --- | --- |
| 22 | 公网 | SSH |
| 80 | 公网 | Nginx（当前已开放） |
| 443 | 未配置 | 需要域名和证书后再开 |
| 8080 | 仅 127.0.0.1 | Spring Boot |
| 3306 / 6379 | 不暴露 | MySQL / Redis，仅 Docker 内网 |

### 3.4 国内加速配置

Maven（`~/.m2/settings.xml`）：

```xml
<mirror>
  <id>aliyun</id>
  <url>https://maven.aliyun.com/repository/public</url>
  <mirrorOf>central</mirrorOf>
</mirror>
```

Docker（`/etc/docker/daemon.json`）：

```json
{
  "registry-mirrors": ["https://mirror.ccs.tencentyun.com"]
}
```

效果：

- JRE 基础镜像 `eclipse-temurin:21-jre` 拉取约 **6 秒**
- Maven 首次完整构建约 **48 秒**，Docker 镜像构建约 **49 秒**
- 后续构建有缓存，整体部署约 **1–2 分钟**

### 3.5 应用密钥

服务器 `/opt/juejin/.env`（权限 `600`）保存：

```text
APP_IMAGE
MYSQL_URL / MYSQL_USERNAME / MYSQL_PASSWORD
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
JWT_SECRET
SMS_PEPPER
SMS_MOCK=true
SMS_EXPOSE_CODE=false
```

真实值不进入 Git，也不出现在文档和日志中。

Dev 环境短信仍使用 mock：验证码只写应用日志，不返回接口响应；如需测试登录，从服务器日志中取验证码。

### 3.6 Nginx

配置文件：`/etc/nginx/sites-available/juejin.conf`

- 80 端口反向代理到 `127.0.0.1:8080`
- 转发 `X-Real-IP` / `X-Forwarded-For` / `X-Forwarded-Proto`
- `/actuator/` 仅允许本机访问，公网返回 403
- 已移除 Ubuntu 默认站点，避免 `default_server` 冲突

## 4. GitHub Actions

工作流：`.github/workflows/deploy-dev.yml`

触发条件：

- push 到 `dev`
- 手动 `workflow_dispatch`
- `docs/**` 和 `**.md` 的纯文档变更不触发部署
- 同一分支并发部署会取消旧任务

任务：

1. **verify**（GitHub Runner）
   - 检出代码
   - Java 21 + Maven 缓存
   - `mvn -B -ntp clean verify`
2. **deploy**（GitHub Runner）
   - 通过 SSH 连接 175
   - 执行 `/opt/juejin/deploy.sh "$DEPLOY_SHA"`

GitHub 仓库 secrets：

| Secret | 用途 |
| --- | --- |
| `DEV_HOST` | `49.233.89.175` |
| `DEV_USER` | `ubuntu` |
| `DEV_SSH_KEY` | 部署专用 ed25519 私钥 |

说明：

- 本机私钥：`~/.ssh/juejin_github_actions`，公钥已加入服务器 `ubuntu` 的 `authorized_keys`
- 之前为 TCR 创建的 `TCR_USERNAME` / `TCR_PASSWORD` secrets 仍在仓库中，但当前流程已不使用，可按需删除
- TCR 个人版资源（命名空间 `juejin-dev`、仓库 `juejin-backend`）仍保留，免费，暂未使用

## 5. deploy.sh 流程

```text
参数：commit SHA
1. git fetch --depth=1 origin <sha>，失败则 fetch dev 最近 50 个提交
2. git checkout -f <sha>
3. docker compose stop app（构建期间释放约 900MB 内存）
4. mvn -B -ntp clean package -DskipTests
5. docker build -t juejin-backend:local-<sha前12位> .
6. 更新 .env 的 APP_IMAGE
7. docker compose up -d（首次会启动 MySQL/Redis）
8. 每 2 秒检查 /actuator/health，最多 120 秒
9. 成功：记录 .current_tag，保留最近 3 个 local 镜像
10. 失败：输出 app 日志，切回上一个本地镜像
```

镜像 tag 使用 commit SHA，便于追溯和手动回滚。

## 6. 首次上线与验证结果

GitHub Actions **Run #3**：`completed success`

| 验证项 | 结果 |
| --- | --- |
| app / mysql / redis 容器 | 全部 healthy |
| 应用健康检查（本机） | HTTP 200，`status=UP` |
| Flyway | V1 自动建表成功 |
| 初始管理员 | 自动创建 `admin / admin123` |
| 公网管理员登录 | HTTP 200，返回 JWT |
| 公网发送短信验证码 | HTTP 200，mock 模式下不返回 debugCode |
| 从服务器日志取验证码完成 SMS 登录 | 200，`newUser=true`，自动注册成功 |
| SMS 用户访问 `/api/users` | HTTP 403，无权限 |
| 管理员访问 `/api/users` | HTTP 200，分页正常 |
| `/actuator/health` 经过 Nginx | HTTP 403，已禁止公网访问 |

## 7. 运维手册

SSH 登录：

```bash
ssh -i ~/.ssh/juejin_github_actions ubuntu@49.233.89.175
```

查看状态和日志：

```bash
cd /opt/juejin
docker compose ps
docker compose logs -f app
docker compose logs --tail=100 mysql
```

手动部署指定 commit：

```bash
cd /opt/juejin
./deploy.sh <git-sha>
```

回滚到上一个本地镜像：

```bash
cd /opt/juejin
docker images juejin-backend --format '{{.Tag}} {{.CreatedAt}}'
# 把 .env 中的 APP_IMAGE 改成目标 tag
docker compose up -d
```

重启应用：

```bash
cd /opt/juejin
docker compose restart app
```

进入 MySQL：

```bash
cd /opt/juejin
docker compose exec mysql mysql -uroot -p juejin
```

数据库备份建议（尚未配置定时任务）：

```bash
docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_PASSWORD" juejin \
  | gzip > /opt/juejin/backups/juejin-$(date +%F-%H%M).sql.gz
```

## 8. 成本

- **TCR 个人版免费**，没有创建任何企业版实例；仅保留了一个免费命名空间和仓库。
- GitHub Actions 使用公开仓库的免费额度。
- 175 服务器为已有资源，部署方案没有新增云产品。
- 当前没有产生额外费用。

TCR/GHCR 资源如果不再需要，可以在控制台清理；不影响当前 CI/CD。

## 9. 安全设计

- 应用密钥只在服务器 `.env`，权限 600，不进 Git。
- GitHub 只保存 SSH 部署凭据。
- MySQL/Redis 不映射公网端口。
- 应用端口只绑定 `127.0.0.1`，公网只能经 Nginx 访问。
- Actuator 仅允许本机访问。
- 部署使用专用 ed25519 密钥。
- 仓库为 public，源码可见；密钥和密码均不在仓库中。

## 10. 已知限制与后续优化

- **当前仅 HTTP**：JWT 和短信验证码走明文传输；正式对用户开放前需要域名 + HTTPS（Nginx + certbot）。
- **单机部署**：服务器故障即不可用。
- **构建期间会短暂停止应用**，dev 环境可接受；如需零停机可改蓝绿部署。
- **构建占用服务器内存**：通过构建前停止 app + swap 控制；不要在部署时同时跑其他重任务。
- **数据库尚未配置定时备份和监控**。
- **自动回滚逻辑已实现但尚未实际触发验证**。
- 如需更标准的 GitHub Actions 体验，可在 175 安装 self-hosted runner，让构建和部署都在本地执行。
- 后续可增加：外部 Uptime 监控、飞书通知、数据库备份到对象存储、HTTPS、生产环境手动审批。

## 11. 变更记录

| 时间 | 变更 |
| --- | --- |
| 2026-10-17 | 初版方案：GitHub Actions + GHCR + SSH |
| 2026-10-17 | Run #1：GHCR 跨境拉取过慢，deploy 超时 |
| 2026-10-17 | 开通腾讯云 TCR 个人版（免费），创建命名空间和仓库 |
| 2026-10-17 | Run #2：GitHub 海外 Runner 推送 TCR 过慢，放弃 |
| 2026-10-17 | 改为 175 本地构建，Run #3 部署成功 |
| 2026-10-17 | 补充 Nginx、健康检查、回滚和运维文档 |
