/**
 * Crawl History - View all crawl jobs with table/card views and modal detail
 */

import {apolloClient, gql} from './graphql/apollo-client.js';

// State
let currentView = 'table';
let currentPage = 1;
const pageSize = 20;
let totalCount = 0;
let currentFilters = {
    status: null,
    type: null,
    search: '',
    dateFrom: null,
    dateTo: null
};
let endCursor = null;
let cursors = [null];
let currentJobId = null;
let jobDetailModal = null;

// GraphQL Queries
const CRAWL_JOBS_QUERY = gql`
    query CrawlHistory($filter: CrawlJobFilter, $first: Int, $after: String, $sort: [CrawlJobSort!]) {
        crawlJobs(filter: $filter, first: $first, after: $after, sort: $sort) {
            edges {
                node {
                    id
                    type
                    status
                    targetUrl
                    targetName
                    completedItems
                    totalItems
                    failedItems
                    skippedItems
                    createdAt
                    updatedAt
                    progress {
                        percent
                        message
                    }
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
`;

const JOB_DETAIL_QUERY = gql`
    query CrawlJobDetail($id: UUID!) {
        crawlJob(id: $id) {
            id
            type
            status
            targetUrl
            targetName
            downloadMode
            depth
            totalItems
            completedItems
            failedItems
            skippedItems
            percent
            errorMessage
            createdAt
            startedAt
            completedAt
            updatedAt
            progress {
                percent
                message
                bytesDownloaded
                estimatedRemainingSeconds
            }
            messages(first: 50) {
                edges {
                    node {
                        id
                        timestamp
                        level
                        message
                    }
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
        }
    }
`;

const JOB_STATS_QUERY = gql`
    query CrawlDashboardStats {
        crawlStats {
            byStatus {
                running
                pending
                completed
                failed
            }
        }
    }
`;

// Mutations
const PAUSE_JOB = gql`mutation PauseJob($id: UUID!) { pauseCrawlJob(id: $id) { id status } }`;
const RESUME_JOB = gql`mutation ResumeJob($id: UUID!) { resumeCrawlJob(id: $id) { id status } }`;
const RETRY_JOB = gql`mutation RetryJob($id: UUID!) { retryCrawlJob(id: $id) { id status } }`;
const CANCEL_JOB = gql`mutation CancelJob($id: UUID!) { cancelCrawlJob(id: $id) { id status } }`;

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', () => {
    init();
});

async function init() {
    if (typeof feather !== 'undefined') {
        feather.replace();
    }

    // Initialize modal
    var modalEl = document.getElementById('jobDetailModal');
    if (modalEl) {
        jobDetailModal = new bootstrap.Modal(modalEl);
    }

    setupEventListeners();
    await loadJobs();
    await loadStats();
}

function setupEventListeners() {
    // Filter changes
    document.getElementById('filterStatus')?.addEventListener('change', (e) => {
        currentFilters.status = e.target.value || null;
        resetPagination();
        loadJobs();
    });

    document.getElementById('filterType')?.addEventListener('change', (e) => {
        currentFilters.type = e.target.value || null;
        resetPagination();
        loadJobs();
    });

    document.getElementById('filterDateFrom')?.addEventListener('change', (e) => {
        currentFilters.dateFrom = e.target.value || null;
        resetPagination();
        loadJobs();
    });

    document.getElementById('filterDateTo')?.addEventListener('change', (e) => {
        currentFilters.dateTo = e.target.value || null;
        resetPagination();
        loadJobs();
    });

    // Search with debounce
    let searchTimeout;
    document.getElementById('searchInput')?.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentFilters.search = e.target.value || '';
            resetPagination();
            loadJobs();
        }, 300);
    });

    // View toggle
    document.getElementById('btnTableView')?.addEventListener('click', () => setView('table'));
    document.getElementById('btnCardView')?.addEventListener('click', () => setView('card'));
}

function resetPagination() {
    currentPage = 1;
    cursors = [null];
    endCursor = null;
}

function setView(view) {
    currentView = view;
    document.getElementById('btnTableView')?.classList.toggle('active', view === 'table');
    document.getElementById('btnCardView')?.classList.toggle('active', view === 'card');
    document.getElementById('tableView').style.display = view === 'table' ? 'block' : 'none';
    document.getElementById('cardView').style.display = view === 'card' ? 'grid' : 'none';
}

