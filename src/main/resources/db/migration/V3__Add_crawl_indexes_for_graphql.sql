-- V3: Add indexes to support GraphQL filtering, sorting, and pagination
-- These indexes optimize common query patterns used by the GraphQL API

-- Composite index for status + created_at (common filter + sort combination)
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_status_created
    ON crawl_jobs (status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Composite index for parent + type + status (children query with filters)
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_parent_type_status
    ON crawl_jobs (parent_job_id, crawl_type, status)
    WHERE deleted_at IS NULL;

-- Composite index for type + status (type filter + status filter)
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_type_status
    ON crawl_jobs (crawl_type, status)
    WHERE deleted_at IS NULL;

-- Index for root jobs query (parent is null)
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_root_created
    ON crawl_jobs (created_at DESC)
    WHERE parent_job_id IS NULL AND deleted_at IS NULL;

-- Index for depth-based queries
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_depth
    ON crawl_jobs (depth)
    WHERE deleted_at IS NULL;

-- Full-text search index for target_name, target_url, target_slug
-- Using PostgreSQL trigram for partial text matching (LIKE '%search%')
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_crawl_jobs_target_name_trgm
    ON crawl_jobs USING gin (target_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_crawl_jobs_target_url_trgm
    ON crawl_jobs USING gin (target_url gin_trgm_ops);

-- Index for failed items filtering (jobs with failures)
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_failed_items
    ON crawl_jobs (failed_items)
    WHERE failed_items > 0 AND deleted_at IS NULL;

-- Index for progress-based queries (calculated percent would need computed column)
-- For now, index on completed_items and total_items for manual calculation
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_progress
    ON crawl_jobs (total_items, completed_items)
    WHERE deleted_at IS NULL;

-- Index for retry count filtering
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_retry_count
    ON crawl_jobs (retry_count)
    WHERE retry_count > 0 AND deleted_at IS NULL;

-- Index for date range queries
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_started_at
    ON crawl_jobs (started_at DESC)
    WHERE started_at IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_crawl_jobs_completed_at
    ON crawl_jobs (completed_at DESC)
    WHERE completed_at IS NOT NULL AND deleted_at IS NULL;

-- Progress messages GIN index for searching in message arrays
CREATE INDEX IF NOT EXISTS idx_crawl_progress_messages_gin
    ON crawl_progress USING gin (messages);

-- Queue index for pending items sorted by priority
CREATE INDEX IF NOT EXISTS idx_crawl_queue_priority
    ON crawl_queue (priority DESC, created_at ASC)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

-- Analyze tables to update statistics for query planner
ANALYZE crawl_jobs;
ANALYZE crawl_progress;
ANALYZE crawl_checkpoints;
ANALYZE crawl_queue;

