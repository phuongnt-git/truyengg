/**
 * Failed Items Management Component
 * Displays and manages failed crawl items with retry/skip functionality
 */

const CrawlFailedItems = (() => {
    'use strict';

    // State
    let containerEl = null;
    let currentJobId = null;
    let failedItems = [];
    let selectedIndices = new Set();

    /**
     * Initialize component
     */
    function init(container) {
        containerEl = typeof container === 'string'
            ? document.querySelector(container)
            : container;

        if (!containerEl) {
            console.error('[CrawlFailedItems] Container not found');
            return;
        }

        containerEl.innerHTML = `
            <div class="failed-items h-100 d-flex flex-column">
                <div class="failed-header d-flex align-items-center p-2 border-bottom">
                    <h6 class="mb-0 me-auto">
                        <i data-feather="alert-circle" class="me-1"></i>
                        Failed Items
                        <span class="badge bg-danger ms-1" id="failed-count">0</span>
                    </h6>
                    <div class="failed-actions">
                        <button class="btn btn-sm btn-outline-primary me-1" id="retry-selected" 
                                disabled title="Retry selected">
                            <i data-feather="refresh-cw"></i> Retry
                        </button>
                        <button class="btn btn-sm btn-outline-warning me-1" id="skip-selected" 
                                disabled title="Skip selected">
                            <i data-feather="skip-forward"></i> Skip
                        </button>
                        <button class="btn btn-sm btn-success" id="retry-all" title="Retry all">
                            <i data-feather="refresh-cw"></i> Retry All
                        </button>
                    </div>
                </div>
                <div class="failed-toolbar p-2 border-bottom bg-light">
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="select-all-failed">
                        <label class="form-check-label small" for="select-all-failed">
                            Select all
                        </label>
                    </div>
                </div>
                <div class="failed-list flex-grow-1 overflow-auto" id="failed-items-list">
                    <div class="text-center text-muted py-4">
                        <i data-feather="check-circle" class="mb-2" style="width: 32px; height: 32px;"></i>
                        <p>No failed items</p>
                    </div>
                </div>
            </div>
        `;

        bindEvents();
        feather.replace();
    }

    /**
     * Bind event handlers
     */
    function bindEvents() {
        // Select all
        containerEl.querySelector('#select-all-failed').addEventListener('change', (e) => {
            if (e.target.checked) {
                selectAll();
            } else {
                deselectAll();
            }
        });

        // Retry selected
        containerEl.querySelector('#retry-selected').addEventListener('click', () => {
            retrySelected();
        });

        // Skip selected
        containerEl.querySelector('#skip-selected').addEventListener('click', () => {
            skipSelected();
        });

        // Retry all
        containerEl.querySelector('#retry-all').addEventListener('click', () => {
            retryAll();
        });
    }

    /**
     * Load failed items for a job
     */
    async function loadFailedItems(jobId) {
        currentJobId = jobId;
        selectedIndices.clear();

        try {
            const data = await CrawlApolloClient.query(
                CrawlGraphQL.JOB_DETAIL_QUERY,
                {id: jobId, childrenFirst: 0, messagesLast: 0}
            );

            failedItems = data.crawlJob?.failedItemsList?.items || [];
            render();
        } catch (error) {
            console.error('[CrawlFailedItems] Failed to load:', error);
        }
    }

    /**
     * Render failed items list
     */
    function render() {
        const listEl = containerEl.querySelector('#failed-items-list');
        const countEl = containerEl.querySelector('#failed-count');

        countEl.textContent = failedItems.length;

        if (failedItems.length === 0) {
            listEl.innerHTML = `
                <div class="text-center text-muted py-4">
                    <i data-feather="check-circle" class="mb-2" style="width: 32px; height: 32px;"></i>
                    <p>No failed items</p>
                </div>
            `;
            feather.replace();
            return;
        }

        listEl.innerHTML = failedItems.map(item => `
            <div class="failed-item d-flex align-items-center p-2 border-bottom ${selectedIndices.has(item.index) ? 'selected bg-light' : ''}"
                 data-index="${item.index}">
                <div class="form-check me-2">
                    <input class="form-check-input failed-checkbox" type="checkbox" 
                           data-index="${item.index}" ${selectedIndices.has(item.index) ? 'checked' : ''}>
                </div>
                <div class="failed-info flex-grow-1">
                    <div class="d-flex align-items-center">
                        <span class="badge bg-secondary me-2">#${item.index}</span>
                        <span class="text-truncate" title="${item.name || item.url}">
                            ${item.name || item.url || 'Unknown item'}
                        </span>
                    </div>
                    <div class="small text-muted text-truncate" title="${item.error}">
                        ${item.error || 'Unknown error'}
                    </div>
                </div>
                <div class="failed-meta text-end me-2">
                    <span class="badge bg-warning" title="Retry count">
                        ${item.retryCount} retries
                    </span>
                </div>
                <div class="failed-actions">
                    <button class="btn btn-sm btn-link retry-single" data-index="${item.index}" title="Retry">
                        <i data-feather="refresh-cw" class="icon-sm"></i>
                    </button>
                    <button class="btn btn-sm btn-link skip-single" data-index="${item.index}" title="Skip">
                        <i data-feather="x" class="icon-sm"></i>
                    </button>
                </div>
            </div>
        `).join('');

        // Bind item events
        listEl.querySelectorAll('.failed-checkbox').forEach(cb => {
            cb.addEventListener('change', (e) => {
                const index = parseInt(e.target.dataset.index);
                if (e.target.checked) {
                    selectedIndices.add(index);
                } else {
                    selectedIndices.delete(index);
                }
                updateSelectionUI();
            });
        });

        listEl.querySelectorAll('.retry-single').forEach(btn => {
            btn.addEventListener('click', () => {
                const index = parseInt(btn.dataset.index);
                retryItems([index]);
            });
        });

        listEl.querySelectorAll('.skip-single').forEach(btn => {
            btn.addEventListener('click', () => {
                const index = parseInt(btn.dataset.index);
                skipItems([index]);
            });
        });

        feather.replace();
        updateSelectionUI();
    }

    /**
     * Select all items
     */
    function selectAll() {
        failedItems.forEach(item => selectedIndices.add(item.index));
        render();
    }

    /**
     * Deselect all items
     */
    function deselectAll() {
        selectedIndices.clear();
        render();
    }

    /**
     * Update selection UI state
     */
    function updateSelectionUI() {
        const hasSelection = selectedIndices.size > 0;
        containerEl.querySelector('#retry-selected').disabled = !hasSelection;
        containerEl.querySelector('#skip-selected').disabled = !hasSelection;

        const selectAllCb = containerEl.querySelector('#select-all-failed');
        selectAllCb.checked = selectedIndices.size === failedItems.length && failedItems.length > 0;
        selectAllCb.indeterminate = selectedIndices.size > 0 && selectedIndices.size < failedItems.length;
    }

    /**
     * Retry selected items
     */
    async function retrySelected() {
        if (selectedIndices.size === 0) return;
        await retryItems([...selectedIndices]);
    }

    /**
     * Skip selected items
     */
    async function skipSelected() {
        if (selectedIndices.size === 0) return;
        await skipItems([...selectedIndices]);
    }

    /**
     * Retry all failed items
     */
    async function retryAll() {
        if (!currentJobId) return;

        try {
            await CrawlApolloClient.mutate(
                CrawlGraphQL.RETRY_ALL_FAILED_ITEMS,
                {id: currentJobId}
            );

            showToast('All failed items queued for retry', 'success');

            // Reload after brief delay
            setTimeout(() => loadFailedItems(currentJobId), 1000);
        } catch (error) {
            console.error('[CrawlFailedItems] Retry all failed:', error);
            showToast('Failed to retry items', 'danger');
        }
    }

    /**
     * Retry specific items
     */
    async function retryItems(indices) {
        if (!currentJobId || indices.length === 0) return;

        try {
            await CrawlApolloClient.mutate(
                CrawlGraphQL.RETRY_FAILED_IMAGES,
                {jobId: currentJobId, indices}
            );

            showToast(`${indices.length} item(s) queued for retry`, 'success');

            // Remove from local list
            failedItems = failedItems.filter(item => !indices.includes(item.index));
            selectedIndices = new Set([...selectedIndices].filter(i => !indices.includes(i)));
            render();
        } catch (error) {
            console.error('[CrawlFailedItems] Retry failed:', error);
            showToast('Failed to retry items', 'danger');
        }
    }

    /**
     * Skip specific items
     */
    async function skipItems(indices) {
        if (!currentJobId || indices.length === 0) return;

        try {
            await CrawlApolloClient.mutate(
                CrawlGraphQL.SKIP_FAILED_ITEMS,
                {id: currentJobId, indices}
            );

            showToast(`${indices.length} item(s) skipped`, 'warning');

            // Remove from local list
            failedItems = failedItems.filter(item => !indices.includes(item.index));
            selectedIndices = new Set([...selectedIndices].filter(i => !indices.includes(i)));
            render();
        } catch (error) {
            console.error('[CrawlFailedItems] Skip failed:', error);
            showToast('Failed to skip items', 'danger');
        }
    }

    /**
     * Show toast notification
     */
    function showToast(message, type = 'info') {
        if (typeof Toastify !== 'undefined') {
            Toastify({
                text: message,
                duration: 3000,
                gravity: 'top',
                position: 'right',
                backgroundColor: type === 'success' ? '#28a745'
                    : type === 'danger' ? '#dc3545'
                        : type === 'warning' ? '#ffc107'
                            : '#17a2b8'
            }).showToast();
        } else {
            console.log(`[Toast ${type}] ${message}`);
        }
    }

    /**
     * Get failed items count
     */
    function getCount() {
        return failedItems.length;
    }

    /**
     * Refresh failed items
     */
    function refresh() {
        if (currentJobId) {
            loadFailedItems(currentJobId);
        }
    }

    // Public API
    return {
        init,
        loadFailedItems,
        retryAll,
        getCount,
        refresh
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CrawlFailedItems;
}

