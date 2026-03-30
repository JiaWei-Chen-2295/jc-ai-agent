-- =====================================================================
-- Multi-Model Chat Support Migration
-- Phase 1: ai_model_config table + chat_session.model_id column
-- =====================================================================

-- 1. 模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(32)   NOT NULL,           -- 'dashscope' | 'openai' | 'deepseek' | 'kimi' | 'glm'
    model_id        VARCHAR(64)   NOT NULL UNIQUE,    -- 业务标识，如 'qwen3-max', 'deepseek-chat', 'gpt-4o'
    model_name      VARCHAR(128)  NOT NULL,           -- 发送给 API 的实际模型名
    display_name    VARCHAR(128)  NOT NULL,           -- 前端展示名
    base_url        VARCHAR(512)  NULL,               -- OpenAI 兼容端点；DashScope 原生可为空
    completions_path VARCHAR(256) NULL,              -- 自定义 completions 路径（如智谱 /v4/chat/completions）；为空用默认 /v1/chat/completions
    api_key_enc     VARCHAR(1024) NULL,               -- AES-256-GCM 加密的 API Key；DashScope 原生可留空
    max_tokens      INTEGER       NULL,               -- 模型最大输出 token
    temperature     DECIMAL(3,2)  DEFAULT 0.70,       -- 默认温度
    description     VARCHAR(512)  NULL,               -- 模型描述文案
    icon_url        VARCHAR(512)  NULL,               -- 模型图标 URL
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_model_config_enabled
    ON ai_model_config(enabled, sort_order);

-- 2. 初始模型数据（DashScope 无需存 API Key，由环境变量管理）
INSERT INTO ai_model_config (provider, model_id, model_name, display_name,
                             base_url, completions_path, api_key_enc, enabled, sort_order, description)
VALUES
    ('dashscope', 'qwen3-max',     'qwen3-max-2026-01-23', '通义千问3 Max',
     NULL, NULL, NULL, true, 1, '阿里云百炼旗舰推理模型，擅长复杂任务'),
    ('deepseek',  'deepseek-chat', 'deepseek-chat',        'DeepSeek Chat',
     'https://api.deepseek.com', NULL, NULL, false, 2, '强推理能力，高性价比'),
    ('openai',    'gpt-4o',        'gpt-4o',               'GPT-4o',
     'https://api.openai.com',   NULL, NULL, false, 3, 'OpenAI 旗舰多模态模型'),
    ('kimi',      'kimi-chat',     'moonshot-v1-8k',       'Kimi Chat',
     'https://api.moonshot.cn', NULL, NULL, false, 4, 'Moonshot AI 长文本模型')
ON CONFLICT (model_id) DO NOTHING;

-- 3. chat_session 表新增 model_id 列（默认 qwen3-max）
ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS model_id VARCHAR(64) NOT NULL DEFAULT 'qwen3-max';

-- 4. 已有数据库追加 completions_path 列（新建库已包含该列）
ALTER TABLE ai_model_config
    ADD COLUMN IF NOT EXISTS completions_path VARCHAR(256) NULL;
