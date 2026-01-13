CREATE
EXTENSION IF NOT EXISTS vector;
CREATE
EXTENSION IF NOT EXISTS hstore;
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS study_friends
(
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content  text,
    metadata json,
    embedding vector(1536)
);

CREATE INDEX ON study_friends USING HNSW (embedding vector_cosine_ops);