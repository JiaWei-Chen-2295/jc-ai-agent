# StudyFriend 联网搜索前端对接文档

> 适用范围：StudyFriend 聊天接口首版 FireCrawl MCP 联网搜索能力
> 更新时间：2026-04-16

## 1. 目的

本文用于前端对接 StudyFriend 聊天接口的“可选联网搜索”能力，重点解决下面几个问题：

- 如何开启或关闭联网搜索
- 同步接口如何拿到回答正文和来源链接
- SSE 接口如何同时拿到来源链接和文本增量
- 前端应该使用哪些接口，不应该使用哪些接口

---

## 2. 接口范围

服务基础路径：

- `http://{host}:{port}/api`

控制器路径：

- `/ai_friend`

本次前端需要重点对接的接口：

1. 创建会话  
`POST /api/ai_friend/session`

2. 同步聊天（返回完整回答 + 来源）  
`GET /api/ai_friend/do_chat/async`

3. SSE 流式聊天（返回 sources 事件 + 文本增量）  
`GET /api/ai_friend/do_chat/sse/emitter`

4. SSE 流式聊天（tool 入口，当前也支持联网搜索开关）  
`GET /api/ai_friend/do_chat/sse_with_tool/emitter`

---

## 3. 前置条件

### 3.1 登录与会话

后端使用会话态鉴权，前端必须携带 Cookie：

- `fetch` 使用 `credentials: 'include'`
- `EventSource` 需要使用 `withCredentials: true` 或同域访问

### 3.2 当前租户

StudyFriend 接口依赖当前激活租户，后端从 Session 中读取 `active_tenant_id`。

如果未登录或未选择租户，常见返回为：

- `40100` 未登录
- `40101` 无权限或未选择租户

---

## 4. 联网搜索开关规则

所有相关聊天接口都支持请求参数：

- `webSearchEnabled=true|false`

规则如下：

1. 前端显式传值时，以请求参数为准
2. 前端不传时，后端回落到全局配置 `jc-ai-agent.web-search.enabled`

建议前端策略：

- 默认不传，由产品配置决定默认行为
- 当用户主动勾选“联网搜索”时，传 `webSearchEnabled=true`
- 当用户主动关闭时，传 `webSearchEnabled=false`

注意：

- 即使传了 `webSearchEnabled=true`，如果后端未配置有效 FireCrawl 凭据，或搜索未命中结果，最终也可能返回 `webSearchUsed=false`

---

## 5. 会话接口

### 5.1 创建会话

`POST /api/ai_friend/session`

请求参数：

- `title`：可选，会话标题
- `modelId`：可选，模型 ID；不传则使用后端默认模型

示例：

```http
POST /api/ai_friend/session?title=Spring%20AI%20问答&modelId=qwen3-max
```

响应：`BaseResponse<ChatSessionVO>`

前端至少需要保存：

- `chatId`
- `modelId`

---

## 6. 同步聊天接口

### 6.1 接口定义

`GET /api/ai_friend/do_chat/async`

请求参数：

- `chatMessage`：必填，用户消息
- `chatId`：必填，会话 ID
- `messageId`：可选，前端消息 ID
- `webSearchEnabled`：可选，是否开启联网搜索

示例：

```http
GET /api/ai_friend/do_chat/async?chatId=session-001&chatMessage=Spring%20AI%20MCP%20怎么接入&messageId=msg-001&webSearchEnabled=true
```

### 6.2 响应结构

统一返回 `BaseResponse<StudyFriendChatResult>`：

```json
{
  "code": 0,
  "data": {
    "content": "Spring AI 1.1.2 可以通过 streamable-http 或 stdio 接入 MCP，本项目当前使用的是 FireCrawl MCP 远端地址。",
    "webSearchUsed": true,
    "sources": [
      {
        "title": "Spring AI MCP Client Docs",
        "url": "https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html",
        "snippet": "Spring AI MCP client supports streamable-http connections for remote MCP servers."
      },
      {
        "title": "Firecrawl MCP Server",
        "url": "https://docs.firecrawl.dev/mcp-server",
        "snippet": "Firecrawl MCP Server provides search, scrape, map and crawl capabilities over MCP."
      }
    ]
  },
  "message": "ok"
}
```

### 6.3 类型定义

```ts
export interface BaseResponse<T> {
  code: number;
  data: T;
  message: string;
}

export interface StudyFriendSource {
  title: string;
  url: string;
  snippet: string;
}

export interface StudyFriendChatResult {
  content: string;
  webSearchUsed: boolean;
  sources: StudyFriendSource[];
}
```

### 6.4 前端使用建议

1. 先显示 `content`
2. 当 `webSearchUsed === true && sources.length > 0` 时，在回答下方展示“来源”区域
3. `sources` 中的 `url` 直接渲染为可点击链接
4. 不要从 `content` 里再做正则提取链接，链接以 `sources` 为准

