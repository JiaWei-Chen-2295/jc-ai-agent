# JC-AI-Agent 软件需求规格说明书 (SRS)

> **项目名称：** JC-AI-Agent 智能学习平台后端
> **版本：** v1.0
> **编写日期：** 2026-03-21
> **文档状态：** 已实现需求提取

---

## 目录

- [1. 引言](#1-引言)
- [2. 系统总体描述](#2-系统总体描述)
  - [2.3 核心类图](#23-核心类图)
- [3. 用例分析](#3-用例分析)
- [4. 功能需求](#4-功能需求)
- [5. 核心业务流程](#5-核心业务流程)
  - [5.4 智能测验完整活动流程](#54-智能测验完整活动流程)
  - [5.5 状态机](#55-状态机)
- [6. 数据模型](#6-数据模型)
- [7. 非功能需求](#7-非功能需求)
- [8. 接口清单](#8-接口清单)

---

## 1. 引言

### 1.1 项目背景与目的

本项目是一个面向学生的 **AI 智能学习平台后端系统**，旨在通过 AI 大模型和 RAG 知识检索增强技术，为学生提供以下核心能力：

1. **智能知识问答**：上传学习资料后，基于 RAG 技术进行精准知识问答
2. **自适应智能测验**：利用 ReAct Agent 自主推理，根据学生认知状态动态生成题目
3. **学习分析与薄弱点追踪**：三维认知模型实时评估学习状态

### 1.2 文档范围

本文档记录系统已实现的全部功能需求，涵盖用户管理、多租户隔离、AI 对话、文档知识库、智能测验、学习分析和可观测性等模块。

### 1.3 术语与缩略语

| 术语 | 定义 |
|------|------|
| **RAG** | Retrieval-Augmented Generation，检索增强生成 |
| **ReAct** | Reasoning + Acting，推理与行动交替的 Agent 范式 |
| **RRF** | Reciprocal Rank Fusion，倒数排名融合算法 |
| **BM25** | Best Matching 25，经典信息检索排名算法 |
| **PGVector** | PostgreSQL 向量扩展，支持近似最近邻搜索 |
| **SSE** | Server-Sent Events，服务端推送事件流 |
| **Tenant** | 租户，系统中的数据隔离单元（个人空间或团队） |
| **DashScope** | 阿里云大模型服务平台 |

### 1.4 技术栈概览

| 层级 | 技术选型 |
|------|----------|
| 应用框架 | Spring Boot 3.4.4 / Java 21 |
| AI 框架 | Spring AI |
| 大模型 | 阿里云 DashScope (Qwen3) |
| 嵌入模型 | text-embedding-v2 (1536 维) |
| 主数据库 | PostgreSQL 15+ (pgvector 扩展) |
| 缓存 | Redis 6+ |
| 搜索引擎 | Elasticsearch 8.x |
| 对象存储 | 阿里云 OSS |
| API 文档 | Swagger / Knife4j (OpenAPI 3.0) |

---

## 2. 系统总体描述

### 2.1 系统架构

> **图表文件：** [diagrams/system-architecture.drawio](diagrams/system-architecture.drawio)

系统采用经典分层架构，自上而下为：

| 层级 | 说明 |
|------|------|
| **客户端层** | Web 前端、移动端、Swagger 文档、管理后台 |
| **API 网关层** | Session 认证、租户上下文注入、CORS、全局异常处理、SSE 流式输出、OpenAPI |
| **Controller 层** | 9 个 Controller，处理 HTTP 请求路由 |
| **业务服务层** | UserService、TenantService、ChatService、QuizService 等 11+ 服务 |
| **AI Agent 层** | AbstractReActAgent、QuizReActAgent、StudyFriend ChatClient，4 个 Agent 工具 |
| **RAG 检索层** | HybridRetriever（混合检索引擎）、TenantVectorStore、EsKeywordSearch、RRF Merger |
| **数据持久层** | PostgreSQL + PGVector、Redis、Elasticsearch、阿里云 OSS、DashScope API |

### 2.2 部署架构

> **图表文件：** [diagrams/deployment.drawio](diagrams/deployment.drawio)

| 组件 | 配置 |
|------|------|
| 应用服务器 | JVM (Java 21), -Xms512m -Xmx2g, G1GC, Port 8525 |
| PostgreSQL | Port 5432, pgvector 扩展 |
| Redis | Port 6379 |
| Elasticsearch | Port 9200 |
| 阿里云 DashScope | HTTPS 远程调用 |
| 阿里云 OSS | 前端直传 + 后端签名 |

### 2.3 核心类图

> **图表文件：** [diagrams/class-diagram.drawio](diagrams/class-diagram.drawio)

类图展示系统核心设计模式与类层次关系，分为 5 个区域：

| 区域 | 包含的类 | 设计模式 |
|------|----------|----------|
| **Agent Core** | BaseAgent(接口) → AbstractReActAgent(抽象) → QuizReActAgent | 模板方法模式 |
| **Agent Tools** | AgentTool(接口) → QuizGeneratorTool / KnowledgeRetrieverTool / AnswerEvaluatorTool / UserAnalyzerTool | 策略模式 |
| **Decision Engine** | DecisionEngine (三维认知阈值判定) | 独立决策引擎 |
| **RAG Layer** | HybridRetriever → TenantVectorStore + EsKeywordSearchService + RRFMerger | 门面模式 |
| **Domain Entities** | QuizSession / QuizQuestion / QuestionResponse / UserKnowledgeState | JPA 实体 |

### 2.4 用户角色定义

| 角色 | 权限说明 |
|------|----------|
| **学生用户** | 注册登录、创建/加入团队、上传文档、AI 对话、参加测验、查看学习分析 |
| **管理员** | 继承学生用户全部权限 + 用户管理（增删改查）、查看所有用户对话、全量 ES 同步、Agent 可观测性 |
| **AI 大模型** | 系统参与者，提供对话生成、文档嵌入、Agent 推理等能力 |

---

## 3. 用例分析

### 3.1 用例图

> **图表文件：** [diagrams/use-cases.drawio](diagrams/use-cases.drawio)

### 3.2 用例清单

| 编号 | 用例名称 | 主要参与者 | 所属模块 |
|------|----------|------------|----------|
| UC-01 | 注册账号 | 学生用户 | 用户系统 |
| UC-02 | 登录/登出 | 学生用户 | 用户系统 |
| UC-03 | 上传头像 | 学生用户 | 用户系统 |
| UC-04 | 查看个人信息 | 学生用户 | 用户系统 |
| UC-05 | 创建团队 | 学生用户 | 多租户 |
| UC-06 | 加入团队 | 学生用户 | 多租户 |
| UC-07 | 退出团队 | 学生用户 | 多租户 |
| UC-08 | 切换活跃租户 | 学生用户 | 多租户 |
| UC-09 | 转移管理权限 | 学生用户 | 多租户 |
| UC-10 | 创建对话会话 | 学生用户 | AI 对话 |
| UC-11 | RAG 知识问答 | 学生用户 / AI | AI 对话 |
| UC-12 | SSE 流式对话 | 学生用户 | AI 对话 |
| UC-13 | 工具调用对话 | 学生用户 | AI 对话 |
| UC-14 | 查看历史消息 | 学生用户 | AI 对话 |
| UC-15 | 上传文档 | 学生用户 | 文档知识库 |
| UC-16 | 异步向量化索引 | 系统自动 / AI | 文档知识库 |
| UC-17 | 管理文档 | 学生用户 | 文档知识库 |
| UC-18 | 同步至 ES | 管理员 | 文档知识库 |
| UC-19 | 混合检索 | 系统内部 / AI | 文档知识库 |
| UC-20 | 创建测验 | 学生用户 | 智能测验 |
| UC-21 | 提交答案 | 学生用户 | 智能测验 |
| UC-22 | 获取下一题 | 学生用户 / AI | 智能测验 |
| UC-23 | 查看测验状态 | 学生用户 | 智能测验 |
| UC-24 | 暂停/恢复/放弃 | 学生用户 | 智能测验 |
| UC-25 | 查看知识覆盖率 | 学生用户 | 智能测验 |
| UC-26 | 查看三维认知状态 | 学生用户 | 学习分析 |
| UC-27 | 查看测验报告 | 学生用户 | 学习分析 |
| UC-28 | 查看知识薄弱点 | 学生用户 | 学习分析 |
| UC-29 | 标记薄弱点已解决 | 学生用户 | 学习分析 |
| UC-30 | 查看 Agent 执行时间线 | 管理员 | 可观测性 |
| UC-31 | 查看工具统计 | 管理员 | 可观测性 |
| UC-32 | 查看 RAG 检索追踪 | 管理员 | 可观测性 |
| UC-33 | 管理用户 (增删改查) | 管理员 | 管理功能 |
| UC-34 | 查看所有用户对话 | 管理员 | 管理功能 |
| UC-35 | 全量同步 ES 索引 | 管理员 | 管理功能 |

---

## 4. 功能需求

### 4.1 用户系统 (FR-USER)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-USER-001 | 用户通过账号和密码注册，密码需加密存储 | P0 | 无 |
| FR-USER-002 | 用户通过账号密码登录，建立 Session 会话 | P0 | 已注册 |
| FR-USER-003 | 用户登出，清除 Session | P0 | 已登录 |
| FR-USER-004 | 获取当前登录用户信息（脱敏） | P0 | 已登录 |
| FR-USER-005 | 管理员创建用户 | P1 | 管理员角色 |
| FR-USER-006 | 管理员删除用户（逻辑删除） | P1 | 管理员角色 |
| FR-USER-007 | 管理员更新用户信息 | P1 | 管理员角色 |
| FR-USER-008 | 管理员搜索用户列表 | P1 | 管理员角色 |
| FR-USER-009 | 用户注册时自动创建个人租户 (PERSONAL) | P0 | 无 |
| FR-USER-010 | 用户获取 OSS 上传策略，前端直传头像至 OSS | P2 | 已登录 |
| FR-USER-011 | 用户更新头像地址 | P2 | 已登录 |

### 4.2 多租户系统 (FR-TENANT)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-TENANT-001 | 用户创建团队，创建者自动成为 ADMIN，每人限建 5 个 | P0 | 已登录 |
| FR-TENANT-002 | 查询当前用户已加入的团队列表 | P0 | 已登录 |
| FR-TENANT-003 | 用户通过团队 ID 加入已有团队 | P0 | 已登录 |
| FR-TENANT-004 | 用户退出团队，若为 ADMIN 则自动转移管理权限 | P1 | 已加入团队 |
| FR-TENANT-005 | ADMIN 手动转移管理权限给指定成员 | P1 | ADMIN 角色 |
| FR-TENANT-006 | 切换当前活跃租户（Session 级别） | P0 | 已加入多个团队 |
| FR-TENANT-007 | 所有业务数据按 tenantId 严格隔离 | P0 | - |

### 4.3 AI 对话 (FR-CHAT)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-CHAT-001 | 创建对话会话（绑定租户和用户） | P0 | 已登录、已选租户 |
| FR-CHAT-002 | 查询用户会话列表（游标分页，按最后消息时间降序） | P0 | 已登录 |
| FR-CHAT-003 | 查询会话历史消息（游标分页） | P0 | 已登录 |
| FR-CHAT-004 | 同步对话：发送问题 → RAG 检索 → LLM 生成 → 返回完整回答 | P1 | 会话已创建 |
| FR-CHAT-005 | SSE 流式对话：实时推送 Token 到前端 | P0 | 会话已创建 |
| FR-CHAT-006 | SSE + Tool Calling：流式对话中支持工具调用 | P1 | 会话已创建 |
| FR-CHAT-007 | SSE + AgentEvent：结构化 Agent 事件流（思考中/搜索中/输出中） | P1 | 会话已创建 |
| FR-CHAT-008 | SSE + AgentEvent + Tool Calling：完整 Agent 能力流式对话 | P1 | 会话已创建 |
| FR-CHAT-009 | 对话结束后持久化用户消息和 AI 回复消息 | P0 | - |
| FR-CHAT-010 | 管理员查看所有用户的会话列表和消息内容 | P1 | 管理员角色 |

### 4.4 文档与 RAG 知识库 (FR-RAG)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-RAG-001 | 上传文档，支持 PDF、PPTX、DOCX、Markdown、图片格式 | P0 | 已登录 |
| FR-RAG-002 | 文档状态生命周期：UPLOADED → INDEXING → INDEXED / FAILED | P0 | - |
| FR-RAG-003 | 后台异步解析文档：根据文件类型选择对应解析器 | P0 | 文档已上传 |
| FR-RAG-004 | 文档分块后调用 text-embedding-v2 生成向量嵌入 | P0 | 文档已解析 |
| FR-RAG-005 | 向量嵌入存入 PGVector 表 (study_friends)，含租户 ID 元数据 | P0 | - |
| FR-RAG-006 | 文档分块后提取关键词，索引至 Elasticsearch (study_friends_bm25) | P1 | - |
| FR-RAG-007 | 查询文档列表和单个文档状态 | P0 | 已登录 |
| FR-RAG-008 | 删除文档 | P1 | 文档归属用户或管理员 |
| FR-RAG-009 | 失败文档重新索引 | P1 | 文档状态为 FAILED |
| FR-RAG-010 | 单文档手动同步至 ES | P2 | 管理员 |
| FR-RAG-011 | 全量批量同步 PG → ES | P2 | 管理员 |
| FR-RAG-012 | 混合检索：并行执行向量语义检索和 BM25 关键词检索 | P0 | 知识库已索引 |
| FR-RAG-013 | RRF 排序融合 (k=60)，合并两路检索结果 | P0 | - |
| FR-RAG-014 | 优雅降级：向量失败→仅 BM25；全部失败→降级模式 | P1 | - |
| FR-RAG-015 | 检索时按 tenantId 过滤，确保租户数据隔离 | P0 | - |

### 4.5 智能测验系统 (FR-QUIZ)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-QUIZ-001 | 创建测验会话，指定难度模式和文档范围 | P0 | 已登录 |
| FR-QUIZ-002 | 支持 4 种难度模式：EASY / MEDIUM / HARD / ADAPTIVE | P0 | - |
| FR-QUIZ-003 | 支持 9 种题型：单选/多选/判断/填空/简答/解释/匹配/排序/代码补全 | P0 | - |
| FR-QUIZ-004 | ReAct Agent 循环出题：Thought → Action → Observation | P0 | - |
| FR-QUIZ-005 | Agent 最大迭代 10 次，含死锁检测和降级处理 | P1 | - |
| FR-QUIZ-006 | 每次迭代记录执行日志到 agent_execution_log 表 | P1 | - |
| FR-QUIZ-007 | 提交答案后自动批改，支持部分得分 | P0 | 题目已生成 |
| FR-QUIZ-008 | 评估答案后检测概念掌握度（MASTERED/PARTIAL/UNMASTERED） | P0 | - |
| FR-QUIZ-009 | 获取下一题：Agent 根据当前认知状态动态生成 | P0 | 测验进行中 |
| FR-QUIZ-010 | 查询实时测验状态（当前题号/总题数/得分） | P0 | - |
| FR-QUIZ-011 | 暂停/恢复/放弃测验 | P1 | 测验进行中 |
| FR-QUIZ-012 | 查看知识覆盖率指标 | P1 | - |
| FR-QUIZ-013 | 查询用户测验历史列表 | P1 | 已登录 |
| FR-QUIZ-014 | 软删除测验 | P2 | - |

#### 4.5.1 ReAct Agent 工具链

| 工具 | 功能描述 |
|------|----------|
| **QuizGeneratorTool** | 从知识库检索内容并生成题目，跟踪概念清单，支持降级模式 |
| **KnowledgeRetrieverTool** | 混合检索知识库（向量+BM25），按租户隔离，可配置 top-K 和相似度阈值 |
| **UserAnalyzerTool** | 分析用户三维认知状态（理解深度/认知负荷/稳定性），追踪知识掌握度 |
| **AnswerEvaluatorTool** | 评估学生答案正确性，多题型批改，部分得分计算，反馈生成 |

### 4.6 学习分析 (FR-ANALYSIS)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-ANALYSIS-001 | 查询用户三维认知状态：理解深度/认知负荷/稳定性 (0-100) | P0 | 有测验记录 |
| FR-ANALYSIS-002 | 追踪知识点分类：已掌握/部分掌握/未掌握 | P0 | - |
| FR-ANALYSIS-003 | 按测验会话生成详细学习报告 | P1 | 测验已完成 |
| FR-ANALYSIS-004 | 自动检测知识薄弱点（OPEN 状态） | P0 | 有答题记录 |
| FR-ANALYSIS-005 | 手动标记知识薄弱点为已解决 (RESOLVED) | P1 | 存在 OPEN 薄弱点 |
| FR-ANALYSIS-006 | 计算知识覆盖率 | P1 | - |
| FR-ANALYSIS-007 | LLM 自动从文档中提取概念名称 | P1 | 文档已索引 |
| FR-ANALYSIS-008 | Redis 缓存概念清单（会话生命周期） | P2 | - |
| FR-ANALYSIS-009 | 概念名称归一化处理 | P2 | - |
| FR-ANALYSIS-010 | 知识状态批量刷盘（减少 DB 写入频率） | P2 | - |

### 4.7 Agent 可观测性 (FR-OBS)

| 需求编号 | 需求描述 | 优先级 | 前置条件 |
|----------|----------|--------|----------|
| FR-OBS-001 | 查看 ReAct Agent 完整执行时间线 (THOUGHT/ACTION/OBSERVATION) | P1 | 有 Agent 执行记录 |
| FR-OBS-002 | 查看执行统计概览（迭代次数/总耗时） | P1 | - |
| FR-OBS-003 | 分页查询 Agent 执行日志 | P1 | - |
| FR-OBS-004 | 查看各工具的调用次数和执行耗时统计 | P2 | - |
| FR-OBS-005 | 查看 RAG 检索追踪（向量/ES 延迟、融合操作、降级原因） | P2 | - |

---

## 5. 核心业务流程

### 5.1 RAG 知识问答对话流程

> **图表文件：** [diagrams/sequence-rag-chat.drawio](diagrams/sequence-rag-chat.drawio)

**流程概述：**

```
客户端 → SSE 请求
  → Controller 保存用户消息
    → StudyFriend ChatClient 调用 RAG Advisor
      → HybridRetriever 并行检索：
        ├─ PGVector 向量语义检索
        └─ Elasticsearch BM25 关键词检索
      → RRF 排序融合 (k=60)
    → 携带检索上下文发送 Prompt 至 DashScope LLM
    → LLM 流式返回 Token
  → Controller 转发 SSE 事件流至客户端
  → 流结束后保存 AI 回复消息
```

**关键设计点：**
- 两路检索并行执行，通过 RRF 融合排序
- 向量检索失败时自动降级为仅 BM25，全部失败则进入降级模式
- 每条检索记录可追踪延迟信息

### 5.2 ReAct Agent 自适应出题流程

> **图表文件：** [diagrams/sequence-quiz-agent.drawio](diagrams/sequence-quiz-agent.drawio)

**流程概述：**

```
客户端 GET /session/{id}/next
  → QuizController → QuizSessionService
    → QuizReActAgent.run(context)
      ┌─ ReAct 循环 (max 10 iterations) ──────────────┐
      │ THOUGHT: LLM 分析用户状态 → 输出 AgentDecision │
      │ ACTION:  执行选定工具 (如 KnowledgeRetriever)   │
      │ OBSERVATION: 分析结果, 更新上下文, 判断是否继续  │
      │ (每步记录 ExecutionLog)                         │
      └───────────────────────────────────────────────┘
    → 输出 AgentResponse (包含生成的题目)
  → 持久化 quiz_question
  → 返回 JSON 响应
```

**关键设计点：**
- Agent 每次迭代自主选择调用哪个工具
- 死锁检测：检测重复推理模式，防止无限循环
- 所有迭代步骤记录到 `agent_execution_log` 表供可观测性查询

### 5.3 文档处理与混合检索数据流

> **图表文件：** [diagrams/data-flow.drawio](diagrams/data-flow.drawio)

**写入路径（文档上传）：**

```
用户上传文件
  → 1.0 文档上传 (记录元数据到 PostgreSQL)
  → 2.0 文档解析 (异步, 根据格式选择解析器)
    ├─ PdfDocumentParser
    ├─ TikaDocumentParser (DOCX/PPTX)
    ├─ ImageDocumentParser (OCR)
    └─ MarkdownDocumentReader
  → 3.0 分块 & 向量化 (text-embedding-v2, 1536 维)
    ├─ 存入 PGVector (D1)
    └─ 4.0 关键词提取 → 索引至 Elasticsearch (D2)
  → 5.0 可选: PG→ES 批量同步
```

**读取路径（混合检索）：**

```
用户提问 (query + tenantId)
  → 7.0 HybridRetriever 并行:
    ├─ 7a. 向量语义检索 → PGVector (D1)
    └─ 7b. BM25 关键词检索 → Elasticsearch (D2)
  → 8.0 RRF 排序融合 (k=60)
  → 9.0 LLM 生成回答 (Prompt + 检索上下文)
  → 10.0 返回结果 (SSE/JSON) + 缓存状态至 Redis (D4)
```

### 5.4 智能测验完整活动流程

> **图表文件：** [diagrams/activity-diagram.drawio](diagrams/activity-diagram.drawio)

活动图展示测验从创建到完成的完整业务流程，包含四个泳道（客户端、测验服务、ReAct Agent、数据存储）：

```
客户端发起 → 验证 → 创建会话
  → 并行: LLM 提取概念清单 + Agent 生成初始题目
  → 返回首题
  → [答题循环]:
    → 用户作答 → Agent 评估 → 保存结果
    → 并行: 更新知识状态(Redis) + 记录未掌握知识点
    → DecisionEngine 检查完成条件:
      → [认知达标]: 完成测验 → 刷盘 Redis→DB → 展示报告
      → [有预生成题]: 取下一题 → 继续循环
      → [无预生成题]: Agent 动态生成 → 继续循环
```

### 5.5 状态机

> **图表文件：** [diagrams/state-diagram.drawio](diagrams/state-diagram.drawio)

系统包含三个核心状态机：

**QuizSession 测验会话状态机（5 个状态）：**

| 状态 | 说明 | 可转换至 |
|------|------|----------|
| IN_PROGRESS | 测验进行中（初始状态） | PAUSED, COMPLETED, TIMEOUT, ABANDONED |
| PAUSED | 已暂停 | IN_PROGRESS (恢复), ABANDONED |
| COMPLETED | 已完成（终态） | - |
| TIMEOUT | 已超时（终态） | - |
| ABANDONED | 已放弃（终态） | - |

**StudyFriendDocument 文档状态机（4 个状态）：**

| 状态 | 说明 | 可转换至 |
|------|------|----------|
| UPLOADED | 已上传（初始状态） | INDEXING |
| INDEXING | 索引中（异步处理） | INDEXED, FAILED |
| INDEXED | 已索引（终态） | - |
| FAILED | 处理失败（可重试） | INDEXING (reindex) |

**UnmasteredKnowledge 知识薄弱点状态机（2 个状态）：**

| 状态 | 说明 | 可转换至 |
|------|------|----------|
| ACTIVE | 活跃薄弱点（自循环：再次答错 → failureCount++） | RESOLVED |
| RESOLVED | 已解决 | - |

---

### 6.1 ER 图

> **图表文件：** [diagrams/er-diagram.drawio](diagrams/er-diagram.drawio)

### 6.2 核心表结构

#### 6.2.1 用户与租户模块

**user 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| userAccount | VARCHAR | 账号（唯一） |
| userPassword | VARCHAR | 加密密码 |
| userName | VARCHAR | 用户昵称 |
| userAvatar | VARCHAR | 头像 URL |
| userRole | VARCHAR | 角色 (user / admin) |
| userProfile | VARCHAR | 个人简介 |
| unionId / mpOpenId | VARCHAR | 第三方登录标识 |
| createTime / updateTime | DATETIME | 时间戳 |
| isDelete | TINYINT | 逻辑删除标记 |

**tenant 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| tenantName | VARCHAR | 租户名称 |
| tenantType | ENUM | PERSONAL / TEAM |
| ownerUserId | BIGINT (FK) | 创建者 → user.id |
| createTime / updateTime | DATETIME | 时间戳 |
| isDelete | TINYINT | 逻辑删除标记 |

**tenant_user 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| tenantId | BIGINT (FK) | → tenant.id |
| userId | BIGINT (FK) | → user.id |
| role | ENUM | ADMIN / MEMBER |
| UNIQUE | - | (tenantId, userId) |

#### 6.2.2 对话模块

**chat_session 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| chatId | VARCHAR (PK) | UUID 主键 |
| tenantId | BIGINT (FK) | → tenant.id |
| userId | BIGINT (FK) | → user.id |
| appCode | VARCHAR | 应用编码 |
| title | VARCHAR | 会话标题 |
| lastMessageAt | TIMESTAMP | 最后消息时间 |
| createdAt / updatedAt | TIMESTAMP | 时间戳 |
| isDeleted | BOOLEAN | 逻辑删除 |

**chat_message 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| chatId | VARCHAR (FK) | → chat_session.chatId |
| tenantId / userId | BIGINT | 租户/用户 |
| role | ENUM | USER / ASSISTANT |
| clientMessageId | VARCHAR | 客户端消息 ID（幂等） |
| content | TEXT | 消息内容 |
| metadata | JSONB | 附加元数据 |
| createdAt | TIMESTAMP | 创建时间 |

#### 6.2.3 文档模块

**study_friend_document 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| tenantId | BIGINT (FK) | 所属租户 |
| ownerUserId | BIGINT (FK) | 上传者 |
| fileName | VARCHAR | 文件名 |
| filePath | VARCHAR | 存储路径 |
| fileType | VARCHAR | 文件类型 |
| status | ENUM | UPLOADED / INDEXING / INDEXED / FAILED |
| errorMessage | TEXT | 失败原因 |
| createdAt / updatedAt | TIMESTAMP | 时间戳 |

**study_friends 表 (PGVector)**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| tenant_id | BIGINT | 租户 ID (metadata) |
| content | TEXT | 文档块内容 |
| metadata | JSONB | 元数据 |
| embedding | vector(1536) | 向量嵌入 |

#### 6.2.4 测验模块

**quiz_session 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| tenantId / userId | BIGINT | 租户/用户 |
| quizMode | ENUM | EASY / MEDIUM / HARD / ADAPTIVE |
| documentScope | JSONB | 文档范围 List\<Long\> |
| status | ENUM | IN_PROGRESS / COMPLETED / PAUSED / TIMEOUT / ABANDONED |
| currentQuestionNo | INT | 当前题号 |
| totalQuestions | INT | 总题数 |
| score | DECIMAL | 累计得分 |
| agentState | JSONB | Agent 状态快照 |
| startedAt / completedAt | TIMESTAMP | 时间范围 |

**quiz_question 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| sessionId | UUID (FK) | → quiz_session.id |
| questionNo | INT | 题号 |
| questionType | ENUM | 9 种题型 |
| questionText | TEXT | 题目内容 |
| options | JSONB | 选项 |
| correctAnswer | TEXT | 正确答案 |
| explanation | TEXT | 解析 |
| relatedConcept | VARCHAR | 关联概念 |
| difficulty | ENUM | EASY / MEDIUM / HARD |
| sourceDocId / sourceChunkId | VARCHAR | 来源文档 |

**question_response 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| sessionId / questionId / userId | UUID / BIGINT | 关联 |
| userAnswer | TEXT | 用户答案 |
| isCorrect | BOOLEAN | 是否正确 |
| score | DECIMAL | 得分 |
| responseTimeMs | BIGINT | 作答耗时 (ms) |
| hesitationDetected | BOOLEAN | 犹豫检测 |
| confusionDetected | BOOLEAN | 困惑检测 |
| feedback | TEXT | AI 反馈 |
| conceptMastery | ENUM | MASTERED / PARTIAL / UNMASTERED |

#### 6.2.5 学习分析模块

**user_knowledge_state 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| userId | BIGINT (FK) | → user.id |
| understandingDepth | INT | 理解深度 (0-100) |
| cognitiveLoad | INT | 认知负荷 (0-100) |
| stability | INT | 稳定性 (0-100) |
| masteredConcepts | JSONB | 已掌握概念列表 |
| partialConcepts | JSONB | 部分掌握概念列表 |
| unmasteredConcepts | JSONB | 未掌握概念列表 |

**unmastered_knowledge 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| userId / sessionId | BIGINT / UUID | 关联 |
| concept | VARCHAR | 薄弱概念 |
| difficulty | ENUM | 难度等级 |
| status | ENUM | OPEN / RESOLVED |
| firstSeenAt / lastSeenAt | TIMESTAMP | 首次/最近出现 |
| resolvedAt | TIMESTAMP | 解决时间 (nullable) |

#### 6.2.6 可观测性模块

**agent_execution_log 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID (PK) | 主键 |
| sessionId | UUID (FK) | → quiz_session.id |
| iteration | INT | 迭代序号 |
| phase | ENUM | THOUGHT / ACTION / OBSERVATION |
| decision / result | JSON | 决策/执行内容 |
| executionTimeMs | BIGINT | 执行耗时 (ms) |
| createdAt | TIMESTAMP | 创建时间 |

---

## 7. 非功能需求

### 7.1 性能

| 编号 | 需求描述 |
|------|----------|
| NFR-PERF-001 | SSE 流式对话首 Token 延迟应在 2s 以内（不含模型推理时间） |
| NFR-PERF-002 | 混合检索并行执行，总延迟取决于较慢路径 |
| NFR-PERF-003 | 知识状态批量刷盘，减少测验过程中的 DB 写入频率 |
| NFR-PERF-004 | 概念清单使用 Redis 缓存（会话级 TTL） |
| NFR-PERF-005 | JVM 配置 G1GC，最大暂停 200ms |

### 7.2 安全

| 编号 | 需求描述 |
|------|----------|
| NFR-SEC-001 | 密码加密存储（不明文） |
| NFR-SEC-002 | 基于 Session 的认证，接口需登录才能访问 |
| NFR-SEC-003 | 管理功能仅限 admin 角色 |
| NFR-SEC-004 | 所有数据查询按 tenantId 隔离，防止越权 |
| NFR-SEC-005 | OSS 上传使用签名策略，限制上传目录和文件大小 |

### 7.3 可用性

| 编号 | 需求描述 |
|------|----------|
| NFR-AVL-001 | 检索降级策略：向量→BM25→降级模式，保证服务可用 |
| NFR-AVL-002 | Agent 死锁检测和最大迭代限制，防止无限循环 |
| NFR-AVL-003 | 文档索引失败可重试 |
| NFR-AVL-004 | 健康检查端点 /api/health |

### 7.4 可观测性

| 编号 | 需求描述 |
|------|----------|
| NFR-OBS-001 | Agent 执行全过程可追溯（Thought/Action/Observation 日志） |
| NFR-OBS-002 | RAG 检索延迟可追踪（向量/ES/融合各阶段） |
| NFR-OBS-003 | 工具调用性能可统计 |
| NFR-OBS-004 | API 文档自动生成（Swagger / Knife4j） |

---

## 8. 接口清单

### 8.1 用户系统 `/api/user`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/register` | 用户注册 | 公开 |
| POST | `/login` | 用户登录 | 公开 |
| POST | `/logout` | 用户登出 | 已登录 |
| GET | `/current` | 获取当前用户 | 已登录 |
| POST | `/add` | 创建用户 | 管理员 |
| POST | `/delete` | 删除用户 | 管理员 |
| POST | `/update` | 更新用户 | 管理员 |
| GET | `/search` | 搜索用户 | 管理员 |

### 8.2 用户头像 `/api/user/avatar`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/upload-policy` | 获取 OSS 上传策略 | 已登录 |
| POST | `/update-avatar` | 更新头像地址 | 已登录 |

### 8.3 多租户 `/api/tenant`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/create` | 创建团队 | 已登录 |
| GET | `/list` | 查询已加入团队 | 已登录 |
| POST | `/join` | 加入团队 | 已登录 |
| POST | `/leave` | 退出团队 | 已登录 |
| POST | `/transfer-admin` | 转移管理权限 | ADMIN |
| POST | `/active` | 切换活跃租户 | 已登录 |

### 8.4 AI 对话 `/api/ai_friend`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/session` | 创建会话 | 已登录 |
| GET | `/session/list` | 会话列表 | 已登录 |
| GET | `/session/{chatId}/messages` | 历史消息 | 已登录 |
| GET | `/admin/session/list` | 管理员查看所有会话 | 管理员 |
| GET | `/admin/session/{chatId}/messages` | 管理员查看消息 | 管理员 |
| GET | `/do_chat/async` | 同步对话 | 已登录 |
| GET | `/do_chat/sse/emitter` | SSE 流式对话 | 已登录 |
| GET | `/do_chat/sse_with_tool/emitter` | SSE + Tool Calling | 已登录 |
| GET | `/do_chat/sse/agent/emitter` | SSE + AgentEvent | 已登录 |
| GET | `/do_chat/sse_with_tool/agent/emitter` | SSE + Agent + Tool | 已登录 |

### 8.5 文档管理 `/api/document`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/upload` | 上传文档 | 已登录 |
| GET | `/{documentId}` | 查询文档状态 | 已登录 |
| GET | `/list` | 文档列表 | 已登录 |
| DELETE | `/{documentId}` | 删除文档 | 已登录 |
| POST | `/{documentId}/reindex` | 重新索引 | 已登录 |
| POST | `/{documentId}/sync-es` | 手动同步 ES | 管理员 |
| POST | `/sync-es/full` | 全量同步 ES | 管理员 |

### 8.6 智能测验 `/api/v1/quiz`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/session` | 创建测验 | 已登录 |
| GET | `/session/{id}` | 查询测验详情 | 已登录 |
| PUT | `/session/{id}` | 更新测验状态 | 已登录 |
| DELETE | `/session/{id}` | 软删除测验 | 已登录 |
| GET | `/session/list` | 测验历史 | 已登录 |
| POST | `/session/{id}/answer` | 提交答案 | 已登录 |
| GET | `/session/{id}/status` | 实时状态 | 已登录 |
| GET | `/session/{id}/next` | 获取下一题 | 已登录 |
| GET | `/session/{id}/coverage` | 知识覆盖率 | 已登录 |

### 8.7 学习分析 `/api/v1/quiz/analysis`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/user/{userId}` | 三维认知状态 | 已登录 |
| GET | `/session/{id}/report` | 测验报告 | 已登录 |
| GET | `/user/{userId}/gaps` | 知识薄弱点 | 已登录 |
| POST | `/gap/{id}/resolve` | 标记已解决 | 已登录 |

### 8.8 Agent 可观测性 `/api/v1/agent/observability`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/session/{sessionId}/timeline` | Agent 执行时间线 | 管理员 |
| GET | `/session/{sessionId}/overview` | 执行统计概览 | 管理员 |
| GET | `/session/{sessionId}/logs` | 分页查询日志 | 管理员 |
| GET | `/tool-stats` | 工具调用统计 | 管理员 |
| GET | `/rag-traces` | RAG 检索追踪 | 管理员 |

### 8.9 系统 `/api`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/health` | 健康检查 | 公开 |

---

> **文档结束**
> 共计 **35 个用例**、**60+ 条功能需求**、**40+ 个 API 接口**、**13+ 张数据库表**、**10 张 Draw.io 架构图**（系统架构图、部署架构图、核心类图、用例图、ER 图、RAG 时序图、Agent 时序图、数据流图、活动图、状态机图）。
