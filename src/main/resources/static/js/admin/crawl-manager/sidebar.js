/**
 * Crawl Manager - Sidebar Component
 * Job tree with collapsible groups and lazy loading
 */

import {apolloClient, gql} from '../graphql/apollo-client.js';
import {selectJobById} from './index.js';

// State
let currentFilters = {type: null, search: ''};
let groupData = {};
let selectedJobId = null;
let contextMenuJobId = null;

// Status groups configuration
const STATUS_GROUPS = [
    {status: 'RUNNING', label: 'Running', expanded: true},
    {status: 'QUEUED', label: 'Queued', expanded: false},
    {status: 'PAUSED', label: 'Paused', expanded: false},
    {status: 'COMPLETED', label: 'Completed', expanded: false},
    {status: 'FAILED', label: 'Failed', expanded: false}
];

// GraphQL Queries
const JOBS_BY_STATUS_QUERY = gql`
    query JobsByStatus($statuses: [CrawlStatus!], $types: [CrawlType!], $search: String, $first: Int, $after: String) {
        crawlJobs(
            filter: { statuses: $statuses, types: $types, rootOnly: true }
            first: $first
            after: $after
            sort: [{ field: CREATED_AT, direction: DESC }]
        ) {
            edges {
                node {
                    id
                    type
                    status
                    targetUrl
                    targetName
                    completedItems
                    totalItems
                    progress {
                        percent
                    }
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
`;

const QUEUE_STATS_QUERY = gql`
    query QueueStats {
        queueStatus {
            totalItems
            pendingItems
            processingItems
            failedItems
        }
    }
`;

// Initialize sidebar
export async function initSidebar() {
    await loadAllGroups();
    await loadQueueStats();
}

// Refresh sidebar with optional filters
export async function refreshSidebar(filters = {}) {
    currentFilters = {...currentFilters, ...filters};
    await loadAllGroups();
    await loadQueueStats();
}

