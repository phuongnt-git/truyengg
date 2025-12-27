/**
 * Crawl Job Tree Component
 * Displays job hierarchy with lazy loading via GraphQL pagination
 */

const CrawlTree = (() => {
    'use strict';

    // Configuration
    const CONFIG = {
        pageSize: 10,
        animationDuration: 200
    };

    // State
    let containerEl = null;
    let expandedNodes = new Set();
    let loadingNodes = new Set();
    let childrenCache = new Map();
    let onSelectCallback = null;
    let activeSubscriptions = [];

    // Status icons and colors
    const STATUS_CONFIG = {
        PENDING: {icon: '○', color: 'secondary', text: 'Pending'},
        RUNNING: {icon: '●', color: 'primary', text: 'Running'},
        PAUSED: {icon: '⏸', color: 'warning', text: 'Paused'},
        COMPLETED: {icon: '✓', color: 'success', text: 'Completed'},
        FAILED: {icon: '✗', color: 'danger', text: 'Failed'},
        CANCELLED: {icon: '⊘', color: 'dark', text: 'Cancelled'}
    };

    // Type icons
    const TYPE_CONFIG = {
        CATEGORY: {icon: '📁', text: 'Category'},
        COMIC: {icon: '📚', text: 'Comic'},
        CHAPTER: {icon: '📄', text: 'Chapter'},
        IMAGE: {icon: '🖼', text: 'Image'}
    };

    /**
     * Initialize tree component
     */
    function init(container, onSelect) {
        containerEl = typeof container === 'string'
            ? document.querySelector(container)
            : container;
        onSelectCallback = onSelect;

        if (!containerEl) {
            console.error('[CrawlTree] Container not found');
            return;
        }

        containerEl.classList.add('crawl-tree');
    }

    /**
     * Render tree with root jobs
     */
    function render(jobs) {
        if (!containerEl) return;

        containerEl.innerHTML = '';

        if (!jobs || jobs.length === 0) {
            containerEl.innerHTML = `
                <div class="text-center text-muted py-4">
                    <i data-feather="inbox" class="mb-2"></i>
                    <p>No jobs found</p>
                </div>
            `;
            feather.replace();
            return;
        }

        const ul = document.createElement('ul');
        ul.className = 'tree-root list-unstyled';

        jobs.forEach(job => {
            ul.appendChild(createTreeNode(job, 0));
        });

        containerEl.appendChild(ul);
        feather.replace();
    }

    /**
     * Create a tree node element
     */
    function createTreeNode(job, depth) {
        const li = document.createElement('li');
        li.className = 'tree-node';
        li.dataset.jobId = job.id;
        li.dataset.depth = depth;

        const statusConfig = STATUS_CONFIG[job.status] || STATUS_CONFIG.PENDING;
        const typeConfig = TYPE_CONFIG[job.type] || TYPE_CONFIG.COMIC;
        const isExpanded = expandedNodes.has(job.id);
        const isLoading = loadingNodes.has(job.id);

        li.innerHTML = `
            <div class="tree-node-content d-flex align-items-center py-1 px-2 ${isExpanded ? 'expanded' : ''}" 
                 style="padding-left: ${depth * 20 + 8}px">
                ${job.hasChildren ? `
                    <button class="tree-toggle btn btn-sm p-0 me-1" data-job-id="${job.id}">
                        <i data-feather="${isExpanded ? 'chevron-down' : 'chevron-right'}" class="icon-sm"></i>
                    </button>
                ` : `
                    <span class="tree-spacer me-1" style="width: 20px"></span>
                `}
                <span class="tree-type me-2" title="${typeConfig.text}">${typeConfig.icon}</span>
                <span class="tree-status badge bg-${statusConfig.color} me-2" title="${statusConfig.text}">
                    ${statusConfig.icon}
                </span>
                <span class="tree-name flex-grow-1 text-truncate" title="${job.targetName || job.targetUrl}">
                    ${job.targetName || 'Unnamed'}
                </span>
                ${job.percent !== undefined ? `
                    <span class="tree-progress me-2">
                        <span class="progress" style="width: 60px; height: 4px;">
                            <span class="progress-bar bg-${statusConfig.color}" style="width: ${job.percent}%"></span>
                        </span>
                    </span>
                ` : ''}
                ${job.childrenCount > 0 ? `
                    <span class="tree-count badge bg-light text-dark me-2" title="${job.childrenCount} children">
                        ${job.childrenCount}
                    </span>
                ` : ''}
                <button class="tree-view btn btn-link btn-sm p-0" data-job-id="${job.id}" title="View details">
                    <i data-feather="eye" class="icon-sm"></i>
                </button>
            </div>
            ${isExpanded ? `
                <ul class="tree-children list-unstyled ${isLoading ? 'loading' : ''}">
                    ${isLoading ? `
                        <li class="tree-loading py-2 ps-4">
                            <span class="spinner-border spinner-border-sm me-2"></span>
                            Loading...
                        </li>
                    ` : ''}
                </ul>
            ` : ''}
        `;

        // Bind events
        bindNodeEvents(li, job);

        // If expanded and has cached children, render them
        if (isExpanded && childrenCache.has(job.id)) {
            const childrenUl = li.querySelector('.tree-children');
            const cached = childrenCache.get(job.id);
            cached.children.forEach(child => {
                childrenUl.appendChild(createTreeNode(child, depth + 1));
            });

            // Add load more button if has more
            if (cached.hasNextPage) {
                childrenUl.appendChild(createLoadMoreButton(job.id, cached.endCursor, depth + 1));
            }
        }

        return li;
    }

    /**
     * Bind events to a tree node
     */
    function bindNodeEvents(li, job) {
        // Toggle expand/collapse
        const toggleBtn = li.querySelector('.tree-toggle');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                toggleNode(job.id);
            });
        }

        // View details
        const viewBtn = li.querySelector('.tree-view');
        if (viewBtn) {
            viewBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (onSelectCallback) {
                    onSelectCallback(job.id);
                }
            });
        }

        // Click on content to select
        const content = li.querySelector('.tree-node-content');
        if (content) {
            content.addEventListener('click', () => {
                selectNode(job.id);
            });
        }
    }

    /**
     * Create load more button
     */
    function createLoadMoreButton(parentId, cursor, depth) {
        const li = document.createElement('li');
        li.className = 'tree-load-more py-1';
        li.style.paddingLeft = `${depth * 20 + 40}px`;
        li.innerHTML = `
            <button class="btn btn-link btn-sm" data-parent-id="${parentId}" data-cursor="${cursor}">
                Load more...
            </button>
        `;

        li.querySelector('button').addEventListener('click', () => {
            loadMoreChildren(parentId, cursor);
        });

        return li;
    }

    /**
     * Toggle node expand/collapse
     */
    async function toggleNode(jobId) {
        if (expandedNodes.has(jobId)) {
            collapseNode(jobId);
        } else {
            await expandNode(jobId);
        }
    }

    /**
     * Expand a node
     */
    async function expandNode(jobId) {
        expandedNodes.add(jobId);

        const nodeEl = containerEl.querySelector(`[data-job-id="${jobId}"]`);
        if (!nodeEl) return;

        // Update toggle icon
        const toggleIcon = nodeEl.querySelector('.tree-toggle i');
        if (toggleIcon) {
            toggleIcon.setAttribute('data-feather', 'chevron-down');
            feather.replace();
        }

        // Create or show children container
        let childrenUl = nodeEl.querySelector('.tree-children');
        if (!childrenUl) {
            childrenUl = document.createElement('ul');
            childrenUl.className = 'tree-children list-unstyled';
            nodeEl.appendChild(childrenUl);
        }

        // Load children if not cached
        if (!childrenCache.has(jobId)) {
            await loadChildren(jobId);
        } else {
            // Render cached children
            const cached = childrenCache.get(jobId);
            childrenUl.innerHTML = '';
            const depth = parseInt(nodeEl.dataset.depth) + 1;

            cached.children.forEach(child => {
                childrenUl.appendChild(createTreeNode(child, depth));
            });

            if (cached.hasNextPage) {
                childrenUl.appendChild(createLoadMoreButton(jobId, cached.endCursor, depth));
            }

            feather.replace();
        }
    }

    /**
     * Collapse a node
     */
    function collapseNode(jobId) {
        expandedNodes.delete(jobId);

        const nodeEl = containerEl.querySelector(`[data-job-id="${jobId}"]`);
        if (!nodeEl) return;

        // Update toggle icon
        const toggleIcon = nodeEl.querySelector('.tree-toggle i');
        if (toggleIcon) {
            toggleIcon.setAttribute('data-feather', 'chevron-right');
            feather.replace();
        }

        // Hide children
        const childrenUl = nodeEl.querySelector('.tree-children');
        if (childrenUl) {
            childrenUl.remove();
        }
    }

    /**
     * Load children for a node
     */
    async function loadChildren(parentId) {
        loadingNodes.add(parentId);
        updateLoadingState(parentId, true);

        try {
            const data = await CrawlApolloClient.query(
                CrawlGraphQL.CHILDREN_QUERY,
                {id: parentId, first: CONFIG.pageSize}
            );

            const connection = data.crawlJob?.children;
            if (connection) {
                const children = connection.edges.map(e => e.node);

                childrenCache.set(parentId, {
                    children,
                    hasNextPage: connection.pageInfo.hasNextPage,
                    endCursor: connection.pageInfo.endCursor,
                    totalCount: connection.totalCount
                });

                // Render children
                const nodeEl = containerEl.querySelector(`[data-job-id="${parentId}"]`);
                const childrenUl = nodeEl?.querySelector('.tree-children');
                if (childrenUl) {
                    childrenUl.innerHTML = '';
                    const depth = parseInt(nodeEl.dataset.depth) + 1;

                    children.forEach(child => {
                        childrenUl.appendChild(createTreeNode(child, depth));
                    });

                    if (connection.pageInfo.hasNextPage) {
                        childrenUl.appendChild(createLoadMoreButton(parentId, connection.pageInfo.endCursor, depth));
                    }

                    feather.replace();
                }
            }
        } catch (error) {
            console.error('[CrawlTree] Failed to load children:', error);
        } finally {
            loadingNodes.delete(parentId);
            updateLoadingState(parentId, false);
        }
    }

    /**
     * Load more children
     */
    async function loadMoreChildren(parentId, cursor) {
        try {
            const data = await CrawlApolloClient.query(
                CrawlGraphQL.CHILDREN_QUERY,
                {id: parentId, first: CONFIG.pageSize, after: cursor}
            );

            const connection = data.crawlJob?.children;
            if (connection) {
                const newChildren = connection.edges.map(e => e.node);
                const cached = childrenCache.get(parentId);

                if (cached) {
                    cached.children = [...cached.children, ...newChildren];
                    cached.hasNextPage = connection.pageInfo.hasNextPage;
                    cached.endCursor = connection.pageInfo.endCursor;
                }

                // Render new children
                const nodeEl = containerEl.querySelector(`[data-job-id="${parentId}"]`);
                const childrenUl = nodeEl?.querySelector('.tree-children');
                if (childrenUl) {
                    // Remove load more button
                    const loadMoreBtn = childrenUl.querySelector('.tree-load-more');
                    if (loadMoreBtn) loadMoreBtn.remove();

                    const depth = parseInt(nodeEl.dataset.depth) + 1;
                    newChildren.forEach(child => {
                        childrenUl.appendChild(createTreeNode(child, depth));
                    });

                    if (connection.pageInfo.hasNextPage) {
                        childrenUl.appendChild(createLoadMoreButton(parentId, connection.pageInfo.endCursor, depth));
                    }

                    feather.replace();
                }
            }
        } catch (error) {
            console.error('[CrawlTree] Failed to load more children:', error);
        }
    }

    /**
     * Update loading state for a node
     */
    function updateLoadingState(jobId, isLoading) {
        const nodeEl = containerEl.querySelector(`[data-job-id="${jobId}"]`);
        const childrenUl = nodeEl?.querySelector('.tree-children');

        if (childrenUl) {
            childrenUl.classList.toggle('loading', isLoading);

            if (isLoading) {
                childrenUl.innerHTML = `
                    <li class="tree-loading py-2 ps-4">
                        <span class="spinner-border spinner-border-sm me-2"></span>
                        Loading...
                    </li>
                `;
            }
        }
    }

    /**
     * Select a node
     */
    function selectNode(jobId) {
        // Remove previous selection
        containerEl.querySelectorAll('.tree-node-content.selected').forEach(el => {
            el.classList.remove('selected');
        });

        // Add selection to current
        const nodeEl = containerEl.querySelector(`[data-job-id="${jobId}"]`);
        const content = nodeEl?.querySelector('.tree-node-content');
        if (content) {
            content.classList.add('selected');
        }

        if (onSelectCallback) {
            onSelectCallback(jobId);
        }
    }

    /**
     * Update a node in the tree
     */
    function updateNode(job) {
        const nodeEl = containerEl.querySelector(`[data-job-id="${job.id}"]`);
        if (nodeEl) {
            const depth = parseInt(nodeEl.dataset.depth);
            const newNode = createTreeNode(job, depth);

            // Preserve expanded state
            if (expandedNodes.has(job.id)) {
                const childrenUl = nodeEl.querySelector('.tree-children');
                if (childrenUl) {
                    const newChildrenUl = newNode.querySelector('.tree-children') || document.createElement('ul');
                    newChildrenUl.className = 'tree-children list-unstyled';
                    newChildrenUl.innerHTML = childrenUl.innerHTML;
                    newNode.appendChild(newChildrenUl);
                }
            }

            nodeEl.replaceWith(newNode);
            feather.replace();
        }
    }

    /**
     * Subscribe to child job creation
     */
    function subscribeToChildCreation(parentId) {
        const subscription = CrawlApolloClient.subscribe(
            CrawlGraphQL.CHILD_CREATED_SUBSCRIPTION,
            {parentJobId: parentId},
            (data) => {
                const newChild = data.childJobCreated;
                if (newChild) {
                    // Add to cache
                    const cached = childrenCache.get(parentId);
                    if (cached) {
                        cached.children.unshift(newChild);
                    }

                    // Add to DOM if expanded
                    if (expandedNodes.has(parentId)) {
                        const nodeEl = containerEl.querySelector(`[data-job-id="${parentId}"]`);
                        const childrenUl = nodeEl?.querySelector('.tree-children');
                        if (childrenUl) {
                            const depth = parseInt(nodeEl.dataset.depth) + 1;
                            childrenUl.prepend(createTreeNode(newChild, depth));
                            feather.replace();
                        }
                    }
                }
            }
        );

        activeSubscriptions.push(subscription);
        return subscription;
    }

    /**
     * Cleanup subscriptions
     */
    function cleanup() {
        activeSubscriptions.forEach(sub => sub.unsubscribe());
        activeSubscriptions = [];
    }

    /**
     * Clear cache and collapse all
     */
    function reset() {
        expandedNodes.clear();
        loadingNodes.clear();
        childrenCache.clear();
        cleanup();
    }

    // Public API
    return {
        init,
        render,
        toggleNode,
        expandNode,
        collapseNode,
        selectNode,
        updateNode,
        subscribeToChildCreation,
        cleanup,
        reset,
        STATUS_CONFIG,
        TYPE_CONFIG
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CrawlTree;
}

