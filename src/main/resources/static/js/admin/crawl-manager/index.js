/**
 * Crawl Manager - Main Entry Point
 * Unified crawl job management with sidebar layout
 */

import {initSidebar, refreshSidebar, selectJob} from './sidebar.js';
import {hideJobDetail, initDetailPanel, showJobDetail, updateJobProgress} from './detail-panel.js';
import {closeWizard, initWizard, openWizard, selectType, startCrawl, wizardBack, wizardNext} from './create-wizard.js';
import {apolloClient, gql} from '../graphql/apollo-client.js';

// State
let selectedJobId = null;
let subscriptions = [];

// Global Events Subscription
const GLOBAL_EVENTS_SUBSCRIPTION = gql`
    subscription GlobalCrawlEvents {
        globalCrawlEvents {
            eventType
            jobId
            job {
                id
                type
                status
                targetName
                percent
            }
            message
            timestamp
        }
    }
`;

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', () => {
    init();
});

async function init() {
    // Initialize feather icons
    if (typeof feather !== 'undefined') {
        feather.replace();
    }

    // Initialize components
    await initSidebar();
    initDetailPanel();
    initWizard();

    // Setup global event listeners
    setupEventListeners();

    // Start subscriptions
    startSubscriptions();

    // Check for job ID in URL
    checkUrlForJob();
}

function setupEventListeners() {
    // Search input
    const searchInput = document.getElementById('searchInput');
    let searchTimeout;
    searchInput?.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            refreshSidebar({search: e.target.value});
        }, 300);
    });

    // Type filter
    document.getElementById('filterType')?.addEventListener('change', (e) => {
        refreshSidebar({type: e.target.value || null});
    });

    // Close context menu on click outside
    document.addEventListener('click', () => {
        document.getElementById('contextMenu')?.classList.remove('show');
    });

    // Handle keyboard shortcuts
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeWizard();
            document.getElementById('contextMenu')?.classList.remove('show');
        }
    });

    // Handle browser back/forward
    window.addEventListener('popstate', (e) => {
        if (e.state?.jobId) {
            selectJobById(e.state.jobId, false);
        } else {
            hideJobDetail();
        }
    });
}

function startSubscriptions() {
    try {
        const subscription = apolloClient.subscribe({
            query: GLOBAL_EVENTS_SUBSCRIPTION
        }).subscribe({
            next: ({data}) => {
                if (data?.globalCrawlEvents) {
                    handleGlobalEvent(data.globalCrawlEvents);
                }
            },
            error: (error) => {
                console.error('Subscription error:', error);
                updateConnectionStatus(false);
            }
        });

        subscriptions.push(subscription);
        updateConnectionStatus(true);
    } catch (error) {
        console.error('Failed to start subscriptions:', error);
        updateConnectionStatus(false);
    }
}

function handleGlobalEvent(event) {
    console.log('Global event:', event);

    // Refresh sidebar on status changes
    if (['JOB_STARTED', 'JOB_COMPLETED', 'JOB_FAILED', 'JOB_PAUSED', 'JOB_RESUMED', 'JOB_CANCELLED', 'JOB_CREATED'].includes(event.eventType)) {
        refreshSidebar();
    }

    // Update selected job if it matches
    if (selectedJobId === event.jobId && event.job) {
        updateJobProgress({
            jobId: event.jobId,
            type: event.job.type,
            status: event.job.status,
            percent: event.job.percent,
            message: event.message
        });
    }
}

function updateConnectionStatus(connected) {
    const statusEl = document.getElementById('connectionStatus');
    if (!statusEl) return;

    const dot = statusEl.querySelector('.connection-dot');
    const text = statusEl.querySelector('span:last-child');

    if (connected) {
        dot?.classList.add('connected');
        if (text) text.textContent = 'Connected';
    } else {
        dot?.classList.remove('connected');
        if (text) text.textContent = 'Disconnected';
    }
}

function checkUrlForJob() {
    const path = window.location.pathname;
    const match = path.match(/\/admin\/crawl\/([a-f0-9-]+)/);
    if (match) {
        selectJobById(match[1], false);
    }
}

// Public API
export function selectJobById(jobId, updateUrl = true) {
    selectedJobId = jobId;

    // Update URL
    if (updateUrl) {
        const url = `/admin/crawl/${jobId}`;
        history.pushState({jobId}, '', url);
    }

    // Show job detail
    showJobDetail(jobId);

    // Mark as selected in sidebar
    selectJob(jobId);
}

export function getSelectedJobId() {
    return selectedJobId;
}

// Expose functions to window for inline handlers
window.refreshSidebar = refreshSidebar;
window.openWizard = openWizard;
window.closeWizard = closeWizard;
window.wizardNext = wizardNext;
window.wizardBack = wizardBack;
window.startCrawl = startCrawl;
window.selectType = selectType;
window.selectJobById = selectJobById;

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    subscriptions.forEach(sub => sub.unsubscribe());
});

