-- 用户文档表
CREATE TABLE  IF NOT EXISTS study_friend_document (
                                       id              BIGSERIAL PRIMARY KEY,

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
