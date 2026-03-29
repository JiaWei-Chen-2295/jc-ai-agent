# JC-AI-Agent 智能学习平台需求规格说明书

## 1. 项目概述

### 1.1 项目背景

随着人工智能技术的快速发展，教育领域正在经历深刻变革。传统学习方式存在知识获取效率低、个性化程度不足、反馈周期长等问题。本项目旨在构建一个基于大语言模型的智能学习平台，通过RAG（检索增强生成）技术整合用户私有知识库，结合智能测验系统实现个性化学习路径规划。

### 1.2 项目目标

构建一个支持多租户的企业级AI智能学习平台，具备以下核心能力：
- 基于私有文档的智能问答系统
- 自适应智能测验与知识掌握追踪
- 完整的用户管理与团队协作功能
- 可观测的Agent执行监控系统

### 1.3 技术栈

| 层次 | 技术选型 |
|------|----------|
| 后端框架 | Spring Boot 3.4.4 + Java 21 |
| AI框架 | Spring AI Alibaba 1.0.0-M6.1 |
| 大模型 | 阿里云 DashScope (Qwen3系列) |
| 向量数据库 | PostgreSQL + pgvector |
| 搜索引擎 | Elasticsearch 8.x |
| 缓存 | Redis 6+ |
| 文档解析 | Apache Tika + Apache POI |
| API文档 | SpringDoc OpenAPI + Knife4j |

---

## 2. 功能需求

### 2.1 用户系统

#### 2.1.1 用户注册
- 支持邮箱账号注册
- 密码需满足基本安全要求
- 注册成功后自动创建个人租户（Personal Tenant）

#### 2.1.2 用户登录/登出
- Session基于的身份认证
- 登录成功后自动设置默认活跃租户
- 支持Session持久化

#### 2.1.3 头像管理
- 支持阿里云OSS前端直传
- 上传凭证由后端生成
- 支持头像查看、删除、重传

#### 2.1.4 个人中心
- 查看和修改个人信息
- 查看我的会话列表

### 2.2 多租户系统

#### 2.2.1 租户类型
| 类型 | 说明 | 创建权限 |
|------|------|----------|
| Personal Tenant | 用户个人专属团队 | 系统自动创建 |
| Team Tenant | 可多人协作的团队 | 用户可创建，默认上限5个 |

#### 2.2.2 团队管理
- **创建团队**：用户可创建Team Tenant，默认最多5个
- **加入团队**：通过邀请码或管理员邀请加入
- **退出团队**：普通成员可主动退出
- **转让管理员**：管理员可转让管理权限
- **切换活跃租户**：用户可在多个所属租户间切换

#### 2.2.3 租户权限规则
| 操作 | 成员 | 团队管理员 | 系统管理员 |
|------|------|------------|------------|
| 上传文档 | ✓ | ✓ | ✓ |
| 查看文档列表 | ✓ | ✓ | ✓ |
| 删除文档 | ✗ | ✓ | ✓ |
| 重索引文档 | ✗ | ✓ | ✓ |
| 踢出成员 | ✗ | ✓ | ✓ |

### 2.3 AI对话系统

#### 2.3.1 会话管理
- 创建新对话会话
- 查看历史会话列表
- 查看会话消息历史

#### 2.3.2 RAG知识问答
- 基于用户上传文档的智能问答
- 支持混合检索（向量检索 + BM25关键词检索）
- 使用RRF（Reciprocal Rank Fusion）算法融合结果
- 支持上下文理解的连续对话

#### 2.3.3 SSE流式输出
- Server-Send Events实时流式响应
- 支持打字机效果展示
- 支持Token级流式输出

#### 2.3.4 工具调用
- 支持AI Agent自动调用工具
- 内置工具：
  - 网页搜索
  - 网页内容抓取
  - 文件操作
  - 终端命令执行
  - 资源下载
  - PDF生成
  - 邮件发送
- 支持MCP协议扩展

### 2.4 文档与知识库

#### 2.4.1 文档上传
- 支持文件类型：Markdown、TXT、PDF、PPT、DOCX、HTML
- 单文件最大50MB
- 上传路径包含租户信息（物理隔离）

#### 2.4.2 异步向量化索引
- 文档上传后异步处理
- 支持关键词 enrichment
- 向量存储至PostgreSQL + pgvector
- 关键词索引同步至Elasticsearch
- 支持失败重试机制

#### 2.4.3 文档管理
- 文档列表查询（分页）
- 文档状态查看
- 文档删除（同步删除向量）
- 文档重索引

