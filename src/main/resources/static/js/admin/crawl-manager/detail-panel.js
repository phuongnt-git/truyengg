/**
 * Crawl Manager - Detail Panel Component
 * Job details with tabs for Overview, Children, Logs, Failed, Images
 */

import {apolloClient, gql} from '../graphql/apollo-client.js';
import {refreshSidebar} from './sidebar.js';

// State
let currentJobId = null;
let progressSubscription = null;
let messageSubscription = null;
let allMessages = [];

// GraphQL Queries
const JOB_DETAIL_QUERY = gql`
    query JobDetail($id: UUID!) {
        crawlJob(id: $id) {
            id
            type
            status
            targetUrl
            targetName
            downloadMode
            depth
            contentId
            completedItems
            failedItems
            skippedItems
            totalItems
            createdAt
            updatedAt
            startedAt
            parentJob { id }
            rootJob { id }
            progress {
                percent
                message
                bytesDownloaded
            }
            children(first: 20) {
                edges {
                    node {
                        id
                        type
                        status
                        targetName
                        completedItems
                        totalItems
                        progress { percent }
                    }
                }
                totalCount
                pageInfo { hasNextPage endCursor }
            }
            messages(first: 100) {
                edges {
                    node { timestamp level message }
                }
                totalCount
            }
            checkpoint {
                failedItemIndices
            }
        }
    }
`;

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

// Initialize
export function initDetailPanel() {
    // Tab lazy loading
    document.querySelectorAll('[data-bs-toggle="tab"]').forEach(tab => {
        tab.addEventListener('shown.bs.tab', handleTabChange);
    });
}

// Show job detail
export async function showJobDetail(jobId) {
    currentJobId = jobId;

    // Hide empty state, show detail
    document.getElementById('emptyState').style.display = 'none';
    document.getElementById('jobDetail').style.display = 'block';

    // Load job data
    await loadJobDetail(jobId);

    // Start subscriptions
    startJobSubscriptions(jobId);
}

// Hide job detail
export function hideJobDetail() {
    currentJobId = null;

    // Show empty state, hide detail
    document.getElementById('emptyState').style.display = 'flex';
    document.getElementById('jobDetail').style.display = 'none';

    // Stop subscriptions
    stopJobSubscriptions();
}

// Load job detail
async function loadJobDetail(jobId) {
    try {
        const {data} = await apolloClient.query({
            query: JOB_DETAIL_QUERY,
            variables: {id: jobId},
            fetchPolicy: 'network-only'
        });

        if (!data.crawlJob) {
            alert('Job not found');
            hideJobDetail();
            return;
        }

        renderJobDetail(data.crawlJob);
    } catch (error) {
        console.error('Error loading job detail:', error);
        alert('Failed to load job details');
    }
}