async function loadJobs() {
    showLoading(true);

    try {
        var filter = {rootOnly: true};

        if (currentFilters.status) filter.statuses = [currentFilters.status];
        if (currentFilters.type) filter.types = [currentFilters.type];
        if (currentFilters.search) filter.search = currentFilters.search;
        if (currentFilters.dateFrom) filter.createdAfter = currentFilters.dateFrom + 'T00:00:00Z';
        if (currentFilters.dateTo) filter.createdBefore = currentFilters.dateTo + 'T23:59:59Z';

        const {data} = await apolloClient.query({
            query: CRAWL_JOBS_QUERY,
            variables: {
                filter,
                first: pageSize,
                after: cursors[currentPage - 1],
                sort: [{field: 'CREATED_AT', direction: 'DESC'}]
            },
            fetchPolicy: 'network-only'
        });

        var jobs = data.crawlJobs;
        totalCount = jobs.totalCount;
        endCursor = jobs.pageInfo.endCursor;

        if (jobs.pageInfo.hasNextPage && cursors.length === currentPage) {
            cursors.push(endCursor);
        }

        renderJobs(jobs.edges.map(e => e.node));
        updatePagination(jobs.pageInfo);
        showLoading(false);
    } catch (error) {
        console.error('Error loading jobs:', error);
        showLoading(false);
        showEmpty(true);
    }
}

