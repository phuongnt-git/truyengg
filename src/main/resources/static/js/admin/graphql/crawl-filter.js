/**
 * Filter State Management for Crawl Jobs
 * Handles filter state, debounced search, and GraphQL variable building
 */

const CrawlFilter = (() => {
    'use strict';

    // Default filter state
    const DEFAULT_STATE = {
        search: '',
        types: [],
        statuses: [],
        dateRange: {from: null, to: null},
        percentRange: {min: 0, max: 100},
        rootOnly: true,
        hasFailures: false,
        hasChildren: null,
        sort: [{field: 'CREATED_AT', direction: 'DESC'}]
    };

    // Current state
    let state = {...DEFAULT_STATE};
    let searchDebounce = null;
    let onChangeCallback = null;

    // Available sort fields
    const SORT_FIELDS = [
        {value: 'CREATED_AT', label: 'Created Date'},
        {value: 'UPDATED_AT', label: 'Updated Date'},
        {value: 'STARTED_AT', label: 'Started Date'},
        {value: 'COMPLETED_AT', label: 'Completed Date'},
        {value: 'TARGET_NAME', label: 'Name'},
        {value: 'STATUS', label: 'Status'},
        {value: 'TYPE', label: 'Type'},
        {value: 'PERCENT', label: 'Progress %'},
        {value: 'TOTAL_ITEMS', label: 'Total Items'},
        {value: 'FAILED_ITEMS', label: 'Failed Items'}
    ];

    // Status options
    const STATUS_OPTIONS = [
        {value: 'PENDING', label: 'Pending', icon: '○', color: 'secondary'},
        {value: 'RUNNING', label: 'Running', icon: '●', color: 'primary'},
        {value: 'PAUSED', label: 'Paused', icon: '⏸', color: 'warning'},
        {value: 'COMPLETED', label: 'Completed', icon: '✓', color: 'success'},
        {value: 'FAILED', label: 'Failed', icon: '✗', color: 'danger'},
        {value: 'CANCELLED', label: 'Cancelled', icon: '⊘', color: 'dark'}
    ];

    // Type options
    const TYPE_OPTIONS = [
        {value: 'CATEGORY', label: 'Category', icon: '📁'},
        {value: 'COMIC', label: 'Comic', icon: '📚'},
        {value: 'CHAPTER', label: 'Chapter', icon: '📄'},
        {value: 'IMAGE', label: 'Image', icon: '🖼'}
    ];

    /**
     * Initialize filter with callback
     */
    function init(onChange) {
        onChangeCallback = onChange;
        reset();
    }

    /**
     * Update a single filter value
     */
    function updateFilter(key, value) {
        state[key] = value;
        triggerChange();
    }

    /**
     * Handle search input with debounce
     */
    function handleSearch(value) {
        state.search = value;

        clearTimeout(searchDebounce);
        searchDebounce = setTimeout(() => {
            triggerChange();
        }, 300); // 300ms debounce
    }

    /**
     * Set quick filter (single status)
     */
    function setQuickFilter(status) {
        if (status === 'ALL') {
            state.statuses = [];
        } else {
            state.statuses = [status];
        }
        triggerChange();
    }

    /**
     * Toggle a type filter
     */
    function toggleType(type) {
        const index = state.types.indexOf(type);
        if (index === -1) {
            state.types.push(type);
        } else {
            state.types.splice(index, 1);
        }
        triggerChange();
    }

    /**
     * Toggle a status filter
     */
    function toggleStatus(status) {
        const index = state.statuses.indexOf(status);
        if (index === -1) {
            state.statuses.push(status);
        } else {
            state.statuses.splice(index, 1);
        }
        triggerChange();
    }

    /**
     * Set date range
     */
    function setDateRange(from, to) {
        state.dateRange = {from, to};
        triggerChange();
    }

    /**
     * Set percent range
     */
    function setPercentRange(min, max) {
        state.percentRange = {min, max};
        triggerChange();
    }

    /**
     * Toggle root only filter
     */
    function toggleRootOnly() {
        state.rootOnly = !state.rootOnly;
        triggerChange();
    }

    /**
     * Toggle has failures filter
     */
    function toggleHasFailures() {
        state.hasFailures = !state.hasFailures;
        triggerChange();
    }

    /**
     * Set sort field and direction
     */
    function setSort(field, direction = 'DESC') {
        state.sort = [{field, direction}];
        triggerChange();
    }

    /**
     * Add a sort field (for multi-sort)
     */
    function addSort(field, direction = 'DESC') {
        state.sort.push({field, direction});
        triggerChange();
    }

    /**
     * Remove a sort field by index
     */
    function removeSort(index) {
        state.sort.splice(index, 1);
        if (state.sort.length === 0) {
            state.sort = [{field: 'CREATED_AT', direction: 'DESC'}];
        }
        triggerChange();
    }

    /**
     * Toggle sort direction at index
     */
    function toggleSortDirection(index) {
        if (state.sort[index]) {
            state.sort[index].direction = state.sort[index].direction === 'ASC' ? 'DESC' : 'ASC';
            triggerChange();
        }
    }

    /**
     * Reset all filters to default
     */
    function reset() {
        state = {...DEFAULT_STATE, sort: [{field: 'CREATED_AT', direction: 'DESC'}]};
        triggerChange();
    }

    /**
     * Get current state
     */
    function getState() {
        return {...state};
    }

    /**
     * Build GraphQL variables from current state
     */
    function buildVariables(pageSize = 20, cursor = null) {
        const filter = {};

        if (state.search) {
            filter.search = state.search;
        }
        if (state.types.length > 0) {
            filter.types = state.types;
        }
        if (state.statuses.length > 0) {
            filter.statuses = state.statuses;
        }
        if (state.dateRange.from) {
            filter.createdAfter = state.dateRange.from;
        }
        if (state.dateRange.to) {
            filter.createdBefore = state.dateRange.to;
        }
        if (state.percentRange.min > 0) {
            filter.percentMin = state.percentRange.min;
        }
        if (state.percentRange.max < 100) {
            filter.percentMax = state.percentRange.max;
        }
        if (state.rootOnly) {
            filter.rootOnly = true;
        }
        if (state.hasFailures) {
            filter.failedItemsMin = 1;
        }
        if (state.hasChildren !== null) {
            filter.hasChildren = state.hasChildren;
        }

        return {
            first: pageSize,
            after: cursor,
            filter: Object.keys(filter).length > 0 ? filter : null,
            sort: state.sort
        };
    }

    /**
     * Get active filter tags for display
     */
    function getActiveFilterTags() {
        const tags = [];

        if (state.search) {
            tags.push({key: 'search', label: `"${state.search}"`, removable: true});
        }
        state.types.forEach(type => {
            const option = TYPE_OPTIONS.find(t => t.value === type);
            tags.push({key: `type-${type}`, label: option?.label || type, removable: true});
        });
        state.statuses.forEach(status => {
            const option = STATUS_OPTIONS.find(s => s.value === status);
            tags.push({key: `status-${status}`, label: option?.label || status, removable: true});
        });
        if (state.dateRange.from || state.dateRange.to) {
            tags.push({key: 'date', label: 'Date range', removable: true});
        }
        if (state.hasFailures) {
            tags.push({key: 'failures', label: 'Has failures', removable: true});
        }
        if (state.percentRange.min > 0 || state.percentRange.max < 100) {
            tags.push({
                key: 'progress',
                label: `Progress: ${state.percentRange.min}% - ${state.percentRange.max}%`,
                removable: true
            });
        }

        return tags;
    }

    /**
     * Remove a specific filter by key
     */
    function removeFilter(key) {
        if (key === 'search') {
            state.search = '';
        } else if (key.startsWith('type-')) {
            const type = key.replace('type-', '');
            state.types = state.types.filter(t => t !== type);
        } else if (key.startsWith('status-')) {
            const status = key.replace('status-', '');
            state.statuses = state.statuses.filter(s => s !== status);
        } else if (key === 'date') {
            state.dateRange = {from: null, to: null};
        } else if (key === 'failures') {
            state.hasFailures = false;
        } else if (key === 'progress') {
            state.percentRange = {min: 0, max: 100};
        }

        triggerChange();
    }

    /**
     * Trigger the onChange callback
     */
    function triggerChange() {
        if (onChangeCallback) {
            onChangeCallback(getState(), buildVariables());
        }
    }

    // Public API
    return {
        init,
        updateFilter,
        handleSearch,
        setQuickFilter,
        toggleType,
        toggleStatus,
        setDateRange,
        setPercentRange,
        toggleRootOnly,
        toggleHasFailures,
        setSort,
        addSort,
        removeSort,
        toggleSortDirection,
        reset,
        getState,
        buildVariables,
        getActiveFilterTags,
        removeFilter,
        SORT_FIELDS,
        STATUS_OPTIONS,
        TYPE_OPTIONS
    };
})();

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CrawlFilter;
}

