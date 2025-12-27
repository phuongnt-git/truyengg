CREATE TYPE status_enum AS ENUM ('PENDING', 'ACTIVE', 'DUPLICATE_DETECTED', 'MERGED', 'ARCHIVED');
CREATE TYPE progress_status_enum AS ENUM ('ONGOING', 'COMPLETED', 'ONHOLD', 'DROPPED', 'COMING_SOON');
CREATE TYPE user_role_enum AS ENUM ('ADMIN', 'USER', 'TRANSLATOR');
CREATE TYPE user_gender_enum AS ENUM ('MALE', 'FEMALE');
CREATE TYPE comic_gender_enum AS ENUM ('ALL', 'MALE', 'FEMALE', 'BOTH');
CREATE TYPE age_rating_enum AS ENUM ('ALL', '13+', '16+', '18+', 'MATURE');
CREATE TYPE backup_status_enum AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
CREATE TYPE story_status_enum AS ENUM ('ONGOING', 'COMPLETED');
CREATE TYPE recharge_status_enum AS ENUM ('PENDING', 'COMPLETED', 'FAILED');
CREATE TYPE payment_type_enum AS ENUM ('RECHARGE', 'PURCHASE', 'OTHER');
CREATE TYPE crawl_type AS ENUM ('CATEGORY', 'COMIC', 'CHAPTER', 'IMAGE');
CREATE TYPE crawl_status AS ENUM ('PENDING', 'RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE queue_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED', 'DELAYED');
CREATE TYPE download_mode AS ENUM ('FULL', 'UPDATE', 'PARTIAL', 'NONE');
CREATE TYPE setting_value_type AS ENUM ('STRING', 'INT', 'LONG', 'DOUBLE', 'BOOLEAN', 'JSON', 'URL', 'EMAIL', 'PASSWORD', 'SECRET');