---

## 7. SSE 流式聊天接口

### 7.1 推荐使用的两个 SSE 接口

1. `GET /api/ai_friend/do_chat/sse/emitter`
2. `GET /api/ai_friend/do_chat/sse_with_tool/emitter`

两者当前对前端的核心差异不大，都支持：

- `webSearchEnabled` 参数
- 首先发送 `sources` 事件
- 然后发送默认 `message` 文本增量

请求参数：

- `chatMessage`：必填
- `chatId`：必填
- `messageId`：可选
- `webSearchEnabled`：可选

示例：

```http
GET /api/ai_friend/do_chat/sse/emitter?chatId=session-001&chatMessage=最新的%20Spring%20AI%20MCP%20文档&webSearchEnabled=true
```

### 7.2 事件格式说明

#### 事件一：`sources`

后端会先发一个命名事件：

```text
event: sources
data: {"webSearchUsed":true,"sources":[{"title":"...","url":"...","snippet":"..."}]}
```

类型定义：

```ts
export interface StudyFriendSourcePayload {
  webSearchUsed: boolean;
  sources: StudyFriendSource[];
}
```

处理规则：

1. 收到 `sources` 事件后，先更新当前回答卡片的来源区域
2. 如果 `webSearchUsed=false` 或 `sources=[]`，前端可以隐藏来源模块

#### 事件二：默认 `message`

正文增量通过默认消息事件返回，没有显式事件名：

```text
data: Spring AI

data: 可以通过

data: MCP client
```

前端应在 `onmessage` 中逐段拼接文本。

### 7.3 浏览器原生 EventSource 示例

```ts
const query = new URLSearchParams({
  chatId: "session-001",
  chatMessage: "Spring AI MCP 怎么接入",
  webSearchEnabled: "true"
});

const es = new EventSource(`/api/ai_friend/do_chat/sse/emitter?${query.toString()}`, {
  withCredentials: true
});

let text = "";
let sources: StudyFriendSource[] = [];

es.addEventListener("sources", (event) => {
  const payload = JSON.parse((event as MessageEvent).data) as StudyFriendSourcePayload;
  sources = payload.sources ?? [];
  // 更新来源 UI
});

es.onmessage = (event) => {
  text += event.data;
  // 更新增量文本 UI
};

es.onerror = () => {
  es.close();
};
```

### 7.4 使用 `fetch-event-source` 示例

如果前端框架对 EventSource 封装较弱，推荐使用 `@microsoft/fetch-event-source`：

```ts
import { fetchEventSource } from "@microsoft/fetch-event-source";

let text = "";
let sources: StudyFriendSource[] = [];

await fetchEventSource("/api/ai_friend/do_chat/sse/emitter?chatId=session-001&chatMessage=Spring%20AI%20MCP%20怎么接入&webSearchEnabled=true", {
  method: "GET",
  credentials: "include",
  onmessage(event) {
    if (event.event === "sources") {
      const payload = JSON.parse(event.data) as StudyFriendSourcePayload;
      sources = payload.sources ?? [];
      return;
    }
    text += event.data;
  }
});
```

---

## 8. 哪些接口当前不建议前端用来展示来源

以下两个接口当前仍然返回的是 `DisplayEvent JSON`，没有接入本次 `sources` 结构化来源事件：

1. `GET /api/ai_friend/do_chat/sse/agent/emitter`
2. `GET /api/ai_friend/do_chat/sse_with_tool/agent/emitter`

如果前端当前目标是展示“联网搜索来源链接”，请优先使用：

- `/api/ai_friend/do_chat/async`
- `/api/ai_friend/do_chat/sse/emitter`
- `/api/ai_friend/do_chat/sse_with_tool/emitter`

---

## 9. 前端页面建议

推荐一个回答卡片拆成三部分：

1. 用户问题
2. AI 回答正文
3. 来源区域

来源区域建议字段：

- 标题：`source.title`
- 链接：`source.url`
- 摘要：`source.snippet`

建议交互：

- `webSearchUsed=true` 时，在回答顶部显示“已联网搜索”标签
- `sources.length > 0` 时显示“查看来源”折叠区
- 点击链接新窗口打开

---

## 10. 常见失败与边界情况

### 10.1 联网搜索已开启，但没有来源

可能出现：

```json
{
  "content": "...",
  "webSearchUsed": false,
  "sources": []
}
```

这通常表示：

1. 后端没有配置 FireCrawl 凭据
2. FireCrawl MCP 查询失败
3. 搜索无有效结果

前端处理建议：

- 正常显示回答正文
- 不展示来源区域
- 不要把它当成接口失败

### 10.2 SSE 先收到 sources，再收到正文

这是正常行为。前端应：

1. 先缓存来源
2. 再持续拼接正文

### 10.3 SSE 中断