#### 2.4.4 混合检索
- 向量相似度检索（语义搜索）
- BM25关键词检索（ES）
- RRF结果融合
- 支持查询重写与多语言翻译

### 2.5 智能测验系统

#### 2.5.1 测验模式
| 模式 | 说明 |
|------|------|
| ADAPTIVE | 自适应模式，根据用户掌握程度调整难度 |
| FIXED | 固定难度模式 |

#### 2.5.2 知识点管理
- 自动从文档中提取知识点
- 构建知识点图谱
- 追踪知识点掌握状态

#### 2.5.3 测验会话
- 创建测验会话
- 提交答案并获取即时反馈
- 查看测验报告
- 查看知识盲区分析

#### 2.5.4 认知状态追踪
- 记录用户答题历史
- 计算知识点掌握度
- 生成知识覆盖度报告

### 2.6 可观测性系统

#### 2.6.1 Agent执行监控
- 记录Agent执行各阶段事件
- 记录工具调用统计
- 生成执行时间线

#### 2.6.2 日志与追踪
- 结构化日志输出
- 执行概览统计
- 详细的执行日志查询

#### 2.6.3 可视化展示
- SSE推送执行事件
- 实时展示Agent思考过程
- 支持多种展示格式（Markdown、JSON、表格）

---

## 3. 非功能需求

### 3.1 性能要求

| 指标 | 要求 |
|------|------|
| API响应时间 | P99 < 500ms |
| 向量检索延迟 | < 200ms |
| SSE首字节时间 | < 1s |
| 支持并发用户 | > 100 |

### 3.2 可用性要求

- 服务可用性：99.9%
- 支持水平扩展
- 文档索引失败自动重试

### 3.3 安全性要求

- 用户密码加密存储
- 租户数据物理隔离
- API接口权限校验
- 文件上传安全校验

### 3.4 扩展性要求

- 支持插件化的工具扩展
- 支持多种存储后端（可扩展）
- 支持多语言查询翻译

---

## 4. 系统架构

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                        │
│   Web前端 | 移动端 | Swagger/Knife4j | 管理后台                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          API网关层 (Port: 8525, /api)                       │
│   Session认证 | 租户上下文注入 | CORS | 全局异常 | SSE | OpenAPI 3.0         │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Controller 层                                   │
│  User | Tenant | StudyFriend | Document | Quiz | Avatar | Observability    │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Service 业务服务层                                 │
│   UserService | TenantService | ChatService | DocumentService | QuizService │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Agent 智能体层                                     │
│   RAG Agent | Quiz Agent | Display Event | Tool Registration                │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RAG 基础设施层                                       │
│   Document Parser | Vector Store | Elasticsearch | Query Rewriter           │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            存储层                                           │
│     PostgreSQL (pgvector)  │  Redis  │  Elasticsearch  │  OSS              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 核心数据流

#### 4.2.1 RAG对话流程
```
用户问题 → Controller → ChatService → RAG Agent
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
            Query Rewriter    Document Retrieval    Tool Calling
                    │                   │                   │
                    └───────────────────┼───────────────────┘
                                        ▼
                                  LLM 生成
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
            Display Event         SSE Output        存储消息历史
```

#### 4.2.2 文档索引流程
```
文档上传 → Controller → 存储原始文件 → 异步事件
                                          │
                                          ▼
                                  Document Parser
                                          │
                                          ▼
                                  向量化处理
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
            PostgreSQL           Elasticsearch          Redis 缓存
            (向量存储)            (关键词索引)           (状态更新)
```

---

## 5. 数据模型

### 5.1 核心实体

#### 5.1.1 用户 (User)
- id, username, email, password, avatar, role, createTime, updateTime

#### 5.1.2 租户 (Tenant)
- id, name, type (PERSONAL/TEAM), ownerUserId, status, createTime

#### 5.1.3 租户用户关联 (TenantUser)
- id, tenantId, userId, role (MEMBER/ADMIN), joinTime

#### 5.1.4 文档 (StudyFriendDocument)
- id, tenantId, ownerUserId, fileName, fileUrl, fileType, status, vectorCount, createTime

#### 5.1.5 对话会话 (ChatSession)
- id, tenantId, userId, title, createTime

#### 5.1.6 对话消息 (ChatMessage)
- id, sessionId, role, content, createTime

#### 5.1.7 测验会话 (QuizSession)
- id, tenantId, userId, quizMode, status, knowledgePoints, cognitiveState, createTime

