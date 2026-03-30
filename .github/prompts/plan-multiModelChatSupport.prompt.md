# JC-AI-Agent 用户聊天多模型支持规划方案

> 基于 Spring AI / Spring AI Alibaba 最佳实践调研，结合项目现状制定

---

## 一、调研结论总览

### 1.1 项目现状

| 维度 | 当前状态 |
|------|----------|
| AI 框架 | Spring AI Alibaba 1.0.0-M6.1 (Milestone) |
| 模型提供商 | **仅 DashScope**（Qwen3-max-2026-01-23） |
| Embedding | DashScope text-embedding-v2（1536维） |
| ChatModel 注入 | Spring Boot 自动装配，单例 `dashscopeChatModel` |
| 模型切换能力 | **无**，hardcoded 在 application.yml |
| 向量存储 | PostgreSQL pgvector + Elasticsearch 混合检索 |
| 聊天架构 | StudyFriend → ChatClient → ChatModel（单模型链路） |

### 1.2 调研的关键发现

**Spring AI 原生能力：**
- Spring AI 支持通过 `@Qualifier` 注入多个 `ChatClient` Bean（OpenAI + Anthropic 等）
- `OpenAiChatModel` 支持 `mutate()` 方法，可基于一个基础实例派生出不同 base-url/apiKey/model 的变体
- `spring.ai.model.chat` 配置支持 `openai`/`none` 等值来启用/禁用特定模型自动装配
- 运行时可通过 `ChatOptions` 动态覆盖 model、temperature 等参数

**Spring AI Alibaba 生态：**
- Spring AI Alibaba 1.0 GA 已发布，提供了更完善的 DashScope 集成
- 官方明确支持 **OpenAI 兼容模型接入**：只需引入 `spring-ai-starter-model-openai` + 配置 base-url 即可接入任何 OpenAI 兼容服务
- DashScope 本身提供 OpenAI 兼容端点：`https://dashscope.aliyuncs.com/compatible-mode/v1`
- 社区已有多模型路由需求（GitHub Issue #1759），但官方尚未内置 `RoutingChatClient`，标记为 "Not planned"

**OpenAI 兼容协议覆盖范围：**
- DashScope（阿里云百炼）✅ 完整支持
- DeepSeek ✅ 完整支持（base-url: `https://api.deepseek.com`）
- Kimi (Moonshot) ✅ 支持 OpenAI 兼容
- GLM (智谱) ✅ 支持 OpenAI 兼容
- Groq / Perplexity / Ollama 等 ✅ 均支持

### 1.3 是否使用 OpenAI 兼容协议？—— **推荐混合方案**

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| 全部走 OpenAI 兼容协议 | 统一抽象、一套代码 | DashScope 原生功能（如联网搜索 `enable_search`）丢失 | ❌ 不推荐 |
| 保留 DashScope 原生 + OpenAI starter | 兼顾 DashScope 高级特性与多模型扩展 | 两套 starter 共存需要处理 Bean 冲突 | ✅ **推荐** |
| 纯 DashScope 不扩展 | 简单 | 无法接入其他模型 | ❌ 不满足需求 |

**最终决策：保留 DashScope 原生 starter 作为主力，新增 `spring-ai-starter-model-openai` 接入 DeepSeek / OpenAI GPT / Kimi / GLM 等。**

### 1.4 是否需要模型路由？—— **需要，但自建轻量版**

Spring AI Alibaba 官方未内置 `RoutingChatClient`。根据需求（用户手动选择模型），不需要智能路由，只需要一个基于 modelId 查找对应 `ChatModel` 实例的 **ModelRegistry** 即可。

---

## 二、需求确认摘要

| 需求项 | 确认结果 |
|--------|----------|
| 目标模型提供商 | DashScope (Qwen) + DeepSeek + OpenAI (GPT) + Kimi + GLM 等 |
| 模型切换粒度 | **用户手动选择**（前端下拉框） |
| 切换级别 | **会话级别**（创建/进入会话时选定，整个会话固定） |
| RAG 支持多模型 | ✅ RAG 聊天也支持多模型切换 |
| Quiz Agent | 保持单模型（DashScope） |
| Embedding 模型 | **不切换**，保持 DashScope text-embedding-v2 |
| 模型管理方式 | **数据库动态配置**（管理员后台管理模型和 API Key） |
| 版本升级 | **同步升级到 Spring AI Alibaba 1.0 GA** |

---

## 三、技术架构设计