// Load all status groups
async function loadAllGroups() {
    const container = document.getElementById('sidebarTree');
    if (!container) return;

    container.innerHTML = '';

    for (const group of STATUS_GROUPS) {
        await loadGroup(group);
    }

    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

// Load a single group
async function loadGroup(group) {
    const container = document.getElementById('sidebarTree');
    if (!container) return;

    try {
        const {data} = await apolloClient.query({
            query: JOBS_BY_STATUS_QUERY,
            variables: {
                statuses: [group.status],
                types: currentFilters.type ? [currentFilters.type] : null,
                search: currentFilters.search || null,
                first: group.status === 'COMPLETED' ? 20 : 50
            },
            fetchPolicy: 'network-only'
        });

        const jobs = data.crawlJobs;
        groupData[group.status] = {
            ...group,
            jobs: jobs.edges.map(e => e.node),
            hasMore: jobs.pageInfo.hasNextPage,
            endCursor: jobs.pageInfo.endCursor,
            totalCount: jobs.totalCount
        };

        container.innerHTML += renderGroup(groupData[group.status]);
    } catch (error) {
        console.error(`Error loading ${group.status} jobs:`, error);
        container.innerHTML += renderGroupError(group);
    }
}

// Render a group
function renderGroup(group) {
    const isExpanded = group.expanded;
    const statusClass = `status-${group.status.toLowerCase()}`;

    let itemsHtml = '';
    if (group.jobs.length > 0) {
        itemsHtml = group.jobs.map(job => renderJobItem(job)).join('');
        if (group.hasMore) {
            itemsHtml += `
                <div class="tree-load-more">
                    <button class="btn btn-sm btn-outline-secondary" onclick="loadMoreJobs('${group.status}')">
                        Load more...
                    </button>
                </div>
            `;
        }
    } else {
        itemsHtml = '<div class="text-muted text-center py-2" style="font-size: 0.8rem;">No jobs</div>';
    }

    return `
        <div class="tree-group" data-status="${group.status}">
            <div class="tree-group-header ${statusClass} ${isExpanded ? '' : 'collapsed'}" 
                 onclick="toggleGroup('${group.status}')">
                <i data-feather="chevron-down" class="expand-icon"></i>
                <span class="group-label">${group.label}</span>
                <span class="group-count">${group.totalCount}</span>
            </div>
            <div class="tree-group-content ${isExpanded ? 'expanded' : ''}" id="group-${group.status}">
                ${itemsHtml}
            </div>
        </div>
    `;
}

// Render error state for a group
function renderGroupError(group) {
    return `
        <div class="tree-group" data-status="${group.status}">
            <div class="tree-group-header collapsed" onclick="toggleGroup('${group.status}')">
                <i data-feather="chevron-down" class="expand-icon"></i>
                <span class="group-label">${group.label}</span>
                <span class="group-count">?</span>
            </div>
            <div class="tree-group-content" id="group-${group.status}">
                <div class="text-danger text-center py-2" style="font-size: 0.8rem;">Failed to load</div>
            </div>
        </div>
    `;
}

// Render a job item
function renderJobItem(job) {
    const jobType = job.type || job.crawlType;
    const typeClass = `type-${jobType.toLowerCase()}`;
    const typeIcon = getTypeIcon(jobType);
    const title = job.targetName || truncateUrl(job.targetUrl);
    const percent = job.progress?.percent || 0;
    const isSelected = job.id === selectedJobId;

    return `
        <div class="tree-item ${isSelected ? 'selected' : ''}" 
             data-job-id="${job.id}"
             onclick="selectJobById('${job.id}')"
             oncontextmenu="showContextMenu(event, '${job.id}', '${job.status}')">
            <div class="item-icon ${typeClass}">${typeIcon}</div>
            <div class="item-info">
                <div class="item-title">${escapeHtml(title)}</div>
                <div class="item-meta">${job.completedItems || 0}/${job.totalItems || '?'}</div>
            </div>
            ${job.status === 'RUNNING' || job.status === 'PAUSED' ?
        `<div class="item-progress">${percent}%</div>` : ''}
        </div>
    `;
}

// Get type icon
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

// Toggle group expansion
window.toggleGroup = function (status) {
    const header = document.querySelector(`.tree-group[data-status="${status}"] .tree-group-header`);
    const content = document.getElementById(`group-${status}`);

    if (header && content) {
        header.classList.toggle('collapsed');
        content.classList.toggle('expanded');

        if (groupData[status]) {
            groupData[status].expanded = content.classList.contains('expanded');
        }
    }
};

// Load more jobs in a group
window.loadMoreJobs = async function (status) {
    const group = groupData[status];
    if (!group || !group.hasMore) return;

    try {
        const {data} = await apolloClient.query({
            query: JOBS_BY_STATUS_QUERY,
            variables: {
                statuses: [status],
                types: currentFilters.type ? [currentFilters.type] : null,
                search: currentFilters.search || null,
                first: 20,
                after: group.endCursor
            },
            fetchPolicy: 'network-only'
        });

        const newJobs = data.crawlJobs.edges.map(e => e.node);
        group.jobs = [...group.jobs, ...newJobs];
        group.hasMore = data.crawlJobs.pageInfo.hasNextPage;
        group.endCursor = data.crawlJobs.pageInfo.endCursor;

        // Re-render the group content
        const content = document.getElementById(`group-${status}`);
        if (content) {
            let itemsHtml = group.jobs.map(job => renderJobItem(job)).join('');
            if (group.hasMore) {
                itemsHtml += `
                    <div class="tree-load-more">
                        <button class="btn btn-sm btn-outline-secondary" onclick="loadMoreJobs('${status}')">
                            Load more...
                        </button>
                    </div>
                `;
            }
            content.innerHTML = itemsHtml;

            if (typeof feather !== 'undefined') {
                feather.replace();
            }
        }
    } catch (error) {
        console.error(`Error loading more ${status} jobs:`, error);
    }
};

// Load queue stats
async function loadQueueStats() {
    try {
        const {data} = await apolloClient.query({
            query: QUEUE_STATS_QUERY,
            fetchPolicy: 'network-only'
        });

        const stats = data.queueStatus;
        document.getElementById('footerRunning').textContent = stats.processingItems || 0;
        document.getElementById('footerQueued').textContent = stats.pendingItems || 0;
        document.getElementById('footerDownloaded').textContent = `${stats.totalItems || 0} total`;
    } catch (error) {
        console.error('Error loading queue stats:', error);
    }
}

// Select a job in the sidebar
export function selectJob(jobId) {
    selectedJobId = jobId;

    // Update selection visual
    document.querySelectorAll('.tree-item').forEach(item => {
        item.classList.toggle('selected', item.dataset.jobId === jobId);
    });
}

// Show context menu
window.showContextMenu = function (event, jobId, status) {
    event.preventDefault();
    event.stopPropagation();

    contextMenuJobId = jobId;

    const menu = document.getElementById('contextMenu');
    if (!menu) return;

    // Show/hide pause/resume based on status
    document.getElementById('ctxPause').style.display =
        status === 'RUNNING' ? 'flex' : 'none';
    document.getElementById('ctxResume').style.display =
        status === 'PAUSED' ? 'flex' : 'none';

    // Position menu
    menu.style.left = `${event.clientX}px`;
    menu.style.top = `${event.clientY}px`;
    menu.classList.add('show');

    if (typeof feather !== 'undefined') {
        feather.replace();
    }
};

// Context menu actions
window.contextAction = async function (action) {
    document.getElementById('contextMenu')?.classList.remove('show');

    if (!contextMenuJobId) return;

    switch (action) {
        case 'view':
            selectJobById(contextMenuJobId);
            break;
        case 'pause':
            await pauseJob(contextMenuJobId);
            break;
        case 'resume':
            await resumeJob(contextMenuJobId);
            break;
        case 'cancel':
            if (confirm('Are you sure you want to cancel this job?')) {
                await cancelJob(contextMenuJobId);
            }
            break;
        case 'copy':
            const job = findJobById(contextMenuJobId);
            if (job?.targetUrl) {
                navigator.clipboard.writeText(job.targetUrl);
            }
            break;
    }

    contextMenuJobId = null;
};

// Find job by ID in current data
function findJobById(jobId) {
    for (const group of Object.values(groupData)) {
        const job = group.jobs?.find(j => j.id === jobId);
        if (job) return job;
    }
    return null;
}

// Job actions
async function pauseJob(jobId) {
    try {
        await apolloClient.mutate({
            mutation: gql`mutation PauseJob($id: ID!) { pauseCrawlJob(id: $id) { id status } }`,
            variables: {id: jobId}
        });
        await refreshSidebar();
    } catch (error) {
        console.error('Error pausing job:', error);
        alert('Failed to pause job');
    }
}

async function resumeJob(jobId) {
    try {
        await apolloClient.mutate({
            mutation: gql`mutation ResumeJob($id: ID!) { resumeCrawlJob(id: $id) { id status } }`,
            variables: {id: jobId}
        });
        await refreshSidebar();
    } catch (error) {
        console.error('Error resuming job:', error);
        alert('Failed to resume job');
    }
}

async function cancelJob(jobId) {
    try {
        await apolloClient.mutate({
            mutation: gql`mutation CancelJob($id: ID!) { cancelCrawlJob(id: $id) { id status } }`,
            variables: {id: jobId}
        });
        await refreshSidebar();
    } catch (error) {
        console.error('Error cancelling job:', error);
        alert('Failed to cancel job');
    }
}

// Utilities
function truncateUrl(url, maxLength = 40) {
    if (!url) return '-';
    if (url.length <= maxLength) return url;
    return url.substring(0, maxLength) + '...';
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

