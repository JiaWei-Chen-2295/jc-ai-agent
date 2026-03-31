# Agent 可观测性接口实现规划

> 规划日期：2026-03-29

## 背景

`agent_execution_log` 表已记录了完整的 ReAct 循环数据（Thought/Action/Observation），`AgentExecutionLogRepository` 已有丰富的查询方法，但没有 Controller 暴露这些能力。需要一个可观测性接口层，当前用于 debug，后期供管理员监控。

## 实现思路

### 新增文件

| 文件 | 用途 |
|------|------|
| `AgentObservabilityController.java` | REST 接口，复用已有 Repository 方法 |
| `ExecutionTimelineVO.java` | 单次会话的完整 ReAct 执行时间线 |
| `ToolStatsVO.java` | 工具调用统计（次数 + 平均耗时） |
| `ExecutionOverviewVO.java` | 会话执行概览（总迭代数、总耗时、工具分布） |

### 接口设计（4 个接口）

```
GET /v1/agent/observability/session/{sessionId}/timeline
```
- 返回按 iteration 分组的 Thought→Action→Observation 完整链路
- 直接调用 `repository.findActiveBySessionId()` 后按 iteration 分组
- 用于 debug 时查看 Agent 每一步的决策过程

```
GET /v1/agent/observability/session/{sessionId}/overview
```
- 返回：总迭代数、总耗时、平均耗时、各 phase 数量、超时日志数
- 复用 `findMaxIterationBySessionId()`、`findTotalExecutionTimeBySessionId()`、`countByPhaseForSession()`、`findTimeoutLogsBySessionId()`
- 用于快速判断某次会话的执行健康度

```
GET /v1/agent/observability/session/{sessionId}/tools
```
- 返回每个 Tool 的调用次数和平均耗时
- 复用 `countByToolNameForSession()` + `findActiveBySessionIdAndToolName()` 计算
- 用于定位慢工具

```
GET /v1/agent/observability/tenant/logs?page=0&size=20
```
- 分页查询当前租户下所有执行日志
- 复用 `findByTenantIdAndIsDelete(tenantId, 0, pageable)`
- 后期管理员监控用，当前 debug 也可用

### 架构原则

1. **不新建 Service 层** — Controller 直接注入 Repository，逻辑只是查询 + VO 转换，无业务规则
2. **复用已有 Repository 方法** — 不写新 SQL，所有查询 Repository 已实现
3. **遵循项目现有模式** — `BaseResponse<T>` 包装、`@Operation` 文档、租户隔离、登录校验
4. **VO 用 `@Data @Builder`** — 与 quiz 模块的 VO 风格一致

### 关键路径

```
Controller 层
  └─ 注入 AgentExecutionLogRepository（已有）
  └─ 注入 UserService（登录校验，已有）
  └─ 查询 → 转换 VO → ResultUtils.success() 返回
```

### 涉及的已有文件（只读引用，不修改）

- `AgentExecutionLogRepository.java` — 所有查询方法已就绪
- `AgentExecutionLog.java` — Entity，含 phase/toolName/executionTimeMs 等字段
- `BaseResponse.java` / `ResultUtils.java` — 响应包装
- `UserService.java` — 登录校验
- `TenantContextHolder.java` — 租户上下文

### 验证方式

1. 启动项目，先触发一次 Quiz 会话让 Agent 产生执行日志
2. 调用 4 个接口，验证返回数据与数据库记录一致
3. 检查 Swagger UI (`/swagger-ui.html`) 接口文档是否正确生成

## 前端对接指南

### 页面结构建议

管理员监控面板拆为两个页面：

**页面 A：会话列表页**（入口）
- 调用 `GET /tenant/logs?page=0&size=20` 获取分页日志列表
- 展示表格：会话 ID、工具名称、阶段、耗时、时间
- 点击某条会话 ID → 跳转页面 B

**页面 B：会话详情页**（核心）
- 页面加载时并行调用 3 个接口：

```
Promise.all([
  fetch(`/v1/agent/observability/session/${id}/overview`),
  fetch(`/v1/agent/observability/session/${id}/timeline`),
  fetch(`/v1/agent/observability/session/${id}/tools`)
])
```

### 各接口对应的 UI 组件

| 接口 | 渲染方式 | 说明 |
|------|----------|------|
| `/overview` | 顶部统计卡片 | 总迭代数、总耗时、平均耗时、超时数，4 个数字卡片横排 |
| `/timeline` | 垂直时间线 | 按 iteration 分组，每组内 Thought→Action→Observation 三步，展开可看 input/output JSON |
| `/tools` | 柱状图或表格 | X 轴工具名，Y 轴调用次数，柱体标注平均耗时 |

### 数据结构与渲染映射

**overview 响应 → 统计卡片：**
```json
{
  "maxIteration": 5,        // → 卡片「总迭代」
  "totalTimeMs": 12300,     // → 卡片「总耗时」，前端转为 "12.3s"
  "avgTimeMs": 2460,        // → 卡片「平均耗时」
  "phaseCount": {           // → 可选：饼图展示各阶段占比
    "THOUGHT": 5,
    "ACTION": 5,
    "OBSERVATION": 5
  },
  "timeoutCount": 1         // → 卡片「超时」，>0 标红
}
```

**timeline 响应 → 时间线组件：**
```json
{
  "iterations": [
    {
      "iteration": 1,
      "steps": [
        { "phase": "THOUGHT",     "outputData": {"thinking": "..."} },
        { "phase": "ACTION",      "toolName": "QuizGeneratorTool", "inputData": {...} },
        { "phase": "OBSERVATION", "outputData": {...}, "executionTimeMs": 1200 }
      ]
    }
  ]
}
```
- 每个 iteration 渲染为一个折叠面板
- 三个 step 用不同颜色区分：THOUGHT 蓝色、ACTION 橙色、OBSERVATION 绿色
- `inputData` / `outputData` 用 JSON 折叠查看器展示（如 react-json-view）

**tools 响应 → 图表：**
```json
{
  "tools": [
    { "toolName": "QuizGeneratorTool",     "count": 3, "avgTimeMs": 2100 },
    { "toolName": "KnowledgeRetrieverTool", "count": 2, "avgTimeMs": 800 }
  ]
}
```
- 按 avgTimeMs 降序排列，最慢的工具排最前
- 超过 5000ms 的标红提示

### 交互要点

1. **timeline 的 JSON 数据默认折叠** — input/output 可能很大，展开查看即可，不要默认全展开
2. **overview 的超时数 > 0 时高亮** — 提示管理员关注性能问题
3. **tools 图表支持点击** — 点击某个工具名，筛选 timeline 只显示该工具相关的 iteration