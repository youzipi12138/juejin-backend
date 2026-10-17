# iOS 前端接入 Dev 后端方案

> 状态：已实施并完成模拟器联调
> 日期：2026-10-17
> 前端项目：`/Users/mixianyun/JuejinClone`（SwiftUI，iOS 18+）
> 后端环境：http://49.233.89.175（175 dev 服务器）

## 1. 目标与范围

把 JuejinClone iOS 客户端的登录流程接到 dev 后端的手机号验证码登录接口：

- 发送短信验证码；
- 手机号 + 验证码登录，未注册自动注册；
- 保存 JWT，后续请求带 `Authorization: Bearer <token>`；
- 启动时用 `/api/auth/me` 恢复登录态；
- 首页/个人页展示登录用户；
- 支持退出登录。

首页文章流 `/api/v1/feed/*` 后端尚未实现，本次不接入，继续使用 `SampleData` 回落。

## 2. 后端地址与 ATS

dev 后端：`http://49.233.89.175`（Nginx 80 端口反向代理到应用）。

前端配置：

```text
Configs/Debug.xcconfig
  ENV_NAME = dev
  INFOPLIST_FILE = Configs/Info-Debug.plist
  API_BASE_URL = http:/$()/49.233.89.175
```

- xcconfig 中 `//` 必须写成 `/$()/`，否则会被当成注释截断。
- dev 是公网 HTTP IP，iOS ATS 默认会拦截，因此新增 `Configs/Info-Debug.plist`，
  只在 Debug 构建开启 `NSAllowsArbitraryLoads`。
- Release 继续使用 `Configs/Info.plist`，保持 HTTPS/本地网络限制，不把放宽配置带进发布包。

## 3. 接口映射

| 前端动作 | 后端接口 | 说明 |
| --- | --- | --- |
| 获取验证码 | `POST /api/auth/sms/send` | body `{ "phone": "139..." }` |
| 登录 / 注册 | `POST /api/auth/sms/login` | body `{ "phone": "...", "code": "..." }`，返回 token + user + newUser |
| 恢复登录态 | `GET /api/auth/me` | `Authorization: Bearer <token>` |
| 退出登录 | `POST /api/auth/logout` | `Authorization: Bearer <token>` |

后端统一响应：

```json
{ "code": 200, "message": "登录成功", "data": { } }
```

前端新增 `APIEnvelope<T>` 和 `APIErrorBody`，由 `APIClient` 统一解包；
4xx/5xx 优先展示后端返回的 `message`。

## 4. 前端改动

| 文件 | 改动 |
| --- | --- |
| `Configs/Debug.xcconfig` | dev 地址改为 `49.233.89.175`，指向 Info-Debug.plist |
| `Configs/Info-Debug.plist` | Debug 专用 ATS 配置 |
| `Models/Auth.swift` | AuthUser / SMSCodeResponse / LoginResponse |
| `Core/Networking/APIEnvelope.swift` | 后端统一返回结构 |
| `Core/Networking/APIError.swift` | 增加 `.server(code, message)` |
| `Core/Networking/APIClient.swift` | 新增 POST、Bearer token、统一解包 |
| `Core/Networking/AuthService.swift` | 登录相关接口封装 |
| `Core/Session/AuthStore.swift` | 登录态、token 持久化、启动恢复、退出 |
| `Features/Login/LoginView.swift` | 验证码输入、发送、登录、冷却倒计时、错误提示 |
| `Features/Home/HomeView.swift` | 登录后展示用户欢迎卡片 |
| `Features/Profile/ProfileView.swift` | 登录后展示后端昵称，增加退出登录 |
| `App/JuejinCloneApp.swift` | 注入 AuthStore，启动恢复会话 |
| `Navigation/MainTabView.swift` | 支持 `-OpenLogin YES` 联调启动参数 |

## 5. 登录流程

```text
输入手机号 + 勾选协议
  → 获取短信验证码
      → 后端 mock 发送，验证码写入服务器 app 日志
  → 从服务器日志取 6 位验证码并填入
  → 点击「登录 / 注册」
      → POST /api/auth/sms/login
      → 保存 token + user（UserDefaults）
      → 关闭登录页，首页显示「Hi，昵称」
  → 下次启动 GET /api/auth/me 恢复登录态
  → 个人页「退出登录」调用 /api/auth/logout 并清理本地状态
```

## 6. 验证码获取（安全说明）

dev 后端保持 `SMS_EXPOSE_CODE=false`，**不会通过公网接口返回验证码**，避免任何人用手机号直接登录。

联调时从服务器 app 日志取：

```bash
ssh -i ~/.ssh/juejin_github_actions ubuntu@49.233.89.175 \
  "docker compose -f /opt/juejin/compose.yml logs --tail=200 app | grep 模拟短信 | tail -1"
```

前端也兼容 `debugCode`：如果后端本地启动并显式开启 `SMS_EXPOSE_CODE=true`，
登录页会自动填入验证码并提示。公网 dev 环境不要开启。

## 7. 联调验证结果

- `xcodebuild` Debug 模拟器构建成功；
- 构建产物 Info.plist：`API_BASE_URL=http://49.233.89.175`、`ENV_NAME=dev`；
- 登录页 UI 正常展示手机号、验证码、协议、登录按钮和开发环境提示；
- 使用后端返回的 mock 验证码完成登录，首页显示「Hi，用户9001 / 已登录」；
- 杀掉 App 重新启动后，通过本地 token 调用 `/api/auth/me` 恢复登录态；
- 个人页显示后端昵称「用户9001」，并可滚动到「退出登录」。

联调启动参数：

```bash
# 直接打开登录页
xcrun simctl launch booted com.pengchen.JuejinClone -OpenLogin YES

# 自动登录（仅 Debug，验证码从服务器日志取）
xcrun simctl launch booted com.pengchen.JuejinClone \
  -DevLoginPhone 13900139001 -DevLoginCode 123456
```

## 8. 已知限制与后续

- token 目前存 `UserDefaults`，上线前应迁移到 Keychain。
- dev 后端是 HTTP，只适合本地联调；正式环境需要 HTTPS。
- 启动恢复登录态时任何错误都会清本地 token；弱网下可能表现为“自动退出”，
  后续可区分 401 与网络错误。
- 没有 refresh token；token 过期（24h）后需重新登录。
- 首页 feed 接口未实现，仍用 SampleData。
- 个人页等级、点赞/收藏等统计暂无后端字段，登录后显示为 0 / LV.1 占位。
- Debug 的 ATS 放宽只存在于 Info-Debug.plist，Release 不受影响。

## 9. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-10-17 | 接入 dev 后端手机号验证码登录、token 持久化、会话恢复与退出 |
