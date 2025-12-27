/**
 * Crawl Images Component
 * Displays image download status with blurhash previews
 */

const CrawlImages = (() => {
    'use strict';

    // Status configuration
    const STATUS_CONFIG = {
        PENDING: {icon: '○', color: 'secondary', text: 'Pending'},
        DOWNLOADING: {icon: '●', color: 'primary', text: 'Downloading'},
        COMPLETED: {icon: '✓', color: 'success', text: 'Completed'},
        FAILED: {icon: '✗', color: 'danger', text: 'Failed'},
        SKIPPED: {icon: '⊘', color: 'warning', text: 'Skipped'}
    };

    // State
    let containerEl = null;
    let currentJobId = null;
    let images = [];
    let subscription = null;

    /**
     * Initialize component
     */
    function init(container) {
        containerEl = typeof container === 'string'
            ? document.querySelector(container)
            : container;

        if (!containerEl) {
            console.error('[CrawlImages] Container not found');
            return;
        }

        containerEl.innerHTML = `
            <div class="crawl-images h-100 d-flex flex-column">
                <div class="images-header d-flex align-items-center p-2 border-bottom">
                    <h6 class="mb-0 me-auto">
                        <i data-feather="image" class="me-1"></i>
                        Images
                        <span class="badge bg-primary ms-1" id="images-count">0</span>
                    </h6>
                    <div class="images-stats small me-3">
                        <span class="text-success me-2">
                            <i data-feather="check" class="icon-sm"></i>
                            <span id="images-completed">0</span>
                        </span>
                        <span class="text-danger me-2">
                            <i data-feather="x" class="icon-sm"></i>
                            <span id="images-failed">0</span>
                        </span>
                        <span class="text-warning">
                            <i data-feather="clock" class="icon-sm"></i>
                            <span id="images-pending">0</span>
                        </span>
                    </div>
                    <div class="images-view btn-group btn-group-sm">
                        <button class="btn btn-outline-secondary active" data-view="grid" title="Grid view">
                            <i data-feather="grid"></i>
                        </button>
                        <button class="btn btn-outline-secondary" data-view="list" title="List view">
                            <i data-feather="list"></i>
                        </button>
                    </div>
                </div>
                <div class="images-progress p-2 border-bottom bg-light">
                    <div class="progress" style="height: 4px;">
                        <div class="progress-bar bg-success" id="images-progress-bar" style="width: 0%"></div>
                    </div>
                </div>
                <div class="images-list flex-grow-1 overflow-auto p-2" id="images-container">
                    <div class="text-center text-muted py-4">
                        <i data-feather="image" class="mb-2" style="width: 32px; height: 32px;"></i>
                        <p>No images</p>
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
        // View toggle
        containerEl.querySelectorAll('.images-view button').forEach(btn => {
            btn.addEventListener('click', () => {
                containerEl.querySelectorAll('.images-view button').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                render(btn.dataset.view);
            });
        });
    }

    /**
     * Load images for a job
     */
    async function loadImages(jobId) {
        currentJobId = jobId;

        try {
            const data = await CrawlApolloClient.query(
                CrawlGraphQL.JOB_DETAIL_QUERY,
                {id: jobId, childrenFirst: 0, messagesLast: 0}
            );

            images = data.crawlJob?.images || [];
            render();
            subscribeToUpdates(jobId);
        } catch (error) {
            console.error('[CrawlImages] Failed to load:', error);
        }
    }

    /**
     * Subscribe to image progress updates
     */
    function subscribeToUpdates(jobId) {
        if (subscription) {
            subscription.unsubscribe();
        }

        subscription = CrawlApolloClient.subscribe(
            CrawlGraphQL.IMAGE_PROGRESS_SUBSCRIPTION,
            {jobId},
            (data) => {
                const update = data.imageProgress;
                if (update) {
                    updateImage(update);
                }
            }
        );
    }

    /**
     * Update a single image
     */
    function updateImage(update) {
        const index = images.findIndex(img => img.index === update.index);
        if (index !== -1) {
            images[index] = {...images[index], ...update};
        } else {
            images.push(update);
        }
        render();
    }

    /**
     * Render images
     */
    function render(view = 'grid') {
        const container = containerEl.querySelector('#images-container');

        // Update counts
        const completed = images.filter(i => i.status === 'COMPLETED').length;
        const failed = images.filter(i => i.status === 'FAILED').length;
        const pending = images.filter(i => i.status === 'PENDING' || i.status === 'DOWNLOADING').length;

        containerEl.querySelector('#images-count').textContent = images.length;
        containerEl.querySelector('#images-completed').textContent = completed;
        containerEl.querySelector('#images-failed').textContent = failed;
        containerEl.querySelector('#images-pending').textContent = pending;

        // Update progress bar
        const percent = images.length > 0 ? (completed * 100 / images.length) : 0;
        containerEl.querySelector('#images-progress-bar').style.width = `${percent}%`;

        if (images.length === 0) {
            container.innerHTML = `
                <div class="text-center text-muted py-4">
                    <i data-feather="image" class="mb-2" style="width: 32px; height: 32px;"></i>
                    <p>No images</p>
                </div>
            `;
            feather.replace();
            return;
        }

        if (view === 'grid') {
            renderGrid(container);
        } else {
            renderList(container);
        }

        feather.replace();
    }

    /**
     * Render grid view
     */
    function renderGrid(container) {
        container.innerHTML = `
            <div class="row row-cols-2 row-cols-md-4 row-cols-lg-6 g-2">
                ${images.map(img => renderGridItem(img)).join('')}
            </div>
        `;
    }

    /**
     * Render a grid item
     */
    function renderGridItem(img) {
        const statusConfig = STATUS_CONFIG[img.status] || STATUS_CONFIG.PENDING;
        const hasPreview = img.blurhash && BlurHashDecoder.isValidBlurhash(img.blurhash);

        return `
            <div class="col">
                <div class="image-card position-relative rounded overflow-hidden" 
                     style="aspect-ratio: 3/4; background: #f0f0f0;"
                     data-index="${img.index}">
                    ${hasPreview ? `
                        <div class="image-preview position-absolute w-100 h-100" 
                             data-blurhash="${img.blurhash}"></div>
                    ` : ''}
                    ${img.path ? `
                        <img src="${img.path}" class="position-absolute w-100 h-100" 
                             style="object-fit: cover;" loading="lazy"
                             onerror="this.style.display='none'">
                    ` : ''}
                    <div class="image-overlay position-absolute bottom-0 start-0 end-0 p-1 
                                bg-dark bg-opacity-75 text-white small d-flex justify-content-between">
                        <span>#${img.index}</span>
                        <span class="badge bg-${statusConfig.color}">${statusConfig.icon}</span>
                    </div>
                    ${img.status === 'DOWNLOADING' ? `
                        <div class="position-absolute top-50 start-50 translate-middle">
                            <span class="spinner-border spinner-border-sm text-primary"></span>
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    /**
     * Render list view
     */
    function renderList(container) {
        container.innerHTML = `
            <div class="list-group list-group-flush">
                ${images.map(img => renderListItem(img)).join('')}
            </div>
        `;
    }

    /**
     * Render a list item
     */
    function renderListItem(img) {
        const statusConfig = STATUS_CONFIG[img.status] || STATUS_CONFIG.PENDING;
        const sizeText = img.size ? formatBytes(img.size) : '-';

        return `
            <div class="list-group-item d-flex align-items-center py-2" data-index="${img.index}">
                <div class="image-thumb me-3" style="width: 48px; height: 48px; background: #f0f0f0;">
                    ${img.blurhash && BlurHashDecoder.isValidBlurhash(img.blurhash) ? `
                        <div class="w-100 h-100 rounded" data-blurhash="${img.blurhash}"></div>
                    ` : ''}
                </div>
                <div class="flex-grow-1">
                    <div class="d-flex align-items-center">
                        <span class="badge bg-secondary me-2">#${img.index}</span>
                        <span class="text-truncate small" title="${img.originalUrl}">
                            ${img.originalUrl || 'Unknown'}
                        </span>
                    </div>
                    ${img.error ? `
                        <div class="small text-danger">${img.error}</div>
                    ` : ''}
                </div>
                <div class="text-end me-3">
                    <div class="small text-muted">${sizeText}</div>
                </div>
                <span class="badge bg-${statusConfig.color}">${statusConfig.text}</span>
            </div>
        `;
    }

    /**
     * Apply blurhash previews after render
     */
    function applyBlurhashPreviews() {
        containerEl.querySelectorAll('[data-blurhash]').forEach(el => {
            const blurhash = el.dataset.blurhash;
            if (blurhash) {
                BlurHashDecoder.applyAsBackground(el, blurhash);
            }
        });
    }

    /**
     * Format bytes to human readable
     */
    function formatBytes(bytes) {
        if (!bytes || bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    /**
     * Cleanup subscription
     */
    function cleanup() {
        if (subscription) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    /**
     * Get image stats
     */
    function getStats() {
        return {
            total: images.length,
            completed: images.filter(i => i.status === 'COMPLETED').length,
            failed: images.filter(i => i.status === 'FAILED').length,
            pending: images.filter(i => i.status === 'PENDING' || i.status === 'DOWNLOADING').length,
            totalSize: images.reduce((sum, i) => sum + (i.size || 0), 0)
        };
    }

    // Public API
    return {
        init,
        loadImages,
        updateImage,
        cleanup,
        getStats,
        applyBlurhashPreviews,
        STATUS_CONFIG
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CrawlImages;
}