当前接口结束时会自然关闭连接。若发生异常中断：

- 保留当前已收到的文本
- 保留已收到的来源
- 提示“流式输出中断，请重试”

---

## 11. 最小对接清单

前端至少完成以下 5 项：

1. 创建会话并保存 `chatId`
2. 调同步接口时解析 `StudyFriendChatResult`
3. 调 SSE 接口时处理 `sources` 事件
4. 调 SSE 接口时在 `onmessage` 中拼接正文文本
5. 所有请求携带 Cookie

---

## 12. 建议联调顺序

1. 先用 `/api/ai_friend/do_chat/async` 验证 `sources` 数据结构
2. 再接 `/api/ai_friend/do_chat/sse/emitter` 验证 `sources` 事件 + 文本增量
3. 最后根据业务需要决定是否切到 `/api/ai_friend/do_chat/sse_with_tool/emitter`

---

## 13. 备注

- Swagger 地址：`/api/swagger-ui.html`
- OpenAPI 地址：`/api/v3/api-docs`
- 本文档描述的是当前已实现行为，不代表后续 AgentEvent SSE 接口也已支持来源事件

---

## 14. 历史消息兼容改造说明

为兼容“消息生成完成后，前端重新打开会话仍能看到联网来源”，后端已经将联网来源写入现有消息记录的扩展信息中，并在历史消息接口中透出为**可选字段**。

这个方案有两个特点：

1. **不修改库表**：复用现有 `chat_message.metadata`
2. **兼容旧前端**：旧前端即使不识别新字段，也会直接忽略，不影响现有渲染

### 14.1 历史消息接口

前端历史消息通常通过下面接口获取：

- `GET /api/ai_friend/session/{chatId}/messages`
- `GET /api/ai_friend/admin/session/{chatId}/messages`

返回结构：`BaseResponse<ChatMessageListResponse>`

其中 `records` 数组里的每一项为 `ChatMessageVO`。

### 14.2 ChatMessageVO 新增可选字段

当前历史消息项结构建议前端按下面定义解析：

```ts
export interface ChatMessageVO {
  id: number;
  role: string;
  content: string;
  createdAt: string;

  // 新增可选字段，旧消息或旧后端可能不存在
  webSearchUsed?: boolean | null;
  sources?: StudyFriendSource[] | null;
}
```

对应字段说明：

- `webSearchUsed`
  - 含义：这条 assistant 消息在生成时是否实际使用了联网搜索
  - 类型：`boolean`
  - 是否必有：否

- `sources`
  - 含义：这条 assistant 消息关联的联网来源列表
  - 类型：`StudyFriendSource[]`
  - 是否必有：否

### 14.3 历史消息响应示例

```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 101,
        "role": "user",
        "content": "联网搜索一下 Spring AI MCP 最新文档",
        "createdAt": "2026-04-16T21:30:00"
      },
      {
        "id": 102,
        "role": "assistant",
        "content": "Spring AI 1.1.2 可以通过 MCP client 接入远端 MCP 服务。",
        "createdAt": "2026-04-16T21:30:03",
        "webSearchUsed": true,
        "sources": [
          {
            "title": "Spring AI MCP Client Docs",
            "url": "https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html",
            "snippet": "Spring AI MCP client supports streamable-http connections for remote MCP servers."
          }
        ]
      }
    ],
    "hasMore": false,
    "nextBeforeId": null
  },
  "message": "ok"
}
```

### 14.4 前端解析建议

推荐按下面顺序兼容处理：

1. 先按老逻辑解析 `id / role / content / createdAt`
2. 如果消息是 `assistant`，再尝试读取 `webSearchUsed` 和 `sources`
3. 若字段不存在、为 `null` 或空数组，直接按普通消息展示
4. 若 `webSearchUsed === true && sources.length > 0`，则展示“来源”区域

可直接参考下面的前端示例：

```ts
function resolveMessageSources(message: ChatMessageVO): StudyFriendSource[] {
  if (message.role !== "assistant") {
    return [];
  }
  if (!message.webSearchUsed) {
    return [];
  }
  return message.sources ?? [];
}
```

### 14.5 与实时接口的关系

前端可以统一按下面规则处理：

1. **实时生成阶段**
   - 同步接口：读取 `StudyFriendChatResult.sources`
   - SSE 接口：读取 `sources` 事件

2. **历史回放阶段**
   - 读取 `ChatMessageVO.sources`

也就是说：

- 实时接口负责“本次生成时立即展示来源”
- 历史消息接口负责“刷新页面后仍然恢复来源”

### 14.6 兼容性结论

这个改造对旧前端是安全的：

- 旧前端不识别新增字段时，会直接忽略
- 老消息没有 `webSearchUsed` / `sources` 时，也不会报错
- 新前端只要把这两个字段当作**可选字段**处理即可