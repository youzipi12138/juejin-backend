# juejin-backend

基于 **Spring Boot 3 + MyBatis-Plus + Apache Shiro + MySQL + Redis** 的后端基础项目。

内置 JWT 登录、Redis token 管理、Shiro 注解鉴权、用户 CRUD、分页、统一返回与全局异常处理。

## 技术栈

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Java | 21 | |
| Spring Boot | 3.5.16 | |
| MyBatis-Plus | 3.5.16 | 3.5.17 调整了包结构，这里使用 3.5.16 |
| Apache Shiro | 2.2.1 | Spring Boot 3 需要 `jakarta` classifier |
| MySQL | 8.4 | |
| Redis | 7 | token 存储 |
| JJWT | 0.12.6 | JWT 签发与校验 |
| Lombok | - | 简化实体/DTO |

## 目录结构

```
src/main/java/com/juejin
├── common/           # 统一返回 Result、业务异常、全局异常处理
├── config/           # Shiro、MyBatis-Plus、CORS、字段自动填充
├── controller/       # AuthController、UserController
├── entity/           # SysUser
├── init/             # 启动时创建初始管理员
├── mapper/           # SysUserMapper
├── model/            # dto / vo
├── security/         # JWT、Shiro Realm、JwtFilter、TokenStore、密码工具
└── service/          # 用户与登录业务
sql/schema.sql        # 建表脚本
docker-compose.yml    # MySQL + Redis（可选）
```

## 快速开始

### 1. 启动 MySQL 和 Redis

使用 Docker：

```bash
docker compose up -d
```

默认连接信息：

- MySQL：`localhost:3306`，数据库 `juejin`，账号 `root / root`
- Redis：`localhost:6379`，无密码

如果使用本机已有的 MySQL/Redis，请先执行建表脚本，并修改
`src/main/resources/application.yml` 中的连接配置：

```bash
mysql -uroot -p < sql/schema.sql
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

应用启动后会自动创建管理员账号：

- 用户名：`admin`
- 密码：`admin123`

> 首次登录后请立即修改密码。

### 3. 调用接口

登录：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

从响应中取出 `data.token`，后续请求携带：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/users
```

## 接口列表

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 匿名 | 注册（默认普通用户） |
| POST | `/api/auth/login` | 匿名 | 登录，返回 JWT |
| POST | `/api/auth/logout` | 登录 | 退出登录，删除 Redis token |
| GET | `/api/auth/me` | 登录 | 当前登录用户 |
| GET | `/api/users?current=1&size=10&username=` | `user:list` | 分页查询用户 |
| GET | `/api/users/{id}` | `user:read` | 用户详情 |
| POST | `/api/users` | `user:add` | 新增用户 |
| PUT | `/api/users/{id}` | `user:update` | 修改用户 |
| DELETE | `/api/users/{id}` | `user:delete` | 逻辑删除用户 |

权限规则在 `JwtRealm#doGetAuthorizationInfo` 中：

- `admin`：拥有 `*` 通配权限，可访问全部接口
- `user`：仅拥有 `user:read`

## 认证流程

1. `POST /api/auth/login` 校验用户名密码（SHA-256 + 随机盐 + 1024 次迭代）。
2. 签发 JWT，并将 token 写入 Redis：
   `juejin:login:token:{userId} = token`，TTL 与 JWT 有效期一致（默认 24 小时）。
3. 后续请求携带 `Authorization: Bearer <token>`。
4. `JwtFilter` 拦截 `/api/**`，交给 `JwtRealm`：
   - 校验 JWT 签名和过期时间；
   - 对比 Redis 中的 token（支持主动退出、修改密码后旧 token 失效）；
   - 加载用户并交给 Shiro 完成认证。
5. Controller 上的 `@RequiresPermissions` 触发 Shiro 授权检查。

Session 已关闭（`StatelessSubjectFactory`），完全基于 token，多实例部署时共享 Redis 即可。

## 配置说明

`application.yml` 支持通过环境变量覆盖连接信息：

| 环境变量 | 默认值 |
| --- | --- |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/juejin?...` |
| `MYSQL_USERNAME` | `root` |
| `MYSQL_PASSWORD` | `root` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | 空 |
| `JWT_SECRET` | 内置开发密钥 |

JWT 相关配置：

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:...}   # 生产环境务必通过环境变量注入，长度 >= 32 字节
    expire-seconds: 86400       # token 有效期，同时作为 Redis TTL
```

## 生产环境建议

- 将 JWT 密钥、数据库密码放入环境变量或配置中心，不要提交到仓库。
- 密码哈希可升级为 BCrypt/Argon2（Shiro 2.x 已内置相关实现）。
- 当前角色权限为硬编码示例，可扩展 `sys_role`、`sys_permission`、`sys_user_role` 等表。
- 收紧 CORS 白名单。
- 按需为登录接口增加限流、验证码、登录失败锁定等防护。