### 3.1 整体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端（模型选择 UI）                         │
│   ┌──────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│   │ 模型列表  │  │  创建会话时选模型  │  │ 聊天时显示当前模型    │  │
│   └──────────┘  └──────────────────┘  └──────────────────────┘  │
└──────────────────────────┬───────────────────────────────────────┘
                           │ modelId in request
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Controller Layer                             │
│   StudyFriendController  ─── modelId 从 session 或请求获取        │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                   ChatModelRegistry（核心路由）                    │
│                                                                  │
│   resolve(modelId) → ChatModel                                   │
│                                                                  │
│   ┌─────────────┐ ┌─────────────┐ ┌──────────────┐              │
│   │  DashScope   │ │  OpenAI     │ │  OpenAI      │  ...         │
│   │  ChatModel   │ │  ChatModel  │ │  ChatModel   │              │
│   │  (原生)      │ │  (DeepSeek) │ │  (Kimi/GLM)  │              │
│   └─────────────┘ └─────────────┘ └──────────────┘              │
│         ▲               ▲               ▲                        │
│         │               │               │                        │
│   spring-ai-alibaba  spring-ai-starter-model-openai              │
│   starter (原生)     (OpenAI 兼容协议, 不同 base-url)             │
└──────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    数据库：ai_model_config 表                     │
│   id | provider | model_name | display_name | base_url |         │
│   api_key_encrypted | enabled | sort_order | ...                 │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件设计

#### 3.2.1 数据库表：`ai_model_config`

```sql
CREATE TABLE IF NOT EXISTS ai_model_config (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(32)  NOT NULL,          -- 'dashscope' | 'openai' | 'deepseek' | 'kimi' | 'glm'
    model_id        VARCHAR(64)  NOT NULL UNIQUE,   -- 业务标识，如 'qwen3-max', 'deepseek-chat', 'gpt-4o'
    model_name      VARCHAR(128) NOT NULL,          -- 发送给API的实际模型名
    display_name    VARCHAR(128) NOT NULL,          -- 前端展示名，如 "通义千问3 Max"
    base_url        VARCHAR(512) NULL,              -- OpenAI兼容端点，DashScope原生可为空
    api_key_enc     VARCHAR(1024) NOT NULL,         -- 加密存储的 API Key
    max_tokens      INTEGER      NULL,              -- 模型最大输出 token
    temperature     DECIMAL(3,2) DEFAULT 0.7,       -- 默认温度
    description     VARCHAR(512) NULL,              -- 模型描述
    icon_url        VARCHAR(512) NULL,              -- 模型图标
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_model_config_enabled ON ai_model_config(enabled, sort_order);

-- 初始数据
INSERT INTO ai_model_config (provider, model_id, model_name, display_name, api_key_enc, enabled, sort_order)
VALUES ('dashscope', 'qwen3-max', 'qwen3-max-2026-01-23', '通义千问3 Max', '${encrypted_key}', true, 1);
```

#### 3.2.2 `chat_session` 表新增字段

```sql
ALTER TABLE chat_session ADD COLUMN model_id VARCHAR(64) NOT NULL DEFAULT 'qwen3-max';
```

#### 3.2.3 ChatModelRegistry（核心服务）

```java
/**
 * 模型注册中心：根据 modelId 获取对应的 ChatModel 实例。
 * - DashScope 模型使用原生 DashScopeChatModel
 * - 其他模型通过 OpenAiChatModel.mutate() 派生实例
 */
@Service
public class ChatModelRegistry {

    private final ChatModel dashscopeChatModel;           // 原生注入
    private final OpenAiChatModel openAiBaseChatModel;    // OpenAI starter 注入
    private final OpenAiApi baseOpenAiApi;                // 用于 mutate
    private final AiModelConfigRepository modelConfigRepo;
    private final ApiKeyEncryptor apiKeyEncryptor;

    // modelId -> ChatModel 实例缓存
    private final ConcurrentHashMap<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 解析 modelId，返回对应的 ChatModel
     */
    public ChatModel resolve(String modelId) {
        return modelCache.computeIfAbsent(modelId, this::createChatModel);
    }

    private ChatModel createChatModel(String modelId) {
        AiModelConfig config = modelConfigRepo.findByModelIdAndEnabledTrue(modelId)
            .orElseThrow(() -> new BusinessException("模型不可用: " + modelId));

        if ("dashscope".equals(config.getProvider())) {
            // DashScope 原生模型 —— 使用自动装配的实例
            // 如果 model_name 与默认配置不同，可通过 ChatOptions 动态指定
            return dashscopeChatModel;
        }

        // OpenAI 兼容模型 —— 通过 mutate 派生
        String decryptedKey = apiKeyEncryptor.decrypt(config.getApiKeyEnc());
        OpenAiApi derivedApi = baseOpenAiApi.mutate()
            .baseUrl(config.getBaseUrl())
            .apiKey(decryptedKey)
            .build();

        return openAiBaseChatModel.mutate()
            .openAiApi(derivedApi)
            .defaultOptions(OpenAiChatOptions.builder()
                .model(config.getModelName())
                .temperature(config.getTemperature() != null
                    ? config.getTemperature().doubleValue() : 0.7)
                .build())
            .build();
    }

    /**
     * 当管理员更新模型配置时，清除缓存以重建
     */
    public void evict(String modelId) {
        modelCache.remove(modelId);
    }

    public void evictAll() {
        modelCache.clear();
    }
}
```