#### 5.1.8 测验题目 (QuizQuestion)
- id, sessionId, content, options, correctAnswer, userAnswer, isCorrect

---

## 6. 接口概览

### 6.1 用户接口
| 接口 | 方法 | 说明 |
|------|------|------|
| /api/user/register | POST | 用户注册 |
| /api/user/login | POST | 用户登录 |
| /api/user/logout | POST | 用户登出 |
| /api/user/get/login | GET | 获取当前登录用户 |
| /api/user/avatar/upload-token | POST | 获取头像上传凭证 |

### 6.2 租户接口
| 接口 | 方法 | 说明 |
|------|------|------|
| /api/tenant/create | POST | 创建团队 |
| /api/tenant/list | GET | 获取我加入的团队列表 |
| /api/tenant/join | POST | 加入团队 |
| /api/tenant/leave | POST | 退出团队 |
| /api/tenant/transfer-admin | POST | 转让管理员 |
| /api/tenant/active | POST | 切换活跃租户 |

### 6.3 AI对话接口
| 接口 | 方法 | 说明 |
|------|------|------|
| /api/ai_friend/chat_session/list | GET | 获取会话列表 |
| /api/ai_friend/chat_session/create | POST | 创建会话 |
| /api/ai_friend/chat_message/list | GET | 获取消息列表 |
| /api/ai_friend/do_chat/async | POST | 异步对话 |
| /api/ai_friend/do_chat/sse/emitter | GET | SSE流式对话 |

### 6.4 文档接口
| 接口 | 方法 | 说明 |
|------|------|------|
| /api/document/upload | POST | 上传文档 |
| /api/document/list | GET | 文档列表 |
| /api/document/get | GET | 文档详情 |
| /api/document/delete | DELETE | 删除文档 |
| /api/document/reindex | POST | 重索引 |

### 6.5 测验接口
| 接口 | 方法 | 说明 |
|------|------|------|
| /api/quiz/session/create | POST | 创建测验 |
| /api/quiz/session/list | GET | 测验列表 |
| /api/quiz/session/detail | GET | 测验详情 |
| /api/quiz/answer/submit | POST | 提交答案 |
| /api/quiz/cognitive-state | GET | 认知状态 |
| /api/quiz/knowledge-gap | GET | 知识盲区 |

### 6.6 可观测性接口
| 接口 | 方法 |说明 |
|------|------|------|
| /api/agent/observability/overview | GET | 执行概览 |
| /api/agent/observability/timeline | GET | 执行时间线 |
| /api/agent/observability/logs | GET | 执行日志 |
| /api/agent/display/event | GET | SSE事件流 |

---

## 7. 部署要求

### 7.1 环境依赖

| 软件 | 版本要求 |
|------|----------|
| JDK | 21+ |
| PostgreSQL | 15+ (需pgvector扩展) |
| Redis | 6+ |
| Elasticsearch | 8.x |

### 7.2 端口配置

| 服务 | 端口 |
|------|------|
| 应用服务 | 8525 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Elasticsearch | 9200 |

### 7.3 环境变量

```
JC_AI_AGENT_API_KEY          # 阿里云DashScope API Key
JC_AI_AGENT_POSTGRES_URL     # PostgreSQL连接地址
JC_AI_AGENT_POSTGRES_USER_NAME
JC_AI_AGENT_POSTGRES_PWD
JC_REDIS_HOST
JC_REDIS_PORT
JC_REDIS_PASSWORD
JC_ES_URIS
JC_ES_USERNAME
JC_ES_PASSWORD
JC_OSS_ENDPOINT
JC_OSS_BUCKET
JC_OSS_ACCESS_KEY_ID
JC_OSS_ACCESS_KEY_SECRET
JC_STORAGE_DOMAIN
```

---

## 8. 附录

### 8.1 术语表

| 术语 | 说明 |
|------|------|
| RAG | Retrieval-Augmented Generation，检索增强生成 |
| SSE | Server-Send Events，服务端推送事件 |
| pgvector | PostgreSQL向量数据库扩展 |
| RRF | Reciprocal Rank Fusion，互惠秩融合算法 |
| MCP | Model Context Protocol，模型上下文协议 |
| Tenant | 租户，代表一个团队或个人工作空间 |
| ReAct | Reasoning + Acting，推理行动框架 |

### 8.2 参考资料

- Spring AI Alibaba 官方文档
- pgvector 官方文档
- Elasticsearch 官方文档
- 阿里云DashScope 官方文档