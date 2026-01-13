

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

