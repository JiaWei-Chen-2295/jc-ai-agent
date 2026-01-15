-- 用户文档表
CREATE TABLE  IF NOT EXISTS study_friend_document (
                                       id              BIGSERIAL PRIMARY KEY,
                                       tenant_id       BIGINT NOT NULL,
                                       owner_user_id   BIGINT NOT NULL,

                                       file_name       VARCHAR(255) NOT NULL,
                                       file_path       TEXT NOT NULL,
                                       file_type       VARCHAR(32) NOT NULL,

                                       status          VARCHAR(32) NOT NULL,
    -- UPLOADED | INDEXING | INDEXED | FAILED

                                       error_message   TEXT,

                                       created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                                       updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_study_friend_document_status
    ON study_friend_document(status);
CREATE INDEX idx_study_friend_document_tenant
    ON study_friend_document(tenant_id);
CREATE INDEX idx_study_friend_document_tenant_status
    ON study_friend_document(tenant_id, status);
CREATE INDEX idx_study_friend_document_tenant_owner
    ON study_friend_document(tenant_id, owner_user_id);
--
-- 
-- 用户表
--
-- 在 PGSQL 如果没有单独配置且每加上双引号 就会把驼峰命名的字段折叠为全部小写
CREATE TABLE "user" (
                                      id            BIGSERIAL PRIMARY KEY,
                                      user_account  VARCHAR(256)                          NOT NULL,
                                      user_password VARCHAR(512)                          NOT NULL,
                                      union_id      VARCHAR(256)                          NULL,
                                      mp_open_id    VARCHAR(256)                          NULL,
                                      user_name     VARCHAR(256)                          NULL,
                                      user_avatar   VARCHAR(1024)                         NULL,
                                      user_profile  VARCHAR(512)                          NULL,
                                      user_role     VARCHAR(256) DEFAULT 'user'           NOT NULL,
                                      create_time   TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                      update_time   TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                      is_delete     SMALLINT     DEFAULT 0                 NOT NULL
);

-- 添加注释
COMMENT ON TABLE "user" IS '用户';
COMMENT ON COLUMN "user".id IS 'id';
COMMENT ON COLUMN "user".user_account IS '账号';
COMMENT ON COLUMN "user".user_password IS '密码';
COMMENT ON COLUMN "user".union_id IS '微信开放平台id';
COMMENT ON COLUMN "user".mp_open_id IS '公众号openId';
COMMENT ON COLUMN "user".user_name IS '用户昵称';
COMMENT ON COLUMN "user".user_avatar IS '用户头像';
COMMENT ON COLUMN "user".user_profile IS '用户简介';
COMMENT ON COLUMN "user".user_role IS '用户角色：user/admin/ban';
COMMENT ON COLUMN "user".create_time IS '创建时间';
COMMENT ON COLUMN "user".update_time IS '更新时间';
COMMENT ON COLUMN "user".is_delete IS '是否删除';

-- 创建索引
CREATE INDEX idx_union_id ON "user" (union_id);
--
-- 租户表
--
CREATE TABLE IF NOT EXISTS tenant (
                                      id            BIGSERIAL PRIMARY KEY,
                                      tenant_name   VARCHAR(128)                          NOT NULL,
                                      tenant_type   VARCHAR(32)                           NOT NULL,
                                      owner_user_id BIGINT                                NOT NULL,
                                      create_time   TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                      update_time   TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                      is_delete     SMALLINT     DEFAULT 0                 NOT NULL
);

CREATE INDEX idx_tenant_owner_user_id ON tenant (owner_user_id);
CREATE INDEX idx_tenant_type ON tenant (tenant_type);

--
-- 租户成员表
--
CREATE TABLE IF NOT EXISTS tenant_user (
                                           id          BIGSERIAL PRIMARY KEY,
                                           tenant_id   BIGINT                                NOT NULL,
                                           user_id     BIGINT                                NOT NULL,
                                           role        VARCHAR(32)                           NOT NULL,
                                           create_time TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                           update_time TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                           is_delete   SMALLINT     DEFAULT 0                 NOT NULL,
                                           UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_tenant_user_tenant_id ON tenant_user (tenant_id);
CREATE INDEX idx_tenant_user_user_id ON tenant_user (user_id);

-- StudyFriend 会话表
CREATE TABLE IF NOT EXISTS chat_session (
                                            chat_id         VARCHAR(64) PRIMARY KEY,
                                            tenant_id       BIGINT NOT NULL,
                                            user_id         BIGINT NOT NULL,
                                            app_code        VARCHAR(64) NOT NULL,
                                            title           VARCHAR(255) NULL,
                                            last_message_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                            created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                                            updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                                            is_deleted      SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_chat_session_tenant_user_last
    ON chat_session(tenant_id, user_id, last_message_at DESC);
CREATE INDEX idx_chat_session_last_message_at
    ON chat_session(last_message_at DESC);

-- StudyFriend 消息表
CREATE TABLE IF NOT EXISTS chat_message (
                                      id               BIGSERIAL PRIMARY KEY,
                                      chat_id          VARCHAR(64) NOT NULL,
                                      tenant_id        BIGINT NOT NULL,
                                      user_id          BIGINT NOT NULL,
                                      role             VARCHAR(32) NOT NULL,
                                      client_message_id VARCHAR(64) NULL,
                                      content          TEXT NOT NULL,
                                      metadata         JSONB NULL,
                                      created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_message_chat_id_id
    ON chat_message(chat_id, id);
CREATE INDEX idx_chat_message_tenant_user
    ON chat_message(tenant_id, user_id);
CREATE UNIQUE INDEX uq_chat_message_client_id
    ON chat_message(chat_id, user_id, role, client_message_id)
    WHERE client_message_id IS NOT NULL;
