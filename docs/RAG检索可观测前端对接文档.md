# RAG 检索可观测前端对接文档

## 1. 目的

用于前端展示每次 RAG 检索过程，回答下面问题：

- 本次 query 分别从 `Vector` / `ES` 召回了什么
- 最终 `RRF` 融合结果是什么
- 每个阶段耗时多少
- 是否发生了降级（只走向量）

---

## 2. 接口总览

服务基础路径：

- `http://{host}:{port}/api`

控制器路径：

- `/v1/agent/observability`

可用接口：

1. 查询最近轨迹列表  
`GET /api/v1/agent/observability/rag/latest?limit=20`

2. 查询单条轨迹详情  
`GET /api/v1/agent/observability/rag/{traceId}`

---

## 3. 前置条件（必须）

1. 用户已登录（后端使用会话态，需携带 Cookie）
2. 用户已选择激活租户（后端从 Session 读取 activeTenantId）

如果未满足条件，会返回：

- `code=40100` 未登录
- `code=40101` 无权限/未选择租户

---

## 4. 响应结构

所有接口统一 `BaseResponse<T>`：

```json
{
  "code": 0,
  "data": {},
  "message": "ok"
}
```

- `code=0` 表示成功
- 非 0 表示失败

---

## 5. 接口详情

### 5.1 查询最近轨迹

`GET /api/v1/agent/observability/rag/latest?limit=20`

请求参数：

- `limit`：返回数量，默认 20，最大建议 200

返回 `data` 类型：`RagRetrievalTrace[]`

示例：

```json
{
  "code": 0,
  "data": [
    {
      "traceId": "1a2b3c...",
      "query": "什么是二叉树前序遍历",
      "tenantId": 1,
      "topK": 5,
      "hybridEnabled": true,
      "rrfK": 60,
      "degradedToVectorOnly": false,
      "degradeReason": null,
      "vectorLatencyMs": 42,
      "esLatencyMs": 18,
      "mergeLatencyMs": 1,
      "totalLatencyMs": 45,
      "vectorDocs": [],
      "esDocs": [],
      "mergedDocs": [],
      "createdAt": "2026-03-16T21:15:08"
    }
  ],
  "message": "ok"
}
```

### 5.2 查询轨迹详情

`GET /api/v1/agent/observability/rag/{traceId}`

路径参数：

- `traceId`：轨迹 ID（从 latest 接口返回中获取）

返回 `data` 类型：`RagRetrievalTrace`

---

## 6. 字段定义（前端可直接建类型）

```ts
export interface BaseResponse<T> {
  code: number;
  data: T;
  message: string;
}

export interface RagTraceDocView {
  id: string;             // chunk id
  score: number | null;   // 当前阶段分数
  documentId: string | null;
  source: "vector" | "es" | "rrf";
  snippet: string;        // 文本片段（已截断）
}

export interface RagRetrievalTrace {
  traceId: string;
  query: string;
  tenantId: number;
  topK: number;
  hybridEnabled: boolean;
  rrfK: number;
  degradedToVectorOnly: boolean;
  degradeReason: string | null;
  vectorLatencyMs: number | null;
  esLatencyMs: number | null;
  mergeLatencyMs: number | null;
  totalLatencyMs: number | null;
  vectorDocs: RagTraceDocView[];
  esDocs: RagTraceDocView[];
  mergedDocs: RagTraceDocView[];
  createdAt: string;      // LocalDateTime 字符串
}
```

---

## 7. 调用示例（fetch）

> 会话态接口务必带 `credentials: 'include'`

```ts
export async function getRagTraceList(limit = 20) {
  const resp = await fetch(`/api/v1/agent/observability/rag/latest?limit=${limit}`, {
    method: "GET",
    credentials: "include"
  });
  return (await resp.json()) as BaseResponse<RagRetrievalTrace[]>;
}

export async function getRagTraceDetail(traceId: string) {
  const resp = await fetch(`/api/v1/agent/observability/rag/${traceId}`, {
    method: "GET",
    credentials: "include"
  });
  return (await resp.json()) as BaseResponse<RagRetrievalTrace>;
}
```

---

## 8. 页面展示建议（开发调试页）

推荐拆成三块：

1. 检索摘要区  
显示：`query`、`createdAt`、`totalLatencyMs`、`degradedToVectorOnly`

2. 阶段耗时区  
显示：`vectorLatencyMs`、`esLatencyMs`、`mergeLatencyMs`

3. 召回结果区（三栏并排）  
左：`vectorDocs`  
中：`esDocs`  
右：`mergedDocs`  
每条显示：`id`、`score`、`documentId`、`snippet`

---

## 9. 降级判定规则（前端逻辑）

当出现以下任一条件，UI 高亮“已降级”：

1. `degradedToVectorOnly === true`
2. `hybridEnabled === true` 且 `esDocs.length === 0` 且 `vectorDocs.length > 0`

若有 `degradeReason`，直接展示给开发者。

---

## 10. 常见失败码处理建议

1. `40100`：跳转登录页
2. `40101`：提示“请先选择团队/租户”
3. `40400`（详情接口）：提示“轨迹不存在或已过期”
4. 其它：统一 toast `message`

---

## 11. 备注

- 轨迹是内存缓存，不是永久存储；服务重启后会清空
- 轨迹数量上限由后端配置控制：  
`jc-ai-agent.rag.observability.max-traces`（默认 300）
