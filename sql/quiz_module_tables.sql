-- =============================================
-- 智能测验系统 - 数据库表设计 (JPA 参考文档)
-- 
-- 说明：
-- 1. 此文件作为参考文档，实际表结构由 JPA Entity 定义
-- 2. 所有表使用 UUID 作为主键
-- 3. 所有表支持软删除 (is_delete)
-- 4. 时间字段统一命名为 create_time / update_time
-- 5. 不使用数据库层面外键约束，由 JPA @ManyToOne 管理关系
-- 
-- 开发模式: spring.jpa.hibernate.ddl-auto=update
-- 生产模式: spring.jpa.hibernate.ddl-auto=validate
-- =============================================

-- 前置依赖
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =============================================
-- 1. 测验会话表 (quiz_session)
-- 管理测验会话生命周期
-- JPA Entity: QuizSession.java
-- =============================================
CREATE TABLE IF NOT EXISTS quiz_session (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    
    -- 测验配置
    quiz_mode       VARCHAR(32) NOT NULL DEFAULT 'ADAPTIVE',  -- EASY, MEDIUM, HARD, ADAPTIVE
    document_scope  JSONB NULL,                                -- 涉及的文档ID列表: List<Long>
    
    -- 会话状态
    status          VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, PAUSED, TIMEOUT, ABANDONED
    current_question_no INT NOT NULL DEFAULT 0,
    total_questions INT NOT NULL DEFAULT 0,
    score           INT NOT NULL DEFAULT 0,
    
    -- Agent 状态 (缓存 Caffeine 缓存的持久化副本)
    agent_state     JSONB NULL,                                -- Agent 状态快照: Map<String, Object>
    
    -- 时间戳 (统一命名)
    started_at      TIMESTAMP NULL,
    completed_at    TIMESTAMP NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- 软删除
    is_delete       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_quiz_session_tenant_user ON quiz_session(tenant_id, user_id) WHERE is_delete = 0;
CREATE INDEX idx_quiz_session_status ON quiz_session(status) WHERE is_delete = 0;
CREATE INDEX idx_quiz_session_user_time ON quiz_session(tenant_id, user_id, create_time DESC) WHERE is_delete = 0;

COMMENT ON TABLE quiz_session IS '测验会话表 - JPA Entity: QuizSession';
COMMENT ON COLUMN quiz_session.id IS 'UUID主键，同时作为业务ID';
COMMENT ON COLUMN quiz_session.document_scope IS '本次测验涉及的文档ID列表 (JSONB -> List<Long>)';
COMMENT ON COLUMN quiz_session.agent_state IS 'Agent状态快照 (JSONB -> Map<String, Object>)';

-- =============================================
-- 2. 测验题目表 (quiz_question)
-- 存储 Agent 生成的题目
-- JPA Entity: QuizQuestion.java
-- =============================================
CREATE TABLE IF NOT EXISTS quiz_question (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    session_id      UUID NOT NULL,                  -- 关联 quiz_session，由 JPA @ManyToOne 管理
    tenant_id       BIGINT NOT NULL,
    
    -- 题目内容
    question_no     INT NOT NULL,
    question_text   TEXT NOT NULL,
    question_type   VARCHAR(32) NOT NULL,           -- 题型: SINGLE_CHOICE, MULTIPLE_SELECT, TRUE_FALSE, FILL_IN_BLANK, SHORT_ANSWER, EXPLANATION, MATCHING, ORDERING, CODE_COMPLETION
    options         JSONB NULL,                     -- 选项数据: 选择题为List<String>; 连线题为List<{left,right}>; 判断题/填空题/主观题为null
    correct_answer  TEXT NOT NULL,
    explanation     TEXT NULL,                      -- 正确答案的解释
    
    -- 关联知识点
    related_concept VARCHAR(256) NULL,              -- 关联的知识点名称
    
    -- 来源
    source_doc_id   BIGINT NULL,                    -- 来源文档ID
    source_chunk_id UUID NULL,                      -- 来源向量块ID (关联 study_friends)
    
    -- 难度
    difficulty      VARCHAR(32) NOT NULL DEFAULT 'MEDIUM', -- EASY, MEDIUM, HARD
    
    -- 时间戳
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- 软删除
    is_delete       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_quiz_question_session ON quiz_question(session_id) WHERE is_delete = 0;
CREATE INDEX idx_quiz_question_tenant ON quiz_question(tenant_id) WHERE is_delete = 0;
CREATE INDEX idx_quiz_question_type ON quiz_question(question_type) WHERE is_delete = 0;

COMMENT ON TABLE quiz_question IS '测验题目表 - JPA Entity: QuizQuestion';
COMMENT ON COLUMN quiz_question.session_id IS '关联会话，由JPA @ManyToOne管理，无数据库外键';
COMMENT ON COLUMN quiz_question.question_type IS '题型: SINGLE_CHOICE(单选), MULTIPLE_SELECT(多选), TRUE_FALSE(判断), FILL_IN_BLANK(填空), SHORT_ANSWER(简答), EXPLANATION(解释), MATCHING(连线), ORDERING(排序), CODE_COMPLETION(代码补全)';
COMMENT ON COLUMN quiz_question.options IS '选择题选项 (JSONB -> List<String>)';
COMMENT ON COLUMN quiz_question.related_concept IS '关联的知识点名称，用于知识缺口分析';

-- =============================================
-- 3. 题目回答表 (question_response)
-- 记录用户的每次回答，用于认知分析
-- JPA Entity: QuestionResponse.java
-- =============================================
CREATE TABLE IF NOT EXISTS question_response (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    session_id      UUID NOT NULL,                  -- 关联 quiz_session
    question_id     UUID NOT NULL,                  -- 关联 quiz_question
    tenant_id       BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    
    -- 回答内容
    user_answer     TEXT NOT NULL,
    is_correct      BOOLEAN NOT NULL,
    score           INT NOT NULL DEFAULT 0,         -- 部分正确的情况，0-100
    
    -- 认知指标 (用于三维建模)
    response_time_ms INT NOT NULL,                  -- 响应时间 -> 认知负荷
    hesitation_detected BOOLEAN NOT NULL DEFAULT FALSE, -- 是否检测到犹豫
    confusion_detected  BOOLEAN NOT NULL DEFAULT FALSE, -- 是否检测到困惑
    
    -- Agent 反馈
    feedback        TEXT NULL,                      -- Agent 生成的反馈
    agent_action    VARCHAR(32) NULL,               -- CONTINUE, REMEDIATE, EXPAND, FINISH
    concept_mastery VARCHAR(32) NULL,               -- MASTERED, PARTIAL, UNMASTERED
    
    -- 时间戳
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- 软删除
    is_delete       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_question_response_session ON question_response(session_id) WHERE is_delete = 0;
CREATE INDEX idx_question_response_question ON question_response(question_id) WHERE is_delete = 0;
CREATE INDEX idx_question_response_user ON question_response(user_id) WHERE is_delete = 0;

COMMENT ON TABLE question_response IS '题目回答表 - JPA Entity: QuestionResponse';
COMMENT ON COLUMN question_response.response_time_ms IS '响应时间(毫秒)，用于计算认知负荷';
COMMENT ON COLUMN question_response.agent_action IS 'Agent在此回答后的决策: CONTINUE/REMEDIATE/EXPAND/FINISH';
COMMENT ON COLUMN question_response.concept_mastery IS '概念掌握程度: MASTERED/PARTIAL/UNMASTERED';

-- =============================================
-- 4. 用户知识状态表 (user_knowledge_state)
-- 三维认知模型: 理解深度 + 认知负荷 + 稳定性
-- JPA Entity: UserKnowledgeState.java
-- =============================================
CREATE TABLE IF NOT EXISTS user_knowledge_state (
    id                   UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    tenant_id            BIGINT NOT NULL,
    user_id              BIGINT NOT NULL,
    
    -- 知识主题 (可以是文档ID或概念名称)
    topic_type           VARCHAR(32) NOT NULL,      -- DOCUMENT, CONCEPT
    topic_id             VARCHAR(128) NOT NULL,     -- 文档ID 或 概念哈希
    topic_name           VARCHAR(256) NULL,         -- 可读名称
    
    -- 三维认知模型 (0-100)
    understanding_depth  INT NOT NULL DEFAULT 50,   -- 理解深度 (≥70 为达标)
    cognitive_load_score INT NOT NULL DEFAULT 50,   -- 认知负荷 (≤40 为达标，越低越好)
    stability_score      INT NOT NULL DEFAULT 50,   -- 稳定性 (≥70 为达标)
    
    -- 统计
    total_questions      INT NOT NULL DEFAULT 0,
    correct_answers      INT NOT NULL DEFAULT 0,
    
    -- 时间戳
    create_time          TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time          TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- 软删除
    is_delete            SMALLINT NOT NULL DEFAULT 0,
    
    -- 业务唯一约束
    UNIQUE(tenant_id, user_id, topic_type, topic_id)
);

CREATE INDEX idx_user_knowledge_state_user ON user_knowledge_state(tenant_id, user_id) WHERE is_delete = 0;
CREATE INDEX idx_user_knowledge_state_topic ON user_knowledge_state(topic_type, topic_id) WHERE is_delete = 0;

COMMENT ON TABLE user_knowledge_state IS '用户知识状态表(三维认知模型) - JPA Entity: UserKnowledgeState';
COMMENT ON COLUMN user_knowledge_state.understanding_depth IS '理解深度: 是否能解释原因/边界 (0-100, ≥70达标)';
COMMENT ON COLUMN user_knowledge_state.cognitive_load_score IS '认知负荷: 回答吃力程度 (0-100, ≤40达标，越低越好)';
COMMENT ON COLUMN user_knowledge_state.stability_score IS '稳定性: 错误重复率的倒数 (0-100, ≥70达标)';

-- =============================================
-- 5. 未掌握知识表 (unmastered_knowledge)
-- 结构化存储知识缺口
-- JPA Entity: UnmasteredKnowledge.java
-- =============================================
CREATE TABLE IF NOT EXISTS unmastered_knowledge (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    
    -- 知识缺口信息
    concept_name    VARCHAR(256) NOT NULL,
    gap_type        VARCHAR(32) NULL,               -- CONCEPTUAL, PROCEDURAL, BOUNDARY
    gap_description TEXT NULL,                      -- AI 生成的缺口描述
    root_cause      TEXT NULL,                      -- 可能的根本原因
    severity        VARCHAR(32) NULL,               -- HIGH, MEDIUM, LOW
    
    -- 来源
    source_doc_id     BIGINT NULL,                  -- 来源文档ID
    source_session_id UUID NULL,                    -- 来源测验会话ID
    
    -- 状态
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, RESOLVED
    failure_count   INT NOT NULL DEFAULT 1,         -- 失败次数
    
    -- 时间戳
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP NULL,
    
    -- 软删除
    is_delete       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_unmastered_knowledge_user ON unmastered_knowledge(tenant_id, user_id) WHERE is_delete = 0;
CREATE INDEX idx_unmastered_knowledge_status ON unmastered_knowledge(status) WHERE is_delete = 0;
CREATE INDEX idx_unmastered_knowledge_concept ON unmastered_knowledge(concept_name) WHERE is_delete = 0;

COMMENT ON TABLE unmastered_knowledge IS '未掌握知识表(知识缺口) - JPA Entity: UnmasteredKnowledge';
COMMENT ON COLUMN unmastered_knowledge.gap_type IS '缺口类型: CONCEPTUAL(概念)/PROCEDURAL(程序)/BOUNDARY(边界)';
COMMENT ON COLUMN unmastered_knowledge.gap_description IS 'Agent生成的缺口描述，如"混淆了重载与重写"';

-- =============================================
-- 6. Agent 执行日志表 (agent_execution_log)
-- 记录 Agent 的 Thought-Action-Observation 循环
-- 用于调试和分析
-- JPA Entity: AgentExecutionLog.java
-- =============================================
CREATE TABLE IF NOT EXISTS agent_execution_log (
    id              UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    session_id      UUID NOT NULL,                  -- 关联 quiz_session
    tenant_id       BIGINT NOT NULL,
    
    -- ReAct 循环信息
    iteration       INT NOT NULL,
    phase           VARCHAR(32) NOT NULL,           -- THOUGHT, ACTION, OBSERVATION
    
    -- 内容
    tool_name       VARCHAR(64) NULL,               -- 调用的工具名称
    input_data      JSONB NULL,                     -- 输入数据: Map<String, Object>
    output_data     JSONB NULL,                     -- 输出数据: Map<String, Object>
    
    -- 性能
    execution_time_ms INT NULL,
    
    -- 时间戳
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- 软删除
    is_delete       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_agent_execution_log_session ON agent_execution_log(session_id) WHERE is_delete = 0;
CREATE INDEX idx_agent_execution_log_phase ON agent_execution_log(phase) WHERE is_delete = 0;

COMMENT ON TABLE agent_execution_log IS 'Agent执行日志(ReAct循环记录) - JPA Entity: AgentExecutionLog';
COMMENT ON COLUMN agent_execution_log.phase IS 'ReAct阶段: THOUGHT/ACTION/OBSERVATION';
COMMENT ON COLUMN agent_execution_log.input_data IS '输入数据 (JSONB -> Map<String, Object>)';
COMMENT ON COLUMN agent_execution_log.output_data IS '输出数据 (JSONB -> Map<String, Object>)';
