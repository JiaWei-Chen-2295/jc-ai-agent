# 多模型聊天支持 — 前端实现规划

> 范围：业务逻辑 & 功能边界，不含 UI 细节。

---

## 1. 核心约束（来自后端设计）

| 约束 | 说明 |
|---|---|
| **模型绑定时机** | 模型在 **创建会话时** 通过 `modelId` 参数绑定，绑定之后不可更改 |
| **模型不跟消息走** | 后续所有发送消息的接口无需传 `modelId`，后端从会话记录中读取 |
| **默认模型** | `modelId` 不传或传 `null` 时，后端自动绑定 `qwen3-max` |
| **可用模型来源** | 前端只展示后端 `GET /ai/models` 返回的启用模型，不做本地硬编码 |

---

## 2. 接口契约

### 2.1 获取可用模型列表

```
GET /ai/models
Authorization: 无需（公开接口）

Response 200:
[
  {
    "id": 1,
    "provider": "dashscope",
    "modelId": "qwen3-max",
    "displayName": "通义千问3 Max",
    "description": "...",
    "iconUrl": "...",
    "sortOrder": 1,
    "enabled": true
    // 无 modelName / baseUrl / apiKeyMasked（管理员字段）
  },
  ...
]
```

**调用时机**：应用初始化阶段（或首次打开聊天页时）拉取并缓存，直到用户刷新页面。

---

### 2.2 创建会话

```
POST /ai_friend/session?title=xxx&modelId=qwen3-max
Authorization: 登录用户

Response 200:
{
  "chatId": "uuid",
  "title": "xxx",
  "modelId": "qwen3-max",
  "modelDisplayName": "通义千问3 Max",
  "createdAt": "...",
  "lastMessageAt": null
}
```

**业务规则**：
- `modelId` 来自步骤 2.1 返回列表中某条记录的 `modelId` 字段
- `title` 可选，不传时后端不报错（后端设置为 null，前端可用"新对话"占位）
- 创建成功后，`chatId` 是后续所有操作的唯一标识

---

### 2.3 会话列表

```
GET /ai_friend/session/list?limit=10&beforeLastMessageAt=...&beforeChatId=...
Authorization: 登录用户

Response 200:
{
  "sessions": [
    {
      "chatId": "...",
      "title": "...",
      "modelId": "qwen3-max",
      "modelDisplayName": "通义千问3 Max",
      "lastMessageAt": "...",
      "createdAt": "..."
    }
  ],
  "hasMore": true
}
```

**说明**：每条会话已包含 `modelId` + `modelDisplayName`，前端直接展示，无需二次查询。

---

### 2.4 发送消息（SSE 流式）

```
GET /ai_friend/chat/sse?chatId=xxx&chatMessage=xxx&enableTool=false
Authorization: 登录用户

Response: text/event-stream
data: 部分回复文字\n\n
data: [DONE]\n\n
```

**说明**：无需传 `modelId`，后端根据会话绑定的模型自动路由。

---

### 2.5 发送消息（轮询模式）

```
POST /ai_friend/chat?chatId=xxx&chatMessage=xxx
Authorization: 登录用户

Response 200: { "data": "完整回复文字" }
```

同上，无需传 `modelId`。

---

## 3. 前端状态管理边界

### 3.1 全局状态（应用生命周期）

| 状态 | 来源 | 更新时机 |
|---|---|---|
| `availableModels: AiModelVO[]` | `GET /ai/models` | 应用启动时拉取一次；管理员在后台变更模型后，需重新拉取（或刷新页面） |
| `selectedModelId: string` | 用户选择或默认值 | 用户在"新建会话"动作前选择；选择结果仅用于下一次创建会话，不影响已有会话 |

> **`selectedModelId` 的生命周期**：它是一个「待创建会话」的预选项，不属于任何具体会话。一旦调用创建会话接口，`selectedModelId` 的值随 `chatId` 固化到服务端，前端之后对该会话只读 `session.modelId`，不再使用全局 `selectedModelId`。

---

### 3.2 会话级状态

| 状态 | 来源 | 说明 |
|---|---|---|
| `session.modelId` | 创建响应 / 列表接口 | 只读，绑定后不可变 |
| `session.modelDisplayName` | 同上 | 用于展示，不可变 |
| `session.chatId` | 创建响应 | 所有消息操作的主键 |

---

## 4. 功能边界

### 4.1 模型选择

- **选择入口**：仅在「新建会话」流程中提供
- **已有会话**：只展示当前绑定的模型名称，**不提供切换入口**
- **可用列表**：只展示 `enabled: true` 的模型（后端已过滤，前端无需再过滤）
- **默认选中**：列表中 `sortOrder` 最小的一项（即第一项）