#### 3.2.4 StudyFriend 改造

```java
@Component
public class StudyFriend {

    private final ChatModelRegistry chatModelRegistry;
    // ... 其他现有依赖不变

    /**
     * 改造后：根据 modelId 动态获取 ChatModel，构建 ChatClient
     */
    public Flux<String> doChatWithRAGStream(String chatMessage, String chatId,
                                             Long tenantId, String modelId) {
        ChatModel chatModel = chatModelRegistry.resolve(modelId);
        ChatClient dynamicClient = ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(
                new MessageChatMemoryAdvisor(chatMemory),
                new AgentLoggerAdvisor()
            )
            .build();

        // 后续 RAG 逻辑与现有相同...
        if (shouldUseRag(chatMessage)) {
            return dynamicClient.prompt().user(chatMessage)
                .advisors(spec -> spec
                    .param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                    .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(buildRagAdvisor(chatMessage, tenantId))
                .stream().content();
        }
        // ...
    }
}
```

#### 3.2.5 Controller 改造

```java
@GetMapping(value = "/do_chat/sse/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter doChatWithRAGStream(
        @RequestParam("chatMessage") String chatMessage,
        @RequestParam("chatId") String chatId,
        @RequestParam(value = "messageId", required = false) String messageId,
        HttpServletRequest request) {

    User loginUser = userService.getLoginUser(request);
    Long tenantId = requireTenantId();
    // ... 权限校验

    // 从 session 获取 modelId
    ChatSession session = studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
    String modelId = session.getModelId();  // 新增

    studyFriendChatService.appendUserMessage(chatId, tenantId, loginUser.getId(), chatMessage, messageId);

    SseEmitter sseEmitter = new SseEmitter(3 * 60 * 1000L);
    StringBuilder assistantBuffer = new StringBuilder();

    studyFriend.doChatWithRAGStream(chatMessage, chatId, tenantId, modelId)
        .subscribe(/* ... 与现有逻辑相同 */);

    return sseEmitter;
}
```

### 3.3 Spring AI 版本升级计划

#### 依赖变更（pom.xml）

```xml
<!-- 升级 Spring AI Alibaba 到 GA -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>1.0.0</version>  <!-- M6.1 → GA -->
</dependency>

<!-- 新增：OpenAI 兼容模型支持 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

#### 配置变更（application.yml 新增）

```yaml
spring:
  ai:
    # 保持 DashScope 原生配置
    dashscope:
      api-key: ${JC_AI_AGENT_API_KEY}
      chat:
        options:
          model: qwen3-max-2026-01-23
      embedding:
        options:
          model: text-embedding-v2

    # 新增：OpenAI 兼容端点（基础配置，实际 base-url/apiKey 由 ChatModelRegistry 动态设置）
    openai:
      api-key: ${JC_OPENAI_PLACEHOLDER_KEY:placeholder}
      base-url: https://api.deepseek.com  # 默认基础端点
      chat:
        options:
          model: deepseek-chat

    # 处理多模型 Bean 冲突
    model:
      chat: dashscope   # 默认 ChatModel 使用 DashScope
```

### 3.4 API Key 加密方案

```java
@Component
public class ApiKeyEncryptor {
    // 使用 AES-256-GCM 对称加密
    // 密钥从环境变量 JC_API_KEY_MASTER_SECRET 获取
    // 避免明文存库

