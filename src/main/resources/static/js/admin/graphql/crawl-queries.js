/**
 * GraphQL Queries, Mutations, and Subscriptions for Crawl System
 */

const CrawlGraphQL = (() => {
    'use strict';

    // Use gql tagged template literal (provided by Apollo)
    const gql = window.gql || ((strings, ...values) => {
        return strings.reduce((result, str, i) => result + str + (values[i] || ''), '');
    });

    // ===== FRAGMENTS =====

    const CRAWL_JOB_FIELDS = gql`
        fragment CrawlJobFields on CrawlJob {
            id
            type
            status
            targetUrl
            targetSlug
            targetName
            downloadMode
            depth
            contentId
            itemIndex
            totalItems
            completedItems
            failedItems
            skippedItems
            percent
            retryCount
            errorMessage
            hasChildren
            childrenCount
            startedAt
            completedAt
            createdAt
            updatedAt
        }
    `;

    const PROGRESS_FIELDS = gql`
        fragment ProgressFields on CrawlProgress {
            id
            itemIndex
            itemName
            itemUrl
            totalItems
            completedItems
            failedItems
            skippedItems
            bytesDownloaded
            percent
            message
            startedAt
            lastUpdateAt
            estimatedRemainingSeconds
        }
    `;

    const CHECKPOINT_FIELDS = gql`
        fragment CheckpointFields on CrawlCheckpoint {
            id
            lastItemIndex
            failedItemIndices
            resumeCount
            pausedAt
            resumedAt
            hasFailedItems
        }
    `;

    const SETTINGS_FIELDS = gql`
        fragment SettingsFields on CrawlSettings {
            id
            parallelLimit
            imageQuality
            timeoutSeconds
            skipItems
            redownloadItems
            rangeStart
            rangeEnd
            hasRange
        }
    `;

    // ===== QUERIES =====

    const CRAWL_JOB_QUERY = gql`
        query CrawlJob($id: UUID!) {
            crawlJob(id: $id) {
                ...CrawlJobFields
                parentJob {
                    id
                    type
                    targetName
                }
                rootJob {
                    id
                    type
                    targetName
                }
                progress {
                    ...ProgressFields
                }
                checkpoint {
                    ...CheckpointFields
                }
                settings {
                    ...SettingsFields
                }
            }
        }
        ${CRAWL_JOB_FIELDS}
        ${PROGRESS_FIELDS}
        ${CHECKPOINT_FIELDS}
        ${SETTINGS_FIELDS}
    `;

    const CRAWL_JOBS_LIST_QUERY = gql`
        query CrawlJobsList(
            $first: Int
            $after: String
            $filter: CrawlJobFilter
            $sort: [CrawlJobSort!]
        ) {
            crawlJobs(first: $first, after: $after, filter: $filter, sort: $sort) {
                edges {
                    node {
                        ...CrawlJobFields
                    }
                    cursor
                }
                pageInfo {
                    hasNextPage
                    hasPreviousPage
                    startCursor
                    endCursor
                }
                totalCount
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const JOB_DETAIL_QUERY = gql`
        query JobDetail(
            $id: UUID!
            $childrenFirst: Int!
            $messagesLast: Int!
        ) {
            crawlJob(id: $id) {
                ...CrawlJobFields
                progress {
                    ...ProgressFields
                }
                checkpoint {
                    ...CheckpointFields
                }
                settings {
                    ...SettingsFields
                }
                aggregatedStats {
                    totalChapters
                    totalImages
                    totalBytes
                    byStatus {
                        pending
                        running
                        completed
                        failed
                        paused
                        cancelled
                    }
                    byType {
                        category
                        comic
                        chapter
                        image
                    }
                    avgProgress
                }
                children(first: $childrenFirst) {
                    edges {
                        node {
                            id
                            type
                            status
                            targetName
                            percent
                            hasChildren
                            childrenCount
                        }
                        cursor
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                    totalCount
                }
                messages(last: $messagesLast) {
                    edges {
                        node {
                            id
                            timestamp
                            level
                            message
                        }
                        cursor
                    }
                    pageInfo {
                        hasPreviousPage
                        startCursor
                    }
                    totalCount
                }
                failedItemsList {
                    totalCount
                    items {
                        index
                        url
                        name
                        error
                        retryCount
                    }
                }
                images {
                    index
                    originalUrl
                    path
                    blurhash
                    status
                    size
                    error
                }
            }
        }
        ${CRAWL_JOB_FIELDS}
        ${PROGRESS_FIELDS}
        ${CHECKPOINT_FIELDS}
        ${SETTINGS_FIELDS}
    `;

    const CRAWL_STATS_QUERY = gql`
        query CrawlStats {
            crawlStats {
                totalJobs
                activeJobs
                completedJobs
                failedJobs
                byType {
                    category
                    comic
                    chapter
                    image
                }
                byStatus {
                    pending
                    running
                    completed
                    failed
                    paused
                    cancelled
                }
                queueDepth
            }
        }
    `;

    const QUEUE_STATUS_QUERY = gql`
        query QueueStatus($jobId: UUID) {
            queueStatus(jobId: $jobId) {
                totalItems
                pendingItems
                processingItems
                failedItems
                itemsByType {
                    category
                    comic
                    chapter
                    image
                }
            }
        }
    `;

    const CHILDREN_QUERY = gql`
        query JobChildren(
            $id: UUID!
            $first: Int
            $after: String
            $filter: CrawlJobFilter
            $sort: [CrawlJobSort!]
        ) {
            crawlJob(id: $id) {
                children(first: $first, after: $after, filter: $filter, sort: $sort) {
                    edges {
                        node {
                            id
                            type
                            status
                            targetName
                            percent
                            totalItems
                            completedItems
                            failedItems
                            hasChildren
                            childrenCount
                            createdAt
                        }
                        cursor
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                    totalCount
                }
            }
        }
    `;

    const SEARCH_SUGGESTIONS_QUERY = gql`
        query SearchSuggestions($query: String!, $limit: Int) {
            searchSuggestions(query: $query, limit: $limit)
        }
    `;

    // ===== MUTATIONS =====

    const CREATE_CRAWL_JOB = gql`
        mutation CreateCrawlJob($input: CreateCrawlJobInput!) {
            createCrawlJob(input: $input) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const START_CRAWL_JOB = gql`
        mutation StartCrawlJob($id: UUID!) {
            startCrawlJob(id: $id) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const PAUSE_CRAWL_JOB = gql`
        mutation PauseCrawlJob($id: UUID!) {
            pauseCrawlJob(id: $id) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const RESUME_CRAWL_JOB = gql`
        mutation ResumeCrawlJob($id: UUID!) {
            resumeCrawlJob(id: $id) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const RETRY_CRAWL_JOB = gql`
        mutation RetryCrawlJob($id: UUID!) {
            retryCrawlJob(id: $id) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const CANCEL_CRAWL_JOB = gql`
        mutation CancelCrawlJob($id: UUID!) {
            cancelCrawlJob(id: $id) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const DELETE_CRAWL_JOB = gql`
        mutation DeleteCrawlJob($id: UUID!, $hard: Boolean) {
            deleteCrawlJob(id: $id, hard: $hard)
        }
    `;

    const UPDATE_CRAWL_SETTINGS = gql`
        mutation UpdateCrawlSettings($id: UUID!, $input: UpdateCrawlSettingsInput!) {
            updateCrawlSettings(id: $id, input: $input) {
                ...CrawlJobFields
                settings {
                    ...SettingsFields
                }
            }
        }
        ${CRAWL_JOB_FIELDS}
        ${SETTINGS_FIELDS}
    `;

    const RETRY_FAILED_IMAGES = gql`
        mutation RetryFailedImages($jobId: UUID!, $indices: [Int!]!) {
            retryFailedImages(input: { jobId: $jobId, indices: $indices }) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const RETRY_ALL_FAILED_ITEMS = gql`
        mutation RetryAllFailedItems($id: UUID!) {
            retryAllFailedItems(id: $id) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const SKIP_FAILED_ITEMS = gql`
        mutation SkipFailedItems($id: UUID!, $indices: [Int!]!) {
            skipFailedItems(id: $id, indices: $indices) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    // ===== SUBSCRIPTIONS =====

    const PROGRESS_SUBSCRIPTION = gql`
        subscription CrawlProgress($jobId: UUID!) {
            crawlProgress(jobId: $jobId) {
                jobId
                percent
                itemIndex
                itemName
                totalItems
                completedItems
                failedItems
                bytesDownloaded
                message
                estimatedRemainingSeconds
            }
        }
    `;

    const MESSAGE_SUBSCRIPTION = gql`
        subscription CrawlMessage($jobId: UUID!) {
            crawlMessage(jobId: $jobId) {
                id
                timestamp
                level
                message
            }
        }
    `;

    const CHILD_CREATED_SUBSCRIPTION = gql`
        subscription ChildJobCreated($parentJobId: UUID!) {
            childJobCreated(parentJobId: $parentJobId) {
                id
                type
                status
                targetName
                percent
                hasChildren
                childrenCount
            }
        }
    `;

    const IMAGE_PROGRESS_SUBSCRIPTION = gql`
        subscription ImageProgress($jobId: UUID!) {
            imageProgress(jobId: $jobId) {
                index
                originalUrl
                path
                blurhash
                status
                size
                error
            }
        }
    `;

    const GLOBAL_EVENTS_SUBSCRIPTION = gql`
        subscription GlobalCrawlEvents {
            globalCrawlEvents {
                eventType
                jobId
                message
                timestamp
                job {
                    ...CrawlJobFields
                }
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    const JOB_STATUS_CHANGED_SUBSCRIPTION = gql`
        subscription JobStatusChanged($jobId: UUID!) {
            jobStatusChanged(jobId: $jobId) {
                ...CrawlJobFields
            }
        }
        ${CRAWL_JOB_FIELDS}
    `;

    // ===== HELPER FUNCTIONS =====

    /**
     * Build filter object from UI state
     */
    function buildFilter(filterState) {
        const filter = {};

        if (filterState.search) {
            filter.search = filterState.search;
        }
        if (filterState.types && filterState.types.length > 0) {
            filter.types = filterState.types;
        }
        if (filterState.statuses && filterState.statuses.length > 0) {
            filter.statuses = filterState.statuses;
        }
        if (filterState.dateRange?.from) {
            filter.createdAfter = filterState.dateRange.from;
        }
        if (filterState.dateRange?.to) {
            filter.createdBefore = filterState.dateRange.to;
        }
        if (filterState.percentMin !== undefined && filterState.percentMin > 0) {
            filter.percentMin = filterState.percentMin;
        }
        if (filterState.percentMax !== undefined && filterState.percentMax < 100) {
            filter.percentMax = filterState.percentMax;
        }
        if (filterState.rootOnly) {
            filter.rootOnly = true;
        }
        if (filterState.hasFailures) {
            filter.failedItemsMin = 1;
        }
        if (filterState.hasChildren !== undefined) {
            filter.hasChildren = filterState.hasChildren;
        }

        return Object.keys(filter).length > 0 ? filter : null;
    }

    /**
     * Build sort array from UI state
     */
    function buildSort(sortState) {
        if (!sortState || sortState.length === 0) {
            return [{field: 'CREATED_AT', direction: 'DESC'}];
        }
        return sortState;
    }

    // Public API
    return {
        // Fragments
        CRAWL_JOB_FIELDS,
        PROGRESS_FIELDS,
        CHECKPOINT_FIELDS,
        SETTINGS_FIELDS,

        // Queries
        CRAWL_JOB_QUERY,
        CRAWL_JOBS_LIST_QUERY,
        JOB_DETAIL_QUERY,
        CRAWL_STATS_QUERY,
        QUEUE_STATUS_QUERY,
        CHILDREN_QUERY,
        SEARCH_SUGGESTIONS_QUERY,

        // Mutations
        CREATE_CRAWL_JOB,
        START_CRAWL_JOB,
        PAUSE_CRAWL_JOB,
        RESUME_CRAWL_JOB,
        RETRY_CRAWL_JOB,
        CANCEL_CRAWL_JOB,
        DELETE_CRAWL_JOB,
        UPDATE_CRAWL_SETTINGS,
        RETRY_FAILED_IMAGES,
        RETRY_ALL_FAILED_ITEMS,
        SKIP_FAILED_ITEMS,

        // Subscriptions
        PROGRESS_SUBSCRIPTION,
        MESSAGE_SUBSCRIPTION,
        CHILD_CREATED_SUBSCRIPTION,
        IMAGE_PROGRESS_SUBSCRIPTION,
        GLOBAL_EVENTS_SUBSCRIPTION,
        JOB_STATUS_CHANGED_SUBSCRIPTION,

        // Helpers
        buildFilter,
        buildSort
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CrawlGraphQL;
}