### 4.2 会话创建流程（关键顺序）

```
用户点击"新建会话"
  → 前端读取 selectedModelId（或取列表第一项）
  → POST /ai_friend/session?modelId={selectedModelId}&title={title}
  → 后端返回 ChatSessionVO（含 modelId + modelDisplayName）
  → 前端将新会话插入列表头部
  → 跳转/激活该会话
  → 后续消息只使用 session.chatId，不再关心 modelId
```

### 4.3 消息发送

- 发送前必须确认 `chatId` 存在（即会话已创建）
- SSE 断线重连：前端负责在 `onerror` / `onclose` 事件时决策是否重连，后端不感知
- `[DONE]` token 表示服务端流结束，前端关闭 EventSource

### 4.4 错误边界

| 场景 | 后端响应 | 前端处理 |
|---|---|---|
| `modelId` 对应模型不存在或已禁用 | 创建会话时 `400` 或路由时抛出 `BusinessException` | 提示"当前模型不可用，请选择其他模型"，清空 `selectedModelId` 并重新拉取列表 |
| API Key 无效（第三方服务报错） | SSE 流中错误消息 或 `500` | 展示后端返回的错误文本，不做特殊重试 |
| 用户未登录 | `401` | 全局拦截，跳转登录页 |

---

## 5. 管理员模块

> 仅 `userRole === "admin"` 的用户可见。

### 5.1 接口汇总

| 操作 | 接口 | 说明 |
|---|---|---|
| 查看全部模型（含禁用） | `GET /ai/admin/models` | 返回额外字段：`modelName`, `baseUrl`, `apiKeyMasked` |
| 新增模型 | `POST /ai/admin/models` | Body: `AiModelConfigRequest` |
| 更新模型 | `PUT /ai/admin/models/{id}` | `apiKeyPlain` 留空表示不修改 API Key |
| 启用/禁用 | `PATCH /ai/admin/models/{id}/toggle` | 返回新的 `enabled` 状态 |
| 删除 | `DELETE /ai/admin/models/{id}` | 删除后后端自动驱逐缓存 |

### 5.2 字段规则

```typescript
interface AiModelConfigRequest {
  provider: string;       // "dashscope" | "openai" | "deepseek" | "moonshot" | "zhipu"
  modelId: string;        // 业务唯一键，创建后建议不修改
  modelName: string;      // 传给 API 的实际模型名，例如 "deepseek-chat"
  displayName: string;    // 展示给用户的名称
  baseUrl?: string;       // dashscope 可不填；其他提供商必填
  apiKeyPlain?: string;   // 创建时必填（dashscope 可传空）；更新时留空=不修改
  maxTokens?: number;
  temperature?: number;
  description?: string;
  iconUrl?: string;
  enabled?: boolean;
  sortOrder?: number;
}
```

### 5.3 管理员操作对用户侧的影响

| 管理员操作 | 用户侧影响 |
|---|---|
| 禁用某模型 | 该模型从 `GET /ai/models` 列表中消失，已有使用该模型的会话**仍可正常聊天**（后端缓存驱逐后按原配置重建） |
| 删除某模型 | 已有会话发送消息时后端会抛出"模型不存在"错误；前端对此无预防手段，属于管理员责任 |
| 更新 API Key | 后端自动驱逐缓存，下次请求透明重建，用户无感知 |
| 新增模型 | 用户需刷新页面（或前端定时重拉列表）才能看到新模型 |

---

## 6. 数据流图（文字版）

```
应用启动
  └─ GET /ai/models ──────────────────────── availableModels (cache)

新建会话
  ├─ 读 availableModels 供选择
  └─ POST /ai_friend/session?modelId=...
       └─ 返回 ChatSessionVO {chatId, modelId, modelDisplayName}
            └─ 存入会话列表

打开已有会话
  └─ GET /ai_friend/session/list
       └─ 返回 ChatSessionVO[] （含 modelId 字段，直接展示）

发消息
  └─ GET/POST /ai_friend/chat(.../sse)?chatId=xxx&chatMessage=xxx
       （无需 modelId，后端自动路由）

管理员
  └─ GET /ai/admin/models → 查看/修改 → POST/PUT/PATCH/DELETE /ai/admin/models
       └─ 变更生效后用户需刷新拉取最新模型列表
```

---

## 7. 不在前端实现范围内的内容

- API Key 的加解密（由后端 `ApiKeyEncryptor` 完全负责）
- 模型路由逻辑（由后端 `ChatModelRegistry` 负责）
- 会话与模型的历史记录绑定（DB 层 `chat_session.model_id` 字段，前端只读）
- 多租户鉴权（由后端中间件处理，前端仅需携带登录态 Cookie/Token）
