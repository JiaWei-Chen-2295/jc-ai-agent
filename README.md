# AI 智能体练手项目


## 用户与租户管理变更
- 每个用户默认拥有一个“个人团队”（personal tenant），登录/注册时自动创建并写入 session 的 `active_tenant_id`。
- 用户可创建有限数量团队（默认 5，可在 `jc-ai-agent.tenant.max-create-teams` 配置）。
- 用户可加入无限数量团队（team tenant）。
- 团队管理员默认是创建者，支持管理员转让；管理员退出团队时按加入顺序自动转让。
- 若管理员退出且没有其他成员，团队会被标记删除。
- 当前租户通过 session 的 `active_tenant_id` 生效，后端会校验成员/管理员权限。

团队接口（Swagger 中可见）：
- `POST /api/tenant/create` 创建团队
- `GET /api/tenant/list` 查询我加入的团队
- `POST /api/tenant/join` 加入团队
- `POST /api/tenant/leave` 退出团队
- `POST /api/tenant/transfer-admin` 转让管理员
- `POST /api/tenant/active` 切换当前团队

## 文档与 AI 请求变化
- 向量库 `study_friends` 增加 `tenant_id` 字段，检索/写入/删除均强制带上当前租户。
- 文档表 `study_friend_document` 增加 `tenant_id` 与 `owner_user_id`，文档上传路径包含租户信息。
- 文档接口权限规则：
  - 成员可上传/查看/列表
  - 团队管理员可删除/重索引
  - 系统管理员可跨团队操作

## Swagger
- Swagger UI: `http://localhost:8525/api/swagger-ui.html`
- OpenAPI: `http://localhost:8525/api/v3/api-docs`

## 用户头像（OSS 前端直传）
- 设计与对接说明：`src/main/resources/doc/用户头像-OSS前端直传.md`

## 前端 Prompt（基于 Swagger）
请使用以下提示词生成前端需求/页面（基于 Swagger）：

```
你是前端工程师，请基于 Swagger/OpenAPI 自动对接后端接口，完成多租户 AI 文档系统前端。
要求：
1) 登录后读取 session cookie；所有请求保持同域 cookie（credentials: include）。
2) 读取 OpenAPI（/api/v3/api-docs）生成/对接接口模型。
3) 必须提供“当前团队”切换入口，调用 /api/tenant/active；默认选个人团队。
4) 团队管理页：创建团队、加入团队、退出团队、管理员转让（仅管理员可见）。
5) 文档管理页：上传、列表、查看状态、删除、重索引。删除/重索引仅管理员可点击。
6) AI 聊天页：对接 /api/ai_friend/do_chat/async 与 SSE 接口（/api/ai_friend/do_chat/sse/emitter），支持流式展示。
7) 页面有基础的登录状态、错误提示、权限提示与 loading 状态。
8) Base URL 可配置（默认 http://localhost:8525/api）。
9) UI 结构清晰，移动端可用。
```
