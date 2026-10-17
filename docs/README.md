# 设计文档索引

| 日期 | 文档 | 摘要 |
| --- | --- | --- |
| 2026-10-17 | [手机号验证码登录方案](./sms-login-design.md) | 手机号 + 验证码登录/自动注册、模拟短信、Redis 限流与一次性消费 |
| 2026-10-17 | [基础认证与权限设计](./auth-design.md) | Spring Boot + Shiro + JWT + Redis 的登录与鉴权基础方案 |
| 2026-10-17 | [Dev 环境 CI/CD 部署方案](./cicd-dev-deployment.md) | GitHub Actions + GHCR + SSH + Docker Compose，4C4G 单机部署与回滚 |

## 约定

- 每完成一个方案/功能，在 `docs/` 下新增一篇 Markdown 文档。
- 文档至少包含：背景与目标、方案概览、数据模型、接口设计、核心流程、安全/生产化设计、配置项、测试结果、已知限制与后续优化。
- 新增文档后同步更新本索引。