async function loadStats() {
    try {
        const {data} = await apolloClient.query({
            query: JOB_STATS_QUERY,
            fetchPolicy: 'network-only'
        });

        var stats = data.crawlStats.byStatus;
        document.getElementById('countRunning').textContent = stats.running || 0;
        document.getElementById('countQueued').textContent = stats.pending || 0;
        document.getElementById('countCompleted').textContent = stats.completed || 0;
        document.getElementById('countFailed').textContent = stats.failed || 0;
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

function renderJobs(jobs) {
    if (jobs.length === 0) {
        showEmpty(true);
        return;
    }

    showEmpty(false);
    renderTableView(jobs);
    renderCardView(jobs);

    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

function renderTableView(jobs) {
    var tbody = document.getElementById('tableBody');
    if (!tbody) return;

    tbody.innerHTML = jobs.map(job => {
        var percent = job.progress?.percent || 0;
        var typeIcon = getTypeIcon(job.type);
        var createdAt = formatDate(job.createdAt);

        return `
            <tr onclick="openJobModal('${job.id}')">
                <td>
                    <span class="badge badge-${job.status.toLowerCase()}">${job.status}</span>
                </td>
                <td>
                    <span class="badge badge-${job.type.toLowerCase()}">${typeIcon} ${job.type}</span>
                </td>
                <td>
                    <div class="fw-medium">${escapeHtml(job.targetName || '-')}</div>
                    <div class="text-muted small text-truncate" style="max-width: 300px;">${escapeHtml(job.targetUrl || '-')}</div>
                </td>
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <div class="progress" style="width: 60px; height: 6px;">
                            <div class="progress-bar" style="width: ${percent}%"></div>
                        </div>
                        <span class="small">${percent}%</span>
                    </div>
                    <div class="small text-muted">${job.completedItems || 0}/${job.totalItems || '?'}</div>
                </td>
                <td><div class="small">${createdAt}</div></td>
                <td>
                    <button class="btn btn-sm btn-outline-primary" onclick="event.stopPropagation(); openJobModal('${job.id}')">
                        <i data-feather="eye" style="width:14px;height:14px;"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

function renderCardView(jobs) {
    var container = document.getElementById('cardView');
    if (!container) return;

    container.innerHTML = jobs.map(job => {
        var percent = job.progress?.percent || 0;
        var typeIcon = getTypeIcon(job.type);
        var createdAt = formatDate(job.createdAt);

        return `
            <div class="card h-100" style="cursor: pointer;" onclick="openJobModal('${job.id}')">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <span style="font-size: 1.5rem;">${typeIcon}</span>
                        <span class="badge badge-${job.status.toLowerCase()}">${job.status}</span>
                    </div>
                    <h6 class="card-title mb-1" style="display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                        ${escapeHtml(job.targetName || 'Unnamed Job')}
                    </h6>
                    <div class="text-muted small text-truncate mb-3">${escapeHtml(job.targetUrl || '-')}</div>
                    <div class="mb-2">
                        <div class="d-flex justify-content-between small mb-1">
                            <span>${percent}%</span>
                            <span class="text-muted">${job.completedItems || 0}/${job.totalItems || '?'}</span>
                        </div>
                        <div class="progress" style="height: 6px;">
                            <div class="progress-bar" style="width: ${percent}%"></div>
                        </div>
                    </div>
                </div>
                <div class="card-footer bg-transparent d-flex justify-content-between align-items-center py-2">
                    <span class="badge badge-${job.type.toLowerCase()}">${job.type}</span>
                    <span class="text-muted small">${createdAt}</span>
                </div>
            </div>
        `;
    }).join('');
}

// Modal Functions
window.openJobModal = async function (jobId) {
    currentJobId = jobId;

    // Show loading state in modal
    document.getElementById('modalJobTitle').textContent = 'Loading...';
    document.getElementById('modalJobId').textContent = '';
    document.getElementById('modalJobUrl').textContent = '';
    document.getElementById('modalProgressBar').style.width = '0%';
    document.getElementById('modalPercent').textContent = '0';
    document.getElementById('modalActions').innerHTML = '';

    jobDetailModal.show();

    try {
        const {data} = await apolloClient.query({
            query: JOB_DETAIL_QUERY,
            variables: {id: jobId},
            fetchPolicy: 'network-only'
        });

        renderModalContent(data.crawlJob);
    } catch (error) {
        console.error('Error loading job details:', error);
        document.getElementById('modalJobTitle').textContent = 'Error loading job';
    }
};

function renderModalContent(job) {
    if (!job) return;

    var typeIcon = getTypeIcon(job.type);

    // Header
    document.getElementById('modalJobTitle').innerHTML = `${typeIcon} ${escapeHtml(job.targetName || 'Unnamed Job')}`;
    document.getElementById('modalJobStatus').className = `badge badge-${job.status.toLowerCase()}`;
    document.getElementById('modalJobStatus').textContent = job.status;
    document.getElementById('modalJobType').className = `badge badge-${job.type.toLowerCase()}`;
    document.getElementById('modalJobType').textContent = job.type;
    document.getElementById('modalJobId').textContent = job.id;
    document.getElementById('modalJobUrl').textContent = job.targetUrl || '-';

    // Progress
    var percent = job.percent || 0;
    document.getElementById('modalProgressBar').style.width = percent + '%';
    document.getElementById('modalPercent').textContent = percent;
    document.getElementById('modalCompleted').textContent = job.completedItems || 0;
    document.getElementById('modalTotal').textContent = job.totalItems || '?';
    document.getElementById('modalFailedCount').textContent = (job.failedItems || 0) + ' failed';

    // Overview tab
    document.getElementById('modalCreated').textContent = formatDate(job.createdAt);
    document.getElementById('modalStarted').textContent = formatDate(job.startedAt);
    document.getElementById('modalCompletedAt').textContent = formatDate(job.completedAt);
    document.getElementById('modalMode').textContent = job.downloadMode || '-';

    document.getElementById('modalStatCompleted').textContent = job.completedItems || 0;
    document.getElementById('modalStatFailed').textContent = job.failedItems || 0;
    document.getElementById('modalStatSkipped').textContent = job.skippedItems || 0;
    document.getElementById('modalStatTotal').textContent = job.totalItems || 0;

    // Error message
    var errorDiv = document.getElementById('modalErrorMessage');
    if (job.errorMessage) {
        errorDiv.textContent = job.errorMessage;
        errorDiv.style.display = 'block';
    } else {
        errorDiv.style.display = 'none';
    }

    // Logs tab
    var logsContainer = document.getElementById('modalLogsContainer');
    var messages = job.messages?.edges || [];
    document.getElementById('modalLogsCount').textContent = job.messages?.totalCount || 0;

    if (messages.length > 0) {
        logsContainer.innerHTML = messages.map(edge => {
            var msg = edge.node;
            var levelClass = 'log-' + msg.level.toLowerCase();
            return `
                <div class="log-entry">
                    <span class="log-time">${formatTime(msg.timestamp)}</span>
                    <span class="${levelClass}">[${msg.level}]</span>
                    ${escapeHtml(msg.message)}
                </div>
            `;
        }).join('');
    } else {
        logsContainer.innerHTML = '<div class="text-muted text-center py-3">No logs available</div>';
    }

    // Failed tab
    var failedList = document.getElementById('modalFailedList');
    var failedItems = job.failedItemsList?.items || [];
    document.getElementById('modalFailedBadge').textContent = job.failedItemsList?.totalCount || 0;

    if (failedItems.length > 0) {
        failedList.innerHTML = failedItems.map(item => `
            <div class="alert alert-danger py-2 mb-2">
                <div class="d-flex justify-content-between">
                    <strong>#${item.index}: ${escapeHtml(item.name || item.url || 'Unknown')}</strong>
                    <span class="badge bg-secondary">Retries: ${item.retryCount}</span>
                </div>
                <div class="small text-muted">${escapeHtml(item.error || 'Unknown error')}</div>
            </div>
        `).join('');
    } else {
        failedList.innerHTML = '<div class="text-muted text-center py-3">No failed items</div>';
    }

    // Actions
    renderModalActions(job);

    // Go to Manager link
    document.getElementById('modalGoToManager').href = '/admin/crawl/' + job.id;

    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

function renderModalActions(job) {
    var actionsDiv = document.getElementById('modalActions');
    var buttons = [];

    switch (job.status) {
        case 'RUNNING':
            buttons.push(`<button class="btn btn-warning btn-sm" onclick="pauseJob('${job.id}')">
                <i data-feather="pause" style="width:14px;height:14px;"></i> Pause
            </button>`);
            buttons.push(`<button class="btn btn-danger btn-sm" onclick="cancelJob('${job.id}')">
                <i data-feather="x" style="width:14px;height:14px;"></i> Cancel
            </button>`);
            break;
        case 'PAUSED':
            buttons.push(`<button class="btn btn-success btn-sm" onclick="resumeJob('${job.id}')">
                <i data-feather="play" style="width:14px;height:14px;"></i> Resume
            </button>`);
            buttons.push(`<button class="btn btn-danger btn-sm" onclick="cancelJob('${job.id}')">
                <i data-feather="x" style="width:14px;height:14px;"></i> Cancel
            </button>`);
            break;
        case 'FAILED':
            buttons.push(`<button class="btn btn-primary btn-sm" onclick="retryJob('${job.id}')">
                <i data-feather="refresh-cw" style="width:14px;height:14px;"></i> Retry
            </button>`);
            break;
        case 'QUEUED':
        case 'PENDING':
            buttons.push(`<button class="btn btn-danger btn-sm" onclick="cancelJob('${job.id}')">
                <i data-feather="x" style="width:14px;height:14px;"></i> Cancel
            </button>`);
            break;
    }

    actionsDiv.innerHTML = buttons.join(' ');
}

// Job Actions
window.pauseJob = async function (jobId) {
    try {
        await apolloClient.mutate({mutation: PAUSE_JOB, variables: {id: jobId}});
        openJobModal(jobId); // Refresh modal
        loadJobs(); // Refresh list
    } catch (error) {
        console.error('Error pausing job:', error);
        alert('Failed to pause job');
    }
};

window.resumeJob = async function (jobId) {
    try {
        await apolloClient.mutate({mutation: RESUME_JOB, variables: {id: jobId}});
        openJobModal(jobId);
        loadJobs();
    } catch (error) {
        console.error('Error resuming job:', error);
        alert('Failed to resume job');
    }
};

window.retryJob = async function (jobId) {
    try {
        await apolloClient.mutate({mutation: RETRY_JOB, variables: {id: jobId}});
        openJobModal(jobId);
        loadJobs();
    } catch (error) {
        console.error('Error retrying job:', error);
        alert('Failed to retry job');
    }
};

window.cancelJob = async function (jobId) {
    if (!confirm('Are you sure you want to cancel this job?')) return;

    try {
        await apolloClient.mutate({mutation: CANCEL_JOB, variables: {id: jobId}});
        openJobModal(jobId);
        loadJobs();
    } catch (error) {
        console.error('Error cancelling job:', error);
        alert('Failed to cancel job');
    }
};

// Pagination & UI helpers
function updatePagination(pageInfo) {
    var start = (currentPage - 1) * pageSize + 1;
    var end = Math.min(currentPage * pageSize, totalCount);

    document.getElementById('showingStart').textContent = totalCount > 0 ? start : 0;
    document.getElementById('showingEnd').textContent = end;
    document.getElementById('totalCount').textContent = totalCount;

    document.getElementById('prevPage')?.classList.toggle('disabled', currentPage === 1);
    document.getElementById('nextPage')?.classList.toggle('disabled', !pageInfo.hasNextPage);
    document.getElementById('paginationContainer').style.display = totalCount > 0 ? 'block' : 'none';
}

function showLoading(show) {
    document.getElementById('loadingState').style.display = show ? 'block' : 'none';
    document.getElementById('tableView').style.display = show || currentView !== 'table' ? 'none' : 'block';
    document.getElementById('cardView').style.display = show || currentView !== 'card' ? 'none' : 'grid';
}

function showEmpty(show) {
    document.getElementById('emptyState').style.display = show ? 'block' : 'none';
    if (show) {
        document.getElementById('tableView').style.display = 'none';
        document.getElementById('cardView').style.display = 'none';
        document.getElementById('paginationContainer').style.display = 'none';
    }
}

window.prevPage = function () {
    if (currentPage > 1) {
        currentPage--;
        loadJobs();
    }
};

window.nextPage = function () {
    currentPage++;
    loadJobs();
};

// Utilities
function getTypeIcon(type) {
    switch (type) {
        case 'CATEGORY':
            return '📁';
        case 'COMIC':
            return '📚';
        case 'CHAPTER':
            return '📄';
        case 'IMAGE':
            return '🖼️';
        default:
            return '📋';
    }
}

function formatDate(dateString) {
    if (!dateString) return '-';
    var date = new Date(dateString);
    return date.toLocaleDateString('vi-VN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatTime(dateString) {
    if (!dateString) return '';
    var date = new Date(dateString);
    return date.toLocaleTimeString('vi-VN', {hour: '2-digit', minute: '2-digit', second: '2-digit'});
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
