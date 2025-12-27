/**
 * Crawl Message Log Component
 * Virtual scrolling with real-time updates via GraphQL subscriptions
 */

const CrawlLog = (() => {
    'use strict';

    // Configuration
    const CONFIG = {
        pageSize: 50,
        maxMessages: 1000,
        autoScroll: true,
        virtualScrollBuffer: 10
    };

    // State
    let containerEl = null;
    let messagesContainer = null;
    let messages = [];
    let currentJobId = null;
    let subscription = null;
    let filterState = {
        levels: [],
        search: ''
    };
    let isAutoScrollEnabled = true;

    // Level configuration
    const LEVEL_CONFIG = {
        INFO: {icon: 'ℹ️', color: 'info', badge: 'bg-info'},
        WARN: {icon: '⚠️', color: 'warning', badge: 'bg-warning'},
        ERROR: {icon: '❌', color: 'danger', badge: 'bg-danger'}
    };

    /**
     * Initialize log component
     */
    function init(container) {
        containerEl = typeof container === 'string'
            ? document.querySelector(container)
            : container;

        if (!containerEl) {
            console.error('[CrawlLog] Container not found');
            return;
        }

        containerEl.innerHTML = `
            <div class="crawl-log h-100 d-flex flex-column">
                <div class="log-toolbar d-flex align-items-center p-2 border-bottom">
                    <div class="log-filter btn-group btn-group-sm me-2">
                        <button class="btn btn-outline-secondary active" data-level="ALL">All</button>
                        <button class="btn btn-outline-info" data-level="INFO">
                            ${LEVEL_CONFIG.INFO.icon} Info
                        </button>
                        <button class="btn btn-outline-warning" data-level="WARN">
                            ${LEVEL_CONFIG.WARN.icon} Warn
                        </button>
                        <button class="btn btn-outline-danger" data-level="ERROR">
                            ${LEVEL_CONFIG.ERROR.icon} Error
                        </button>
                    </div>
                    <div class="log-search flex-grow-1 me-2">
                        <input type="text" class="form-control form-control-sm" 
                               placeholder="Search logs..." id="log-search-input">
                    </div>
                    <div class="log-actions">
                        <button class="btn btn-sm btn-outline-secondary me-1" id="log-auto-scroll" 
                                title="Auto-scroll">
                            <i data-feather="arrow-down"></i>
                        </button>
                        <button class="btn btn-sm btn-outline-secondary" id="log-clear" 
                                title="Clear logs">
                            <i data-feather="trash-2"></i>
                        </button>
                    </div>
                </div>
                <div class="log-stats px-2 py-1 border-bottom bg-light small">
                    <span class="text-muted">
                        <span id="log-count">0</span> messages
                        <span id="log-filtered" class="ms-2 d-none">(filtered)</span>
                    </span>
                </div>
                <div class="log-messages flex-grow-1 overflow-auto" id="log-messages-container">
                    <div class="log-content font-monospace small"></div>
                </div>
            </div>
        `;

        messagesContainer = containerEl.querySelector('.log-content');

        bindEvents();
        feather.replace();
    }

    /**
     * Bind event handlers
     */
    function bindEvents() {
        // Level filter buttons
        containerEl.querySelectorAll('.log-filter button').forEach(btn => {
            btn.addEventListener('click', () => {
                const level = btn.dataset.level;
                setLevelFilter(level === 'ALL' ? [] : [level]);

                // Update button states
                containerEl.querySelectorAll('.log-filter button').forEach(b => {
                    b.classList.remove('active');
                });
                btn.classList.add('active');
            });
        });

        // Search input
        const searchInput = containerEl.querySelector('#log-search-input');
        let searchDebounce = null;
        searchInput.addEventListener('input', () => {
            clearTimeout(searchDebounce);
            searchDebounce = setTimeout(() => {
                filterState.search = searchInput.value;
                renderMessages();
            }, 200);
        });

        // Auto-scroll toggle
        const autoScrollBtn = containerEl.querySelector('#log-auto-scroll');
        autoScrollBtn.addEventListener('click', () => {
            isAutoScrollEnabled = !isAutoScrollEnabled;
            autoScrollBtn.classList.toggle('active', isAutoScrollEnabled);
            if (isAutoScrollEnabled) {
                scrollToBottom();
            }
        });

        // Clear button
        containerEl.querySelector('#log-clear').addEventListener('click', () => {
            clearMessages();
        });

        // Scroll detection for auto-scroll
        const scrollContainer = containerEl.querySelector('#log-messages-container');
        scrollContainer.addEventListener('scroll', () => {
            const {scrollTop, scrollHeight, clientHeight} = scrollContainer;
            const isAtBottom = scrollHeight - scrollTop - clientHeight < 50;

            if (!isAtBottom && isAutoScrollEnabled) {
                // User scrolled up, disable auto-scroll
                isAutoScrollEnabled = false;
                containerEl.querySelector('#log-auto-scroll').classList.remove('active');
            }
        });
    }

    /**
     * Load messages for a job
     */
    async function loadMessages(jobId) {
        currentJobId = jobId;
        messages = [];

        try {
            const data = await CrawlApolloClient.query(
                CrawlGraphQL.JOB_DETAIL_QUERY,
                {id: jobId, childrenFirst: 0, messagesLast: CONFIG.pageSize}
            );

            if (data.crawlJob?.messages?.edges) {
                messages = data.crawlJob.messages.edges.map(e => e.node);
            }

            renderMessages();
            subscribeToNewMessages(jobId);
        } catch (error) {
            console.error('[CrawlLog] Failed to load messages:', error);
        }
    }

    /**
     * Subscribe to new messages
     */
    function subscribeToNewMessages(jobId) {
        // Unsubscribe from previous
        if (subscription) {
            subscription.unsubscribe();
        }

        subscription = CrawlApolloClient.subscribe(
            CrawlGraphQL.MESSAGE_SUBSCRIPTION,
            {jobId},
            (data) => {
                const message = data.crawlMessage;
                if (message) {
                    addMessage(message);
                }
            },
            (error) => {
                console.error('[CrawlLog] Subscription error:', error);
            }
        );
    }

    /**
     * Add a new message
     */
    function addMessage(message) {
        messages.push(message);

        // Trim if exceeds max
        if (messages.length > CONFIG.maxMessages) {
            messages = messages.slice(-CONFIG.maxMessages);
        }

        // Add to DOM
        if (matchesFilter(message)) {
            appendMessageElement(message);
            updateStats();

            if (isAutoScrollEnabled) {
                scrollToBottom();
            }
        }
    }

    /**
     * Render all messages
     */
    function renderMessages() {
        const filtered = messages.filter(matchesFilter);

        messagesContainer.innerHTML = '';
        filtered.forEach(msg => appendMessageElement(msg));

        updateStats();

        if (isAutoScrollEnabled) {
            scrollToBottom();
        }
    }

    /**
     * Append a message element to the container
     */
    function appendMessageElement(message) {
        const levelConfig = LEVEL_CONFIG[message.level] || LEVEL_CONFIG.INFO;
        const timestamp = formatTimestamp(message.timestamp);

        const div = document.createElement('div');
        div.className = `log-entry py-1 px-2 border-bottom text-${levelConfig.color}`;
        div.innerHTML = `
            <span class="log-time text-muted me-2">${timestamp}</span>
            <span class="log-level badge ${levelConfig.badge} me-2">${message.level}</span>
            <span class="log-message">${escapeHtml(message.message)}</span>
        `;

        messagesContainer.appendChild(div);
    }

    /**
     * Check if message matches current filter
     */
    function matchesFilter(message) {
        // Level filter
        if (filterState.levels.length > 0 && !filterState.levels.includes(message.level)) {
            return false;
        }

        // Search filter
        if (filterState.search && !message.message.toLowerCase().includes(filterState.search.toLowerCase())) {
            return false;
        }

        return true;
    }

    /**
     * Set level filter
     */
    function setLevelFilter(levels) {
        filterState.levels = levels;
        renderMessages();
    }

    /**
     * Clear all messages
     */
    function clearMessages() {
        messages = [];
        messagesContainer.innerHTML = '';
        updateStats();
    }

    /**
     * Update statistics display
     */
    function updateStats() {
        const total = messages.length;
        const filtered = messages.filter(matchesFilter).length;

        containerEl.querySelector('#log-count').textContent = filtered;

        const filteredSpan = containerEl.querySelector('#log-filtered');
        if (filtered < total) {
            filteredSpan.classList.remove('d-none');
            filteredSpan.textContent = `(${total - filtered} hidden)`;
        } else {
            filteredSpan.classList.add('d-none');
        }
    }

    /**
     * Scroll to bottom of log container
     */
    function scrollToBottom() {
        const scrollContainer = containerEl.querySelector('#log-messages-container');
        scrollContainer.scrollTop = scrollContainer.scrollHeight;
    }

    /**
     * Format timestamp
     */
    function formatTimestamp(timestamp) {
        if (!timestamp) return '';
        const date = new Date(timestamp);
        return date.toLocaleTimeString('en-US', {hour12: false});
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Cleanup subscriptions
     */
    function cleanup() {
        if (subscription) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    /**
     * Get message count
     */
    function getMessageCount() {
        return {
            total: messages.length,
            filtered: messages.filter(matchesFilter).length,
            byLevel: {
                INFO: messages.filter(m => m.level === 'INFO').length,
                WARN: messages.filter(m => m.level === 'WARN').length,
                ERROR: messages.filter(m => m.level === 'ERROR').length
            }
        };
    }

    // Public API
    return {
        init,
        loadMessages,
        addMessage,
        clearMessages,
        setLevelFilter,
        cleanup,
        getMessageCount,
        LEVEL_CONFIG
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CrawlLog;
}