// Render job detail
function renderJobDetail(job) {
    const jobType = job.type || job.crawlType;

    // Header
    document.getElementById('detailType').textContent = jobType;
    document.getElementById('detailType').className = `job-type-badge type-${jobType.toLowerCase()}`;

    document.getElementById('detailStatus').textContent = job.status;
    document.getElementById('detailStatus').className = `job-status-badge status-${job.status.toLowerCase()}`;

    document.getElementById('detailTitle').textContent = job.targetName || 'Unnamed Job';
    document.getElementById('detailUrl').textContent = job.targetUrl || '-';

    // Progress
    const percent = job.progress?.percent || 0;
    document.getElementById('detailProgressText').textContent = `${percent}%`;
    document.getElementById('detailProgressBar').style.width = `${percent}%`;
    document.getElementById('detailMessage').textContent = job.progress?.message || '';

    // Stats
    document.getElementById('detailStarted').textContent = job.startedAt ? formatRelativeTime(job.startedAt) : '-';
    document.getElementById('detailEta').textContent = calculateEta(job);
    document.getElementById('detailDownloaded').textContent = formatBytes(job.progress?.bytesDownloaded || 0);

    // Actions
    renderActions(job);

    // Overview tab
    document.getElementById('statCompleted').textContent = job.completedItems || 0;
    document.getElementById('statFailed').textContent = job.failedItems || 0;
    document.getElementById('statSkipped').textContent = job.skippedItems || 0;
    document.getElementById('statTotal').textContent = job.totalItems || 0;

    document.getElementById('infoJobId').textContent = job.id.substring(0, 8) + '...';
    document.getElementById('infoType').textContent = jobType;
    document.getElementById('infoMode').textContent = job.downloadMode || 'FULL';
    document.getElementById('infoDepth').textContent = job.depth || 0;
    document.getElementById('infoCreated').textContent = formatDateTime(job.createdAt);

    // Hierarchy
    renderHierarchy(job);

    // Tabs counts
    document.getElementById('childrenCount').textContent = job.children?.totalCount || 0;
    document.getElementById('logsCount').textContent = job.messages?.totalCount || 0;
    document.getElementById('failedCount').textContent = job.checkpoint?.failedItemIndices?.length || 0;

    // Children tab
    renderChildren(job.children);

    // Logs tab - map message field to content for consistency
    allMessages = job.messages?.edges?.map(e => ({
        ...e.node,
        content: e.node.message || e.node.content
    })) || [];
    renderLogs(allMessages);

    // Failed tab - convert failedItemIndices to objects
    const failedItems = (job.checkpoint?.failedItemIndices || []).map((idx, i) => ({
        index: idx,
        title: `Item #${idx}`,
        error: 'Failed during processing'
    }));
    renderFailed(failedItems);

    // Feather icons
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

// Render action buttons
function renderActions(job) {
    const container = document.getElementById('detailActions');
    let html = '';

    switch (job.status) {
        case 'RUNNING':
            html = `
                <button class="btn btn-sm btn-warning" onclick="pauseJob()">
                    <i data-feather="pause"></i> Pause
                </button>
                <button class="btn btn-sm btn-danger" onclick="cancelJob()">
                    <i data-feather="x"></i> Cancel
                </button>
            `;
            break;
        case 'PAUSED':
            html = `
                <button class="btn btn-sm btn-success" onclick="resumeJob()">
                    <i data-feather="play"></i> Resume
                </button>
                <button class="btn btn-sm btn-danger" onclick="cancelJob()">
                    <i data-feather="x"></i> Cancel
                </button>
            `;
            break;
        case 'FAILED':
        case 'CANCELLED':
            html = `
                <button class="btn btn-sm btn-primary" onclick="retryJob()">
                    <i data-feather="refresh-cw"></i> Retry
                </button>
            `;
            break;
        case 'QUEUED':
        case 'PENDING':
            html = `
                <button class="btn btn-sm btn-danger" onclick="cancelJob()">
                    <i data-feather="x"></i> Cancel
                </button>
            `;
            break;
    }

    container.innerHTML = html;
}

// Render hierarchy info
function renderHierarchy(job) {
    const container = document.getElementById('hierarchyInfo');

    const parentId = job.parentJob?.id;
    const rootId = job.rootJob?.id;

    if (!parentId && !rootId) {
        container.innerHTML = '<p class="text-muted mb-0">This is a root job</p>';
        return;
    }

    let html = '';
    if (rootId && rootId !== job.id) {
        html += `
            <p class="mb-1">
                <span class="text-muted">Root:</span>
                <a href="javascript:void(0)" onclick="selectJobById('${rootId}')">${rootId.substring(0, 8)}...</a>
            </p>
        `;
    }
    if (parentId && parentId !== rootId) {
        html += `
            <p class="mb-0">
                <span class="text-muted">Parent:</span>
                <a href="javascript:void(0)" onclick="selectJobById('${parentId}')">${parentId.substring(0, 8)}...</a>
            </p>
        `;
    }

    container.innerHTML = html;
}

// Render children
function renderChildren(children) {
    const container = document.getElementById('childrenList');

    if (!children?.edges?.length) {
        container.innerHTML = '<div class="text-center text-muted py-4">No child jobs</div>';
        return;
    }

    const html = children.edges.map(edge => {
        const child = edge.node;
        const childType = child.type || child.crawlType;
        const typeClass = `type-${childType.toLowerCase()}`;
        const percent = child.progress?.percent || 0;

        return `
            <div class="child-item" onclick="selectJobById('${child.id}')">
                <span class="job-type-badge ${typeClass} me-2">${childType}</span>
                <span class="job-status-badge status-${child.status.toLowerCase()} me-2">${child.status}</span>
                <span class="flex-grow-1">${escapeHtml(child.targetName || 'Child Job')}</span>
                <span class="text-muted">${child.completedItems || 0}/${child.totalItems || '?'}</span>
                ${child.status === 'RUNNING' ? `<span class="ms-2">${percent}%</span>` : ''}
            </div>
        `;
    }).join('');

    container.innerHTML = html;

    if (children.pageInfo?.hasNextPage) {
        container.innerHTML += `
            <div class="text-center mt-2">
                <button class="btn btn-sm btn-outline-secondary" onclick="loadMoreChildren()">Load more...</button>
            </div>
        `;
    }
}

// Render logs
function renderLogs(messages, filter = 'all') {
    const container = document.getElementById('logsContainer');

    const filtered = filter === 'all'
        ? messages
        : messages.filter(m => m.level?.toLowerCase() === filter);

    if (!filtered.length) {
        container.innerHTML = '<div class="text-center text-muted py-4">No logs</div>';
        return;
    }

    const html = filtered.map(msg => {
        const levelClass = `log-${(msg.level || 'info').toLowerCase()}`;
        const time = msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString() : '';
        return `
            <div class="log-entry ${levelClass}">
                <span class="log-time">${time}</span>
                ${escapeHtml(msg.content)}
            </div>
        `;
    }).join('');

    container.innerHTML = html;
    container.scrollTop = container.scrollHeight;
}

// Render failed items
function renderFailed(failedItems) {
    const container = document.getElementById('failedList');

    if (!failedItems.length) {
        container.innerHTML = `
            <div class="text-center text-muted py-4">
                <i data-feather="check-circle" style="width: 48px; height: 48px; opacity: 0.5;"></i>
                <div class="mt-2">No failed items</div>
            </div>
        `;
        return;
    }

    const html = failedItems.map((item, index) => `
        <div class="failed-item">
            <i data-feather="alert-circle" class="text-danger me-3"></i>
            <div class="failed-info flex-grow-1">
                <div class="failed-title">${item.title || `Item #${item.index || index}`}</div>
                <div class="failed-error">${escapeHtml(item.error || 'Unknown error')}</div>
            </div>
            <div class="d-flex gap-1">
                <button class="btn btn-sm btn-outline-success" onclick="retryFailedItem(${item.index || index})">
                    <i data-feather="refresh-cw"></i>
                </button>
                <button class="btn btn-sm btn-outline-secondary" onclick="skipFailedItem(${item.index || index})">
                    <i data-feather="skip-forward"></i>
                </button>
            </div>
        </div>
    `).join('');

    container.innerHTML = html;
}

// Handle tab change
function handleTabChange(e) {
    const target = e.target.getAttribute('data-bs-target');

    switch (target) {
        case '#tabImages':
            loadImages();
            break;
    }
}

// Load images
async function loadImages() {
    if (!currentJobId) return;

    const container = document.getElementById('imagesGrid');
    container.innerHTML = '<div class="text-center text-muted py-4" style="grid-column: 1/-1;">Loading images...</div>';

    try {
        const {data} = await apolloClient.query({
            query: gql`
                query JobImages($id: UUID!) {
                    crawlJob(id: $id) {
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
            `,
            variables: {id: currentJobId},
            fetchPolicy: 'network-only'
        });

        const images = data.crawlJob?.images || [];

        if (!images.length) {
            container.innerHTML = '<div class="text-center text-muted py-4" style="grid-column: 1/-1;">No images available</div>';
            return;
        }

        container.innerHTML = images.map(img => `
            <div class="image-thumb">
                ${img.blurhash ? `<canvas data-blurhash="${img.blurhash}"></canvas>` : ''}
                <div class="image-status">#${img.index} ${img.status}</div>
            </div>
        `).join('');

        // Decode blurhash
        decodeBlurhashes();
    } catch (error) {
        console.error('Error loading images:', error);
        container.innerHTML = '<div class="text-center text-danger py-4" style="grid-column: 1/-1;">Failed to load images</div>';
    }
}

// Decode blurhash images
function decodeBlurhashes() {
    document.querySelectorAll('[data-blurhash]').forEach(canvas => {
        const hash = canvas.dataset.blurhash;
        if (hash && window.blurhash?.decode) {
            try {
                const pixels = window.blurhash.decode(hash, 32, 32);
                const ctx = canvas.getContext('2d');
                const imageData = ctx.createImageData(32, 32);
                imageData.data.set(pixels);
                ctx.putImageData(imageData, 0, 0);
            } catch (e) {
                console.debug('Blurhash decode error:', e);
            }
        }
    });
}

// Start job subscriptions
function startJobSubscriptions(jobId) {
    stopJobSubscriptions();

    try {
        progressSubscription = apolloClient.subscribe({
            query: PROGRESS_SUBSCRIPTION,
            variables: {jobId}
        }).subscribe({
            next: ({data}) => {
                if (data?.crawlProgress && currentJobId === jobId) {
                    updateProgress(data.crawlProgress);
                }
            }
        });

        messageSubscription = apolloClient.subscribe({
            query: MESSAGE_SUBSCRIPTION,
            variables: {jobId}
        }).subscribe({
            next: ({data}) => {
                if (data?.crawlMessage && currentJobId === jobId) {
                    appendMessage(data.crawlMessage);
                }
            }
        });
    } catch (error) {
        console.error('Error starting subscriptions:', error);
    }
}

// Stop job subscriptions
function stopJobSubscriptions() {
    progressSubscription?.unsubscribe();
    messageSubscription?.unsubscribe();
    progressSubscription = null;
    messageSubscription = null;
}

// Update progress from subscription
function updateProgress(progress) {
    document.getElementById('detailStatus').textContent = progress.status;
    document.getElementById('detailStatus').className = `job-status-badge status-${progress.status.toLowerCase()}`;

    document.getElementById('detailProgressText').textContent = `${progress.percent || 0}%`;
    document.getElementById('detailProgressBar').style.width = `${progress.percent || 0}%`;
    document.getElementById('detailMessage').textContent = progress.message || '';

    document.getElementById('statCompleted').textContent = progress.completedItems || 0;
    document.getElementById('statFailed').textContent = progress.failedItems || 0;
    document.getElementById('statTotal').textContent = progress.totalItems || 0;

    document.getElementById('detailDownloaded').textContent = formatBytes(progress.bytesDownloaded || 0);

    // Update sidebar if status changed
    if (['COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED'].includes(progress.status)) {
        refreshSidebar();
    }
}

// Append message from subscription
function appendMessage(message) {
    // Normalize message content
    const normalizedMessage = {
        ...message,
        content: message.message || message.content
    };
    allMessages.push(normalizedMessage);

    const container = document.getElementById('logsContainer');
    const levelClass = `log-${(message.level || 'info').toLowerCase()}`;
    const time = message.timestamp ? new Date(message.timestamp).toLocaleTimeString() : '';

    container.innerHTML += `
        <div class="log-entry ${levelClass}">
            <span class="log-time">${time}</span>
            ${escapeHtml(normalizedMessage.content)}
        </div>
    `;

    // Auto-scroll
    container.scrollTop = container.scrollHeight;

    // Update count
    document.getElementById('logsCount').textContent = allMessages.length;
}

// Update job progress from global event
export function updateJobProgress(event) {
    if (event.status) {
        document.getElementById('detailStatus').textContent = event.status;
        document.getElementById('detailStatus').className = `job-status-badge status-${event.status.toLowerCase()}`;
    }
    if (event.message) {
        document.getElementById('detailMessage').textContent = event.message;
    }
}

// Job actions
window.pauseJob = async function () {
    if (!currentJobId) return;
    try {
        await apolloClient.mutate({
            mutation: gql`mutation PauseJob($id: ID!) { pauseCrawlJob(id: $id) { id status } }`,
            variables: {id: currentJobId}
        });
        await loadJobDetail(currentJobId);
        refreshSidebar();
    } catch (error) {
        console.error('Error pausing job:', error);
        alert('Failed to pause job');
    }
};

window.resumeJob = async function () {
    if (!currentJobId) return;
    try {
        await apolloClient.mutate({
            mutation: gql`mutation ResumeJob($id: ID!) { resumeCrawlJob(id: $id) { id status } }`,
            variables: {id: currentJobId}
        });
        await loadJobDetail(currentJobId);
        refreshSidebar();
    } catch (error) {
        console.error('Error resuming job:', error);
        alert('Failed to resume job');
    }
};

window.cancelJob = async function () {
    if (!currentJobId || !confirm('Are you sure you want to cancel this job?')) return;
    try {
        await apolloClient.mutate({
            mutation: gql`mutation CancelJob($id: ID!) { cancelCrawlJob(id: $id) { id status } }`,
            variables: {id: currentJobId}
        });
        await loadJobDetail(currentJobId);
        refreshSidebar();
    } catch (error) {
        console.error('Error cancelling job:', error);
        alert('Failed to cancel job');
    }
};

window.retryJob = async function () {
    if (!currentJobId) return;
    try {
        await apolloClient.mutate({
            mutation: gql`mutation RetryJob($id: ID!) { retryCrawlJob(id: $id) { id status } }`,
            variables: {id: currentJobId}
        });
        await loadJobDetail(currentJobId);
        refreshSidebar();
    } catch (error) {
        console.error('Error retrying job:', error);
        alert('Failed to retry job');
    }
};

window.filterLogs = function (level) {
    // Update button states
    document.querySelectorAll('#tabLogs .btn-group .btn').forEach(btn => {
        btn.classList.remove('active');
    });
    event.target.classList.add('active');

    renderLogs(allMessages, level);
};

window.exportLogs = function () {
    const content = allMessages.map(m =>
        `${m.timestamp || ''} [${m.level || 'INFO'}] ${m.content}`
    ).join('\n');

    const blob = new Blob([content], {type: 'text/plain'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `crawl-job-${currentJobId}-logs.txt`;
    a.click();
    URL.revokeObjectURL(url);
};

window.retryAllFailed = async function () {
    if (!currentJobId || !confirm('Retry all failed items?')) return;
    try {
        await apolloClient.mutate({
            mutation: gql`
                mutation RetryImages($input: RetryImagesInput!) {
                    retryImages(input: $input) { retriedCount }
                }
            `,
            variables: {input: {jobId: currentJobId, retryAll: true}}
        });
        await loadJobDetail(currentJobId);
    } catch (error) {
        console.error('Error retrying failed items:', error);
        alert('Failed to retry items');
    }
};

window.skipAllFailed = function () {
    alert('Skip all not yet implemented');
};

window.retryFailedItem = async function (index) {
    try {
        await apolloClient.mutate({
            mutation: gql`
                mutation RetryImages($input: RetryImagesInput!) {
                    retryImages(input: $input) { retriedCount }
                }
            `,
            variables: {input: {jobId: currentJobId, imageIndices: [index]}}
        });
        await loadJobDetail(currentJobId);
    } catch (error) {
        console.error('Error retrying item:', error);
        alert('Failed to retry item');
    }
};

window.skipFailedItem = function (index) {
    alert('Skip not yet implemented');
};

// Utilities
function formatRelativeTime(isoString) {
    if (!isoString) return '-';
    const date = new Date(isoString);
    const now = new Date();
    const diff = Math.floor((now - date) / 1000);

    if (diff < 60) return 'just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
}

function formatDateTime(isoString) {
    if (!isoString) return '-';
    return new Date(isoString).toLocaleString();
}

function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function calculateEta(job) {
    if (job.status !== 'RUNNING') return '-';
    if (!job.totalItems || !job.completedItems) return 'Calculating...';

    const remaining = job.totalItems - job.completedItems;
    if (remaining <= 0) return 'Almost done';

    // Simple estimate: assume 1 item per second
    const seconds = remaining;
    if (seconds < 60) return `~${seconds}s`;
    if (seconds < 3600) return `~${Math.ceil(seconds / 60)}m`;
    return `~${Math.ceil(seconds / 3600)}h`;
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

