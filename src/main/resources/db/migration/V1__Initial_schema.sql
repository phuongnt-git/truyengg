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
CREATE TYPE comic_crawl_download_mode AS ENUM('NONE', 'PARTIAL', 'FULL');
CREATE TYPE comic_crawl_status_enum AS ENUM ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED');
CREATE TYPE chapter_crawl_status_enum AS ENUM ('PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED');
CREATE TYPE crawl_event_type_enum AS ENUM ('START', 'PAUSE', 'RESUME', 'CANCEL', 'RETRY', 'ERROR', 'STATUS_CHANGE');

CREATE TABLE IF NOT EXISTS users
(
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(100) UNIQUE NOT NULL,
    password        VARCHAR(255)        NOT NULL,
    username        VARCHAR(255),
    avatar          VARCHAR(255),
    roles           user_role_enum       NOT NULL DEFAULT 'USER',
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
    name                    VARCHAR(255)        NOT NULL,
    slug                    VARCHAR(255) UNIQUE NOT NULL,
    origin_name             TEXT,
    content                 TEXT,
    status                  status_enum         NOT NULL DEFAULT 'PENDING',
    progress_status         progress_status_enum NOT NULL DEFAULT 'ONGOING',
    thumb_url               VARCHAR(255),
    views                   BIGINT              NOT NULL DEFAULT 0,
    author                  TEXT,
    is_backed_up            BOOLEAN             NOT NULL DEFAULT FALSE,
    is_hot                  BOOLEAN             NOT NULL DEFAULT FALSE,
    backup_data             TEXT,
    likes                   BIGINT              NOT NULL DEFAULT 0,
    follows                 BIGINT              NOT NULL DEFAULT 0,
    total_chapters          INTEGER             NOT NULL DEFAULT 0,
    last_chapter_updated_at TIMESTAMP WITH TIME ZONE,
    source                  VARCHAR(500) UNIQUE,
    alternative_names       TEXT[],
    age_rating              age_rating_enum,
    gender                  comic_gender_enum,
    country                 VARCHAR(50),
    merged_comic_id         BIGINT REFERENCES comics(id),
    content_search_vector   tsvector,
    updated_at              TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP,
    updated_at_local        TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP
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
    is_downloaded BOOLEAN      NOT NULL DEFAULT FALSE,
    is_visible    BOOLEAN      NOT NULL DEFAULT TRUE,
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
    setting_key   VARCHAR(100) UNIQUE NOT NULL,
    setting_value TEXT                NOT NULL,
    description   VARCHAR(255),
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_settings_setting_key ON settings (setting_key);

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
    comic_id     BIGINT      NOT NULL REFERENCES comics (id) ON DELETE CASCADE,
    status       backup_status_enum NOT NULL DEFAULT 'PENDING',
    message      TEXT,
    progress     INTEGER     NOT NULL     DEFAULT 0,
    started_at   TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_backup_logs_comic_id ON backup_logs (comic_id);
CREATE INDEX IF NOT EXISTS idx_backup_logs_status ON backup_logs (status);

CREATE TABLE IF NOT EXISTS stories
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL,
    description TEXT,
    thumbnail   VARCHAR(255),
    categories  VARCHAR(255),
    status      story_status_enum NOT NULL DEFAULT 'ONGOING',
    user_id     BIGINT       NOT NULL    DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stories_user_id ON stories (user_id);
CREATE INDEX IF NOT EXISTS idx_stories_slug ON stories (slug);

CREATE TABLE IF NOT EXISTS recharge_history
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount         BIGINT      NOT NULL,
    payment_method VARCHAR(50),
    transaction_id VARCHAR(255),
    status         recharge_status_enum NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recharge_history_user_id ON recharge_history (user_id);
CREATE INDEX IF NOT EXISTS idx_recharge_history_status ON recharge_history (status);
CREATE INDEX IF NOT EXISTS idx_recharge_history_created_at ON recharge_history (created_at);

CREATE TABLE IF NOT EXISTS payment_history
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount       BIGINT      NOT NULL,
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
    user_agent   TEXT
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

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

CREATE TABLE IF NOT EXISTS comic_crawl
(
    id                                      UUID PRIMARY KEY                  DEFAULT gen_random_uuid(),
    status                                  comic_crawl_status_enum    NOT NULL,
    url                                     TEXT                     NOT NULL,
    download_mode                           comic_crawl_download_mode  NOT NULL DEFAULT 'FULL',
    download_chapters                       JSONB,
    part_start                              INTEGER,
    part_end                                INTEGER,
    total_chapters                          INTEGER                  NOT NULL DEFAULT 0,
    downloaded_chapters                     INTEGER                  NOT NULL DEFAULT 0,
    start_time                              TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time                                TIMESTAMP WITH TIME ZONE,
    message                                 TEXT,
    checkpoint_data                         TEXT,
    deleted_at                              TIMESTAMP WITH TIME ZONE,
    deleted_by                              BIGINT REFERENCES users (id),
    created_by                              BIGINT                   NOT NULL REFERENCES users (id),
    total_file_size_bytes                   BIGINT                            DEFAULT 0,
    total_download_time_seconds             BIGINT                            DEFAULT 0,
    total_request_count                     INTEGER                           DEFAULT 0,
    total_error_count                       INTEGER                           DEFAULT 0,
    average_download_speed_bytes_per_second DECIMAL(15, 2),
    chapter_urls                            JSONB,
    chapter_image_urls                      JSONB,
    created_at                              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comic_crawl_created_by ON comic_crawl (created_by);
CREATE INDEX IF NOT EXISTS idx_comic_crawl_status ON comic_crawl (status);
CREATE INDEX IF NOT EXISTS idx_comic_crawl_created_at ON comic_crawl (created_at);
CREATE INDEX IF NOT EXISTS idx_comic_crawl_deleted_at ON comic_crawl (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS chapter_crawl
(
    id                              BIGSERIAL PRIMARY KEY,
    crawl_id                        UUID NOT NULL REFERENCES comic_crawl(id) ON DELETE CASCADE,
    chapter_index                   INTEGER NOT NULL,
    chapter_url                     TEXT NOT NULL,
    chapter_name                    VARCHAR(255),
    status                          chapter_crawl_status_enum NOT NULL,
    total_images                    INTEGER NOT NULL DEFAULT 0,
    downloaded_images               INTEGER NOT NULL DEFAULT 0,
    file_size_bytes                 BIGINT DEFAULT 0,
    download_time_seconds           BIGINT DEFAULT 0,
    request_count                   INTEGER DEFAULT 0,
    error_count                     INTEGER DEFAULT 0,
    download_speed_bytes_per_second DECIMAL(15,2),
    retry_count                     INTEGER NOT NULL DEFAULT 0,
    error_messages                  TEXT[],
    image_paths                     JSONB,
    original_image_paths            JSONB,
    started_at                      TIMESTAMP WITH TIME ZONE,
    completed_at                    TIMESTAMP WITH TIME ZONE,
    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(crawl_id, chapter_index)
);

CREATE INDEX IF NOT EXISTS idx_chapter_crawl_id ON chapter_crawl(crawl_id);
CREATE INDEX IF NOT EXISTS idx_chapter_crawl_status ON chapter_crawl(status);
CREATE INDEX IF NOT EXISTS idx_chapter_crawl_id_status ON chapter_crawl(crawl_id, status);
CREATE INDEX IF NOT EXISTS idx_chapter_crawl_created_at ON chapter_crawl(created_at);

CREATE TABLE IF NOT EXISTS crawl_progress
(
    id                BIGSERIAL PRIMARY KEY,
    crawl_id          UUID NOT NULL REFERENCES comic_crawl(id) ON DELETE CASCADE,
    current_chapter   INTEGER NOT NULL DEFAULT 0,
    total_chapters    INTEGER NOT NULL DEFAULT 0,
    downloaded_images INTEGER NOT NULL DEFAULT 0,
    total_images      INTEGER NOT NULL DEFAULT 0,
    current_message   TEXT,
    messages          TEXT[],
    start_time        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_update       TIMESTAMP WITH TIME ZONE NOT NULL,
    elapsed_seconds   BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(crawl_id)
);

CREATE INDEX IF NOT EXISTS idx_crawl_progress_crawl_id ON crawl_progress(crawl_id);
CREATE INDEX IF NOT EXISTS idx_crawl_progress_last_update ON crawl_progress(last_update);

CREATE TABLE IF NOT EXISTS comic_crawl_checkpoints
(
    id                   BIGSERIAL PRIMARY KEY,
    crawl_id             UUID NOT NULL REFERENCES comic_crawl(id) ON DELETE CASCADE,
    current_chapter_index INTEGER NOT NULL,
    current_image_index  INTEGER,
    current_image_url    TEXT,
    crawled_chapters     TEXT[],
    chapter_progress     JSONB,
    image_urls           JSONB,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(crawl_id)
);

CREATE INDEX IF NOT EXISTS idx_checkpoints_crawl_id ON comic_crawl_checkpoints(crawl_id);

CREATE TABLE IF NOT EXISTS crawl_events
(
    id          BIGSERIAL PRIMARY KEY,
    crawl_id    UUID NOT NULL REFERENCES comic_crawl(id) ON DELETE CASCADE,
    event_type  crawl_event_type_enum NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_crawl_events_crawl_id ON crawl_events(crawl_id);
CREATE INDEX IF NOT EXISTS idx_crawl_events_event_type ON crawl_events(event_type);
CREATE INDEX IF NOT EXISTS idx_crawl_events_created_at ON crawl_events(created_at);
CREATE INDEX IF NOT EXISTS idx_crawl_events_crawl_type ON crawl_events(crawl_id, event_type);

CREATE TABLE IF NOT EXISTS category_crawl_jobs
(
    id            UUID PRIMARY KEY,
    category_url  TEXT                        NOT NULL,
    source        VARCHAR(50)                 NOT NULL,
    status        VARCHAR(20)                 NOT NULL,
    total_pages   INTEGER                     NOT NULL DEFAULT 0,
    crawled_pages INTEGER                     NOT NULL DEFAULT 0,
    total_stories INTEGER                     NOT NULL DEFAULT 0,
    crawled_stories INTEGER                   NOT NULL DEFAULT 0,
    created_by    BIGINT REFERENCES users (id) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_category_crawl_jobs_status ON category_crawl_jobs(status);
CREATE INDEX IF NOT EXISTS idx_category_crawl_jobs_created_by ON category_crawl_jobs(created_by);
CREATE INDEX IF NOT EXISTS idx_category_crawl_jobs_created_at ON category_crawl_jobs(created_at);

CREATE TABLE IF NOT EXISTS category_crawl_details
(
    id                      BIGSERIAL PRIMARY KEY,
    category_crawl_job_id   UUID REFERENCES category_crawl_jobs (id) ON DELETE CASCADE NOT NULL,
    story_url               TEXT                                    NOT NULL,
    story_title             VARCHAR(255),
    story_slug              VARCHAR(255),
    total_chapters          INTEGER                                 NOT NULL DEFAULT 0,
    crawled_chapters        INTEGER                                 NOT NULL DEFAULT 0,
    failed_chapters         INTEGER                                 NOT NULL DEFAULT 0,
    status                  VARCHAR(20)                             NOT NULL,
    error_message           TEXT,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category_crawl_job_id, story_url)
);

CREATE INDEX IF NOT EXISTS idx_category_crawl_details_job_id ON category_crawl_details(category_crawl_job_id);
CREATE INDEX IF NOT EXISTS idx_category_crawl_details_status ON category_crawl_details(status);
CREATE INDEX IF NOT EXISTS idx_category_crawl_details_story_url ON category_crawl_details(story_url);

CREATE TABLE IF NOT EXISTS story_crawl_queue
(
    id                      BIGSERIAL PRIMARY KEY,
    category_crawl_job_id   UUID REFERENCES category_crawl_jobs (id) ON DELETE CASCADE NOT NULL,
    category_crawl_detail_id BIGINT REFERENCES category_crawl_details (id) ON DELETE CASCADE,
    story_url               TEXT                                    NOT NULL,
    status                  VARCHAR(20)                             NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_story_crawl_queue_job_id ON story_crawl_queue(category_crawl_job_id);
CREATE INDEX IF NOT EXISTS idx_story_crawl_queue_status ON story_crawl_queue(status);
CREATE INDEX IF NOT EXISTS idx_story_crawl_queue_status_created ON story_crawl_queue(status, created_at);

CREATE TABLE IF NOT EXISTS category_crawl_progress
(
    category_crawl_job_id   UUID PRIMARY KEY REFERENCES category_crawl_jobs (id) ON DELETE CASCADE NOT NULL,
    current_page            INTEGER                                 NOT NULL DEFAULT 0,
    current_story_index     INTEGER                                 NOT NULL DEFAULT 0,
    total_stories           INTEGER                                 NOT NULL DEFAULT 0,
    crawled_stories         INTEGER                                 NOT NULL DEFAULT 0,
    total_chapters          INTEGER                                 NOT NULL DEFAULT 0,
    crawled_chapters        INTEGER                                 NOT NULL DEFAULT 0,
    total_images            INTEGER                                 NOT NULL DEFAULT 0,
    downloaded_images       INTEGER                                 NOT NULL DEFAULT 0,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_category_crawl_progress_job_id ON category_crawl_progress(category_crawl_job_id);

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION comics_content_search_vector_update()
RETURNS TRIGGER AS $$
DECLARE
  search_config regconfig := 'simple';
BEGIN
  IF EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'vietnamese') THEN
    search_config := 'vietnamese';
  END IF;

  NEW.content_search_vector :=
    setweight(to_tsvector(search_config, COALESCE(NEW.name, '')), 'A') ||
    setweight(to_tsvector(search_config, COALESCE(NEW.origin_name, '')), 'A') ||
    setweight(to_tsvector(search_config, array_to_string(COALESCE(NEW.alternative_names, ARRAY[]::TEXT[]), ' ')), 'B') ||
    setweight(to_tsvector(search_config, COALESCE(NEW.author, '')), 'B') ||
    setweight(to_tsvector(search_config, COALESCE(NEW.content, '')), 'C');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS comics_content_search_vector_trigger ON comics;
CREATE TRIGGER comics_content_search_vector_trigger
  BEFORE INSERT OR UPDATE ON comics
  FOR EACH ROW
  EXECUTE FUNCTION comics_content_search_vector_update();

DO $$
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
    search_config_name, search_config_name, search_config_name, search_config_name, search_config_name);
END $$;

CREATE MATERIALIZED VIEW IF NOT EXISTS comics_search_cache AS
SELECT
  c.id,
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
WHERE c.status = 'ACTIVE' AND c.merged_comic_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_comics_search_cache_vector ON comics_search_cache USING GIN (content_search_vector);

CREATE TABLE IF NOT EXISTS comic_duplicates
(
    id                BIGSERIAL PRIMARY KEY,
    primary_comic_id   BIGINT NOT NULL REFERENCES comics(id) ON DELETE CASCADE,
    duplicate_comic_id BIGINT NOT NULL REFERENCES comics(id) ON DELETE CASCADE,
    similarity_score   DECIMAL(5, 4) NOT NULL,
    merge_reason       TEXT,
    merged_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    merged_by          BIGINT REFERENCES users(id),
    UNIQUE (primary_comic_id, duplicate_comic_id)
);

CREATE INDEX IF NOT EXISTS idx_comic_duplicates_primary ON comic_duplicates(primary_comic_id);
CREATE INDEX IF NOT EXISTS idx_comic_duplicates_duplicate ON comic_duplicates(duplicate_comic_id);