CREATE TABLE IF NOT EXISTS users
(
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(100) UNIQUE NOT NULL,
    password        VARCHAR(255)        NOT NULL,
    username        VARCHAR(255),
    avatar          VARCHAR(255),
    roles           user_role_enum      NOT NULL DEFAULT 'USER',
    banned_until    TIMESTAMP WITH TIME ZONE,
    xu              BIGINT              NOT NULL DEFAULT 0,
    points          BIGINT              NOT NULL DEFAULT 0,
    level           INTEGER             NOT NULL DEFAULT 1,
    progress        INTEGER             NOT NULL DEFAULT 0,
    last_name       VARCHAR(255),
    first_name      VARCHAR(255),
    gender          user_gender_enum,
    type_rank       INTEGER             NOT NULL DEFAULT 0,
    reset_token     VARCHAR(100),
    failed_attempts INTEGER             NOT NULL DEFAULT 0,
    lockout_time    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_roles ON users (roles);

CREATE TABLE IF NOT EXISTS comics
(
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(255)         NOT NULL,
    slug                    VARCHAR(255) UNIQUE  NOT NULL,
    origin_name             TEXT,
    content                 TEXT,
    status                  status_enum          NOT NULL DEFAULT 'PENDING',
    progress_status         progress_status_enum NOT NULL DEFAULT 'ONGOING',
    thumb_url               VARCHAR(255),
    views                   BIGINT               NOT NULL DEFAULT 0,
    author                  TEXT,
    is_backed_up            BOOLEAN              NOT NULL DEFAULT FALSE,
    is_hot                  BOOLEAN              NOT NULL DEFAULT FALSE,
    backup_data             TEXT,
    likes                   BIGINT               NOT NULL DEFAULT 0,
    follows                 BIGINT               NOT NULL DEFAULT 0,
    total_chapters          INTEGER              NOT NULL DEFAULT 0,
    last_chapter_updated_at TIMESTAMP WITH TIME ZONE,
    source                  VARCHAR(500) UNIQUE,
    cover_hash              VARCHAR(64),
    cover_blurhash          VARCHAR(50),
    alternative_names       TEXT[],
    age_rating              age_rating_enum,
    gender                  comic_gender_enum,
    country                 VARCHAR(50),
    merged_comic_id         BIGINT REFERENCES comics (id),
    content_search_vector   tsvector,
    updated_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE      DEFAULT CURRENT_TIMESTAMP,
    updated_at_local        TIMESTAMP WITH TIME ZONE      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comics_slug ON comics (slug);
CREATE INDEX IF NOT EXISTS idx_comics_name ON comics (name);
CREATE INDEX IF NOT EXISTS idx_comics_status ON comics (status);
CREATE INDEX IF NOT EXISTS idx_comics_progress_status ON comics (progress_status);
CREATE INDEX IF NOT EXISTS idx_comics_is_hot ON comics (is_hot);
CREATE INDEX IF NOT EXISTS idx_comics_source ON comics (source) WHERE source IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comics_merged_comic_id ON comics (merged_comic_id) WHERE merged_comic_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comics_alternative_names ON comics USING GIN (alternative_names);
CREATE INDEX IF NOT EXISTS idx_comics_content_fulltext ON comics USING GIN (content_search_vector);

CREATE TABLE IF NOT EXISTS categories
(
    id          BIGSERIAL PRIMARY KEY,
    category_id VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100)       NOT NULL,
    slug        VARCHAR(100)       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_categories_category_id ON categories (category_id);
CREATE INDEX IF NOT EXISTS idx_categories_slug ON categories (slug);

CREATE TABLE IF NOT EXISTS comic_categories
(
    comic_id    BIGINT NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (comic_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_comic_categories_comic_id ON comic_categories (comic_id);
CREATE INDEX IF NOT EXISTS idx_comic_categories_category_id ON comic_categories (category_id);

CREATE TABLE IF NOT EXISTS chapters
(
    id            BIGSERIAL PRIMARY KEY,
    comic_id      BIGINT      NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    chapter_name  VARCHAR(50) NOT NULL,
    chapter_title VARCHAR(255)             DEFAULT '',
    source        VARCHAR(500),
    is_backed_up  BOOLEAN     NOT NULL     DEFAULT FALSE,
    deleted_at    TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (comic_id, chapter_name)
);

CREATE INDEX IF NOT EXISTS idx_chapters_comic_id ON chapters (comic_id);
CREATE INDEX IF NOT EXISTS idx_chapters_chapter_name ON chapters (chapter_name);
CREATE INDEX IF NOT EXISTS idx_chapters_comic_created ON chapters (comic_id, created_at);
CREATE INDEX IF NOT EXISTS idx_chapters_source ON chapters (source) WHERE source IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_chapters_deleted_at ON chapters (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS chapter_images
(
    id            BIGSERIAL PRIMARY KEY,
    chapter_id    BIGINT       NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    path          VARCHAR(500) NOT NULL,
    original_url  VARCHAR(500),
    image_order   INTEGER      NOT NULL,
    manual_order  INTEGER,
    is_downloaded BOOLEAN      NOT NULL    DEFAULT FALSE,
    is_visible    BOOLEAN      NOT NULL    DEFAULT TRUE,
    blurhash      VARCHAR(50),
    deleted_at    TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chapter_images_chapter_id ON chapter_images (chapter_id);
CREATE INDEX IF NOT EXISTS idx_chapter_images_image_order ON chapter_images (chapter_id, image_order);
CREATE INDEX IF NOT EXISTS idx_chapter_images_manual_order ON chapter_images (chapter_id, manual_order);
CREATE INDEX IF NOT EXISTS idx_chapter_images_visible ON chapter_images (chapter_id, is_visible) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_chapter_images_deleted_at ON chapter_images (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS comments
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    comic_id   BIGINT  NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    parent_id  BIGINT  REFERENCES comments (id) ON DELETE SET NULL,
    content    TEXT    NOT NULL,
    likes      INTEGER NOT NULL         DEFAULT 0,
    dislikes   INTEGER NOT NULL         DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comments_user_id ON comments (user_id);
CREATE INDEX IF NOT EXISTS idx_comments_comic_id ON comments (comic_id);
CREATE INDEX IF NOT EXISTS idx_comments_parent_id ON comments (parent_id);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON comments (created_at);

CREATE TABLE IF NOT EXISTS user_follows
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    comic_id   BIGINT NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, comic_id)
);

CREATE INDEX IF NOT EXISTS idx_user_follows_user_id ON user_follows (user_id);
CREATE INDEX IF NOT EXISTS idx_user_follows_comic_id ON user_follows (comic_id);

CREATE TABLE IF NOT EXISTS reading_history
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    comic_id     BIGINT      NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    chapter_id   BIGINT REFERENCES chapters (id),
    slug         VARCHAR(255),
    name         VARCHAR(255),
    thumb_url    VARCHAR(255),
    chapter_name VARCHAR(20) NOT NULL,
    last_read_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, comic_id)
);

CREATE INDEX IF NOT EXISTS idx_reading_history_user_id ON reading_history (user_id);
CREATE INDEX IF NOT EXISTS idx_reading_history_comic_id ON reading_history (comic_id);
CREATE INDEX IF NOT EXISTS idx_reading_history_last_read_at ON reading_history (last_read_at);

CREATE TABLE IF NOT EXISTS settings
(
    id            BIGSERIAL PRIMARY KEY,
    category_id   INT,
    key           VARCHAR(100)       NOT NULL,
    full_key      VARCHAR(255) UNIQUE,
    value         TEXT               NOT NULL,
    value_type    setting_value_type NOT NULL DEFAULT 'STRING',
    default_value TEXT,
    constraints   JSONB,
    is_required   BOOLEAN                     DEFAULT false,
    is_sensitive  BOOLEAN                     DEFAULT false,
    is_readonly   BOOLEAN                     DEFAULT false,
    description   TEXT,
    updated_by    BIGINT,
    created_at    TIMESTAMPTZ                 DEFAULT NOW(),
    updated_at    TIMESTAMPTZ                 DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chapter_errors
(
    id            BIGSERIAL PRIMARY KEY,
    chapter_id    BIGINT      NOT NULL REFERENCES chapters (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    error_type    VARCHAR(50) NOT NULL,
    error_message TEXT        NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chapter_errors_chapter_id ON chapter_errors (chapter_id);
CREATE INDEX IF NOT EXISTS idx_chapter_errors_user_id ON chapter_errors (user_id);

CREATE TABLE IF NOT EXISTS comic_view_logs
(
    id              BIGSERIAL PRIMARY KEY,
    comic_id        BIGINT  NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    views_increment INTEGER NOT NULL         DEFAULT 0,
    log_date        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comic_view_logs_comic_id ON comic_view_logs (comic_id);
CREATE INDEX IF NOT EXISTS idx_comic_view_logs_log_date ON comic_view_logs (log_date);

CREATE TABLE IF NOT EXISTS backup_logs
(
    id           BIGSERIAL PRIMARY KEY,
    comic_id     BIGINT             NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    status       backup_status_enum NOT NULL DEFAULT 'PENDING',
    message      TEXT,
    progress     INTEGER            NOT NULL DEFAULT 0,
    started_at   TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE    DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_backup_logs_comic_id ON backup_logs (comic_id);
CREATE INDEX IF NOT EXISTS idx_backup_logs_status ON backup_logs (status);

CREATE TABLE IF NOT EXISTS stories
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255)      NOT NULL,
    slug        VARCHAR(255)      NOT NULL,
    description TEXT,
    thumbnail   VARCHAR(255),
    categories  VARCHAR(255),
    status      story_status_enum NOT NULL DEFAULT 'ONGOING',
    user_id     BIGINT            NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE   DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE   DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stories_user_id ON stories (user_id);
CREATE INDEX IF NOT EXISTS idx_stories_slug ON stories (slug);

CREATE TABLE IF NOT EXISTS recharge_history
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT               NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount         BIGINT               NOT NULL,
    payment_method VARCHAR(50),
    transaction_id VARCHAR(255),
    status         recharge_status_enum NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP WITH TIME ZONE      DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recharge_history_user_id ON recharge_history (user_id);
CREATE INDEX IF NOT EXISTS idx_recharge_history_status ON recharge_history (status);
CREATE INDEX IF NOT EXISTS idx_recharge_history_created_at ON recharge_history (created_at);

CREATE TABLE IF NOT EXISTS payment_history
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT            NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount       BIGINT            NOT NULL,
    payment_type payment_type_enum NOT NULL,
    description  TEXT,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_history_user_id ON payment_history (user_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_payment_type ON payment_history (payment_type);
CREATE INDEX IF NOT EXISTS idx_payment_history_created_at ON payment_history (created_at);

CREATE TABLE IF NOT EXISTS refresh_tokens
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token        VARCHAR(500)             NOT NULL UNIQUE,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    jti          VARCHAR(64) UNIQUE,
    is_revoked   BOOLEAN                  DEFAULT false
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_jti ON refresh_tokens (jti) WHERE jti IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked ON refresh_tokens (is_revoked) WHERE is_revoked = false;

CREATE TABLE IF NOT EXISTS token_blacklist
(
    id         BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(255)             NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    user_id    BIGINT                   REFERENCES users (id) ON DELETE SET NULL,
    reason     VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_token_blacklist_token_hash ON token_blacklist (token_hash);
CREATE INDEX IF NOT EXISTS idx_token_blacklist_expires_at ON token_blacklist (expires_at);

CREATE TABLE IF NOT EXISTS crawl_jobs
(
    id              UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    crawl_type      crawl_type    NOT NULL,
    parent_job_id   UUID REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    root_job_id     UUID REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    depth           INT           NOT NULL DEFAULT 0,
    target_url      TEXT          NOT NULL,
    target_slug     VARCHAR(255),
    target_name     VARCHAR(500),
    item_index      INT           NOT NULL DEFAULT 0,
    content_id      BIGINT        NOT NULL DEFAULT -1,
    status          crawl_status  NOT NULL DEFAULT 'PENDING',
    download_mode   download_mode NOT NULL DEFAULT 'FULL',
    total_items     INT           NOT NULL DEFAULT 0,
    completed_items INT           NOT NULL DEFAULT 0,
    failed_items    INT           NOT NULL DEFAULT 0,
    skipped_items   INT           NOT NULL DEFAULT 0,
    error_message   TEXT,
    retry_count     INT           NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    created_by      BIGINT REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_crawl_jobs_type ON crawl_jobs (crawl_type);
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_status ON crawl_jobs (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_parent ON crawl_jobs (parent_job_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_root ON crawl_jobs (root_job_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_url ON crawl_jobs (target_url);
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_created_by ON crawl_jobs (created_by);
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_active ON crawl_jobs (status)
    WHERE status IN ('PENDING', 'RUNNING', 'PAUSED') AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_deleted ON crawl_jobs (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_content ON crawl_jobs (content_id) WHERE content_id > 0;

CREATE TABLE IF NOT EXISTS crawl_settings
(
    id                UUID PRIMARY KEY REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    parallel_limit    INT         NOT NULL DEFAULT 3,
    image_quality     INT         NOT NULL DEFAULT 85,
    timeout_seconds   INT         NOT NULL DEFAULT 30,
    skip_items        INT[],
    redownload_items  INT[],
    range_start       INT         NOT NULL DEFAULT -1,
    range_end         INT         NOT NULL DEFAULT -1,
    per_item_settings JSONB,
    custom_headers    JSONB,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crawl_progress
(
    id                          UUID PRIMARY KEY REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    item_index                  INT         NOT NULL DEFAULT 0,
    item_name                   VARCHAR(500),
    item_url                    TEXT,
    total_items                 INT         NOT NULL DEFAULT 0,
    completed_items             INT         NOT NULL DEFAULT 0,
    failed_items                INT         NOT NULL DEFAULT 0,
    skipped_items               INT         NOT NULL DEFAULT 0,
    bytes_downloaded            BIGINT      NOT NULL DEFAULT 0,
    percent                     INT         NOT NULL DEFAULT 0,
    message                     TEXT,
    messages                    TEXT[]               DEFAULT '{}',
    started_at                  TIMESTAMPTZ,
    last_update_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    estimated_remaining_seconds INT         NOT NULL DEFAULT 0,
    deleted_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_crawl_progress_update ON crawl_progress (last_update_at);

CREATE TABLE IF NOT EXISTS crawl_checkpoints
(
    id                  UUID PRIMARY KEY REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    last_item_index     INT         NOT NULL DEFAULT -1,
    failed_item_indices INT[],
    failed_nested_items JSONB,
    resume_count        INT         NOT NULL DEFAULT 0,
    paused_at           TIMESTAMPTZ,
    resumed_at          TIMESTAMPTZ,
    state_snapshot      JSONB,
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS crawl_queue
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    crawl_job_id  UUID         NOT NULL REFERENCES crawl_jobs (id) ON DELETE CASCADE,
    crawl_type    crawl_type   NOT NULL,
    target_url    TEXT         NOT NULL,
    target_name   VARCHAR(500),
    item_index    INT          NOT NULL DEFAULT 0,
    priority      INT          NOT NULL DEFAULT 0,
    status        queue_status NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    max_retries   INT          NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMPTZ,
    error_message TEXT,
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_crawl_queue_job ON crawl_queue (crawl_job_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_queue_status ON crawl_queue (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_queue_pending ON crawl_queue (status, priority DESC)
    WHERE status = 'PENDING' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_queue_delayed ON crawl_queue (next_retry_at)
    WHERE status = 'DELAYED' AND deleted_at IS NULL;

CREATE OR REPLACE FUNCTION update_crawl_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_crawl_jobs_updated_at
    BEFORE UPDATE
    ON crawl_jobs
    FOR EACH ROW
EXECUTE FUNCTION update_crawl_updated_at();

CREATE TRIGGER trigger_crawl_settings_updated_at
    BEFORE UPDATE
    ON crawl_settings
    FOR EACH ROW
EXECUTE FUNCTION update_crawl_updated_at();

CREATE TRIGGER trigger_crawl_checkpoints_updated_at
    BEFORE UPDATE
    ON crawl_checkpoints
    FOR EACH ROW
EXECUTE FUNCTION update_crawl_updated_at();

CREATE TRIGGER trigger_crawl_queue_updated_at
    BEFORE UPDATE
    ON crawl_queue
    FOR EACH ROW
EXECUTE FUNCTION update_crawl_updated_at();

CREATE TABLE setting_categories
(
    id          SERIAL PRIMARY KEY,
    parent_id   INT REFERENCES setting_categories (id) ON DELETE CASCADE,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    level       INT          NOT NULL DEFAULT 0,
    path        VARCHAR(255) NOT NULL UNIQUE,
    path_ids    INT[]        NOT NULL,
    is_system   BOOLEAN               DEFAULT false,
    is_active   BOOLEAN               DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_parent_code UNIQUE (parent_id, code)
);

CREATE INDEX idx_category_parent ON setting_categories (parent_id);
CREATE INDEX idx_category_path ON setting_categories (path);
CREATE INDEX idx_category_path_ids ON setting_categories USING GIN (path_ids);
CREATE INDEX idx_category_level ON setting_categories (level);

ALTER TABLE settings
    ADD CONSTRAINT fk_settings_category FOREIGN KEY (category_id) REFERENCES setting_categories (id) ON DELETE RESTRICT;
ALTER TABLE settings
    ADD CONSTRAINT fk_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE settings
    ADD CONSTRAINT uq_settings_category_key UNIQUE (category_id, key);

CREATE INDEX idx_settings_category ON settings (category_id);
CREATE INDEX idx_settings_full_key ON settings (full_key);
CREATE INDEX idx_settings_key ON settings (key);
CREATE INDEX idx_settings_type ON settings (value_type);
CREATE INDEX idx_settings_sensitive ON settings (is_sensitive) WHERE is_sensitive = true;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_settings_key_trgm ON settings USING GIN (key gin_trgm_ops);
CREATE INDEX idx_settings_desc_trgm ON settings USING GIN (description gin_trgm_ops);

CREATE TABLE qsc_key_pairs
(
    id             BIGSERIAL PRIMARY KEY,
    key_type       VARCHAR(20)  NOT NULL CHECK (key_type IN ('KYBER', 'DILITHIUM')),
    algorithm      VARCHAR(50)  NOT NULL,
    key_usage      VARCHAR(50)  NOT NULL,
    public_key     BYTEA        NOT NULL,
    private_key    BYTEA        NOT NULL,
    fingerprint    VARCHAR(128) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ  NOT NULL,
    is_active      BOOLEAN               DEFAULT TRUE,
    rotation_count INT                   DEFAULT 0
);

CREATE UNIQUE INDEX uq_qsc_key_type_usage_active ON qsc_key_pairs (key_type, key_usage) WHERE is_active = TRUE;
CREATE INDEX idx_qsc_keys_active ON qsc_key_pairs (key_type, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_qsc_keys_usage ON qsc_key_pairs (key_usage, is_active);
CREATE INDEX idx_qsc_keys_expires ON qsc_key_pairs (expires_at);
CREATE INDEX idx_qsc_keys_type ON qsc_key_pairs (key_type);
CREATE INDEX idx_qsc_keys_fingerprint ON qsc_key_pairs (fingerprint);

CREATE OR REPLACE FUNCTION comics_content_search_vector_update()
    RETURNS TRIGGER AS
$$
DECLARE
    search_config regconfig := 'simple';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'vietnamese') THEN
        search_config := 'vietnamese';
    END IF;
    NEW.content_search_vector :=
            setweight(to_tsvector(search_config, COALESCE(NEW.name, '')), 'A') ||
            setweight(to_tsvector(search_config, COALESCE(NEW.origin_name, '')), 'A') ||
            setweight(
                    to_tsvector(search_config, array_to_string(COALESCE(NEW.alternative_names, ARRAY []::TEXT[]), ' ')),
                    'B') ||
            setweight(to_tsvector(search_config, COALESCE(NEW.author, '')), 'B') ||
            setweight(to_tsvector(search_config, COALESCE(NEW.content, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS comics_content_search_vector_trigger ON comics;
CREATE TRIGGER comics_content_search_vector_trigger
    BEFORE INSERT OR UPDATE
    ON comics
    FOR EACH ROW
EXECUTE FUNCTION comics_content_search_vector_update();

DO
$$
    DECLARE
        search_config_name TEXT := 'simple';
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'vietnamese') THEN
            search_config_name := 'vietnamese';
        END IF;
        EXECUTE format('UPDATE comics SET content_search_vector =
    setweight(to_tsvector(%L::regconfig, COALESCE(name, '''')), ''A'') ||
    setweight(to_tsvector(%L::regconfig, COALESCE(origin_name, '''')), ''A'') ||
    setweight(to_tsvector(%L::regconfig, array_to_string(COALESCE(alternative_names, ARRAY[]::TEXT[]), '' '')), ''B'') ||
    setweight(to_tsvector(%L::regconfig, COALESCE(author, '''')), ''B'') ||
    setweight(to_tsvector(%L::regconfig, COALESCE(content, '''')), ''C'')',
                       search_config_name, search_config_name, search_config_name, search_config_name,
                       search_config_name);
    END
$$;

CREATE MATERIALIZED VIEW IF NOT EXISTS comics_search_cache AS
SELECT c.id,
       c.name,
       c.origin_name,
       c.slug,
       c.status,
       c.progress_status,
       c.thumb_url,
       c.views,
       c.likes,
       c.follows,
       c.content_search_vector,
       ts_rank(c.content_search_vector, plainto_tsquery('simple', '')) as search_rank
FROM comics c
WHERE c.status = 'ACTIVE'
  AND c.merged_comic_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_comics_search_cache_vector ON comics_search_cache USING GIN (content_search_vector);

CREATE OR REPLACE FUNCTION build_category_path()
    RETURNS TRIGGER AS
$$
DECLARE
    parent_path  VARCHAR(255);
    parent_ids   INT[];
    parent_level INT;
BEGIN
    IF NEW.parent_id IS NULL THEN
        NEW.path := NEW.code;
        NEW.path_ids := ARRAY [NEW.id];
        NEW.level := 0;
    ELSE
        SELECT path, path_ids, level
        INTO parent_path, parent_ids, parent_level
        FROM setting_categories
        WHERE id = NEW.parent_id;
        NEW.path := parent_path || '.' || NEW.code;
        NEW.path_ids := parent_ids || NEW.id;
        NEW.level := parent_level + 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_category_path
    BEFORE INSERT OR UPDATE
    ON setting_categories
    FOR EACH ROW
EXECUTE FUNCTION build_category_path();

CREATE OR REPLACE FUNCTION build_setting_full_key()
    RETURNS TRIGGER AS
$$
DECLARE
    category_path VARCHAR(255);
BEGIN
    IF NEW.category_id IS NOT NULL AND NEW.key IS NOT NULL THEN
        SELECT path
        INTO category_path
        FROM setting_categories
        WHERE id = NEW.category_id;
        NEW.full_key := category_path || '.' || NEW.key;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_setting_full_key
    BEFORE INSERT OR UPDATE
    ON settings
    FOR EACH ROW
    WHEN (NEW.category_id IS NOT NULL AND NEW.key IS NOT NULL)
EXECUTE FUNCTION build_setting_full_key();

CREATE OR REPLACE VIEW v_settings_hierarchical AS
SELECT s.id,
       s.category_id,
       c.path     AS category_path,
       c.name     AS category_name,
       c.level    AS category_level,
       s.key,
       s.full_key,
       s.value,
       s.value_type,
       s.default_value,
       s.constraints,
       s.is_required,
       s.is_sensitive,
       s.is_readonly,
       s.description,
       s.created_at,
       s.updated_at,
       u.username AS updated_by_username
FROM settings s
         JOIN setting_categories c ON s.category_id = c.id
         LEFT JOIN users u ON s.updated_by = u.id
ORDER BY c.path, s.key;

CREATE TABLE IF NOT EXISTS user_passkeys
(
    id              UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    user_id         BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    credential_id   BYTEA                    NOT NULL UNIQUE,
    public_key      BYTEA                    NOT NULL,
    sign_count      BIGINT                   NOT NULL DEFAULT 0,
    aaguid          BYTEA,
    device_name     VARCHAR(255)             NOT NULL,
    transports      TEXT[],
    is_discoverable BOOLEAN                  NOT NULL DEFAULT FALSE,
    last_used_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_passkeys_user_id ON user_passkeys (user_id);
CREATE INDEX IF NOT EXISTS idx_user_passkeys_credential_id ON user_passkeys (credential_id);
CREATE INDEX IF NOT EXISTS idx_user_passkeys_last_used ON user_passkeys (last_used_at);