    public String encrypt(String plainApiKey) { /* ... */ }
    public String decrypt(String encryptedApiKey) { /* ... */ }
}
```

---

## 四、前端对接接口设计

### 4.1 获取可用模型列表

```
GET /api/ai/models
Response:
{
  "code": 0,
  "data": [
    {
      "modelId": "qwen3-max",
      "displayName": "通义千问3 Max",
      "provider": "dashscope",
      "description": "阿里云旗舰模型，适合复杂推理",
      "iconUrl": "/icons/qwen.svg"
    },
    {
      "modelId": "deepseek-chat",
      "displayName": "DeepSeek Chat",
      "provider": "deepseek",
      "description": "高性价比推理模型",
      "iconUrl": "/icons/deepseek.svg"
    },
    {
      "modelId": "gpt-4o",
      "displayName": "GPT-4o",
      "provider": "openai",
      "description": "OpenAI 旗舰多模态模型",
      "iconUrl": "/icons/openai.svg"
    }
  ]
}
```

### 4.2 创建会话（带模型选择）

```
POST /ai_friend/session
Body: { "modelId": "deepseek-chat" }   // 新增 modelId 参数
Response: { "code": 0, "data": { "chatId": "xxx", "modelId": "deepseek-chat" } }
```

### 4.3 会话列表（返回模型信息）

```
GET /ai_friend/session/list
Response 中每个 session 新增:
{
  "chatId": "xxx",
  "modelId": "deepseek-chat",
  "modelDisplayName": "DeepSeek Chat",
  ...
}
```

---

## 五、实施计划（分阶段）

### Phase 1：基础设施（升级 + 数据库）
- [ ] 升级 Spring AI Alibaba 到 1.0 GA，解决 Breaking Changes
- [ ] 新增 `spring-ai-starter-model-openai` 依赖
- [ ] 处理 DashScope + OpenAI 双 starter 的 Bean 冲突（`spring.ai.model.chat` 配置）
- [ ] 创建 `ai_model_config` 表，插入初始模型数据
- [ ] `chat_session` 表新增 `model_id` 列，默认 `'qwen3-max'`
- [ ] 实现 `AiModelConfig` JPA Entity + Repository
- [ ] 实现 `ApiKeyEncryptor` AES 加密组件

### Phase 2：核心模型路由
- [ ] 实现 `ChatModelRegistry`（模型查找 + 缓存 + mutate 派生）
- [ ] 改造 `StudyFriend`：从单 ChatClient 改为根据 modelId 动态构建
- [ ] 确保 ChatMemory 跨模型共享（同一会话，切换模型后记忆不丢失）
- [ ] 改造 `StudyFriendController`：从 session 读取 modelId 传入
- [ ] 改造 `StudyFriendChatService`：创建会话时支持 modelId 参数

### Phase 3：管理后台 + API
- [ ] 模型列表查询 API：`GET /api/ai/models`
- [ ] 管理员模型管理 CRUD API（新增/编辑/启用/禁用模型）
- [ ] 管理员修改模型配置后清除 `ChatModelRegistry` 缓存
- [ ] 创建会话接口支持 `modelId` 参数
- [ ] 会话列表/详情返回 `modelId` + `modelDisplayName`

### Phase 4：QuizAgent 适配 + 测试
- [ ] QuizAgent 保持使用默认 DashScope 模型（确认不受影响）
- [ ] 各模型提供商端到端测试（DashScope / DeepSeek / OpenAI / Kimi / GLM）
- [ ] 流式输出兼容性测试（不同提供商的 SSE 格式差异处理）
- [ ] 错误处理：模型不可用时的降级策略（fallback 到默认模型或报错提示）
- [ ] API Key 无效/额度耗尽的友好错误提示

---

## 六、风险与注意事项

### 6.1 Bean 冲突处理
- `spring-ai-alibaba-starter` 和 `spring-ai-starter-model-openai` 同时存在时，会各自注册 `ChatModel` Bean
- 解决方案：通过 `spring.ai.model.chat=dashscope` 让 DashScope 作为 `@Primary`，OpenAI 的 Bean 通过 `@Qualifier("openAiChatModel")` 注入

### 6.2 版本升级风险
- M6.1 → GA 可能存在 Breaking API Changes
- 重点关注：`ChatClient.builder()` API 变化、Advisor 接口变化、配置属性 key 变化
- 建议：升级后先跑通现有单模型功能，再开发多模型

### 6.3 API Key 安全
- 所有 API Key 使用 AES-256-GCM 加密存储，主密钥从环境变量读取
- 管理 API 不返回完整 API Key（仅显示 `sk-***xxx` 格式）

### 6.4 流式兼容性
- 不同 OpenAI 兼容服务的 SSE 格式可能有微小差异
- Spring AI 的 OpenAiChatModel 已处理标准差异，但需实际测试各厂商

### 6.5 成本控制
- 不同模型价格差异大（如 GPT-4o vs DeepSeek），可考虑后续增加用量统计和配额管理
- 本期不纳入，但数据库 `chat_message.metadata` JSONB 字段可记录 token 消耗

---

## 七、不采纳的方案及理由

| 方案 | 不采纳原因 |
|------|----------|
| 全部走 OpenAI 兼容协议（包括 DashScope） | DashScope 原生 starter 提供更好的联网搜索、多模态等高级功能支持，且已有大量代码依赖 |
| Spring AI Alibaba Graph 路由节点 | `QuestionClassifierNode` 适用于 Agent 工作流级别的路由，不适合用户手动选择场景 |
| 等待官方 RoutingChatClient | GitHub Issue #1759 已被关闭（Not planned），短期内不会有官方实现 |
| 每个模型一个独立的 ChatClient Bean | 模型数量动态变化（管理员可增删），静态 Bean 不灵活 |
| Embedding 模型也支持切换 | 向量库已有 1536 维数据，切换 Embedding 模型维度不兼容，需重建索引，代价过大 |
