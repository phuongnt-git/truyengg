/**
 * Cache Management Module
 * Handles cache statistics display, filtering, and clear operations
 */
(function () {
    'use strict';

    // State
    var cacheData = [];
    var categories = [];
    var autoRefreshInterval = null;
    var isAutoRefreshEnabled = false;
    var currentCacheToDelete = null;
    var currentCategoryToDelete = null;

    // Bootstrap modal instances
    var clearCacheModal = null;
    var clearCategoryModal = null;
    var clearAllModal = null;

    /**
     * Initialize on document ready
     */
    $(document).ready(function () {
        if (!ApiClient.getToken()) {
            var currentPath = window.location.pathname;
            window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
            return;
        }

        initModals();
        initEventListeners();
        loadCategories();
        loadCacheStats();
    });

    /**
     * Initialize Bootstrap modals
     */
    function initModals() {
        clearCacheModal = new bootstrap.Modal(document.getElementById('clearCacheModal'));
        clearCategoryModal = new bootstrap.Modal(document.getElementById('clearCategoryModal'));
        clearAllModal = new bootstrap.Modal(document.getElementById('clearAllModal'));
    }

    /**
     * Initialize event listeners
     */
    function initEventListeners() {
        // Search input with debounce
        var searchTimeout = null;
        $('#searchInput').on('input', function () {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(function () {
                filterAndRender();
            }, 300);
        });

        // Category filter
        $('#categoryFilter').on('change', function () {
            filterAndRender();
        });

        // Refresh button
        $('#btnRefresh').on('click', function () {
            loadCacheStats();
        });

        // Auto refresh toggle
        $('#btnAutoRefresh').on('click', function () {
            toggleAutoRefresh();
        });

        // Clear all button
        $('#btnClearAll').on('click', function () {
            $('#clearAllConfirmInput').val('');
            $('#confirmClearAll').prop('disabled', true);
            clearAllModal.show();
        });

        // Evict all confirm input validation
        $('#clearAllConfirmInput').on('input', function () {
            var value = $(this).val().trim();
            $('#confirmClearAll').prop('disabled', value !== 'EVICT ALL');
        });

        // Confirm clear single cache
        $('#confirmClearCache').on('click', function () {
            if (currentCacheToDelete) {
                clearCache(currentCacheToDelete);
            }
        });

        // Confirm clear category
        $('#confirmClearCategory').on('click', function () {
            if (currentCategoryToDelete) {
                clearCategory(currentCategoryToDelete);
            }
        });

        // Confirm clear all
        $('#confirmClearAll').on('click', function () {
            clearAllCaches();
        });
    }

    /**
     * Load cache categories
     */
    function loadCategories() {
        ApiClient.get('/admin/cache/categories').done(function (response) {
            if (response.success && response.data) {
                categories = response.data;
                renderCategoryFilter();
            }
        });
    }

    /**
     * Render category filter dropdown
     */
    function renderCategoryFilter() {
        var $select = $('#categoryFilter');
        $select.find('option:not(:first)').remove();

        categories.forEach(function (category) {
            $select.append($('<option>').val(category).text(category));
        });
    }

    /**
     * Load cache statistics
     */
    function loadCacheStats() {
        $('#loadingState').removeClass('d-none');
        $('#cacheGroups').empty();
        $('#emptyState').addClass('d-none');

        ApiClient.get('/admin/cache/stats').done(function (response) {
            $('#loadingState').addClass('d-none');

            if (response.success && response.data) {
                cacheData = response.data;
                renderStatsCards();
                filterAndRender();
            } else {
                $('#emptyState').removeClass('d-none');
            }
        }).fail(function (xhr) {
            $('#loadingState').addClass('d-none');
            showError('Failed to load cache statistics');
        });
    }

    /**
     * Render stats cards
     */
    function renderStatsCards() {
        var totalCaches = cacheData.length;
        var totalHits = 0;
        var totalMisses = 0;
        var totalSize = 0;
        var totalEvictions = 0;

        cacheData.forEach(function (cache) {
            totalHits += cache.hitCount || 0;
            totalMisses += cache.missCount || 0;
            totalSize += cache.currentSize || 0;
            totalEvictions += cache.evictionCount || 0;
        });

        var hitRate = (totalHits + totalMisses) > 0
            ? ((totalHits / (totalHits + totalMisses)) * 100).toFixed(1)
            : 0;

        $('#stat-total-caches').text(totalCaches);
        $('#stat-hit-rate').text(hitRate + '%')
            .removeClass('text-success text-warning text-danger')
            .addClass(getHitRateClass(hitRate));
        $('#stat-total-size').text(formatNumber(totalSize));
        $('#stat-evictions').text(formatNumber(totalEvictions));
    }

    /**
     * Filter data and render table
     */
    function filterAndRender() {
        var searchTerm = $('#searchInput').val().toLowerCase().trim();
        var selectedCategory = $('#categoryFilter').val();

        var filteredData = cacheData.filter(function (cache) {
            var matchesSearch = !searchTerm ||
                cache.cacheName.toLowerCase().includes(searchTerm) ||
                cache.displayName.toLowerCase().includes(searchTerm);

            var matchesCategory = !selectedCategory || cache.category === selectedCategory;

            return matchesSearch && matchesCategory;
        });

        renderCacheTable(filteredData);
    }

    /**
     * Render cache table grouped by category
     */
    function renderCacheTable(data) {
        var $container = $('#cacheGroups');
        $container.empty();

        if (data.length === 0) {
            $('#emptyState').removeClass('d-none');
            // Re-render feather icons for the empty state
            if (typeof feather !== 'undefined') {
                feather.replace();
            }
            return;
        }

        $('#emptyState').addClass('d-none');

        // Group by category
        var grouped = {};
        data.forEach(function (cache) {
            var category = cache.category || 'Other';
            if (!grouped[category]) {
                grouped[category] = [];
            }
            grouped[category].push(cache);
        });

        // Sort categories
        var sortedCategories = Object.keys(grouped).sort();

        // Render each category
        sortedCategories.forEach(function (category) {
            var caches = grouped[category];
            var $card = createCategoryCard(category, caches);
            $container.append($card);
        });

        // Re-render feather icons
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
    }

    /**
     * Create category card with cache table
     */
    function createCategoryCard(category, caches) {
        var categoryStats = calculateCategoryStats(caches);

        var $card = $('<div class="card mb-3">');

        // Card header
        var $header = $('<div class="card-header d-flex justify-content-between align-items-center">');
        $header.append(
            $('<div>').append(
                $('<h5 class="mb-0">').html(
                    '<i data-feather="folder" class="me-2"></i>' +
                    escapeHtml(category) +
                    ' <span class="badge bg-secondary ms-2">' + caches.length + '</span>'
                ),
                $('<small class="text-muted">').text(
                    'Hit Rate: ' + categoryStats.hitRate + '% | Size: ' + formatNumber(categoryStats.totalSize)
                )
            )
        );

        var $clearBtn = $('<button class="btn btn-outline-warning btn-sm">')
            .html('<i data-feather="trash"></i> Evict Category')
            .on('click', function () {
                showClearCategoryModal(category);
            });
        $header.append($clearBtn);

        $card.append($header);

        // Card body with table
        var $body = $('<div class="card-body p-0">');
        var $table = createCacheTable(caches);
        $body.append($table);
        $card.append($body);

        return $card;
    }

    /**
     * Create cache table
     */
    function createCacheTable(caches) {
        var $tableWrapper = $('<div class="table-responsive">');
        var $table = $('<table class="table table-hover mb-0">');

        // Header
        var $thead = $('<thead class="table-light">');
        $thead.append(
            $('<tr>').append(
                $('<th>').text('Cache Name'),
                $('<th class="text-center">').text('TTL'),
                $('<th class="text-end">').text('Hits'),
                $('<th class="text-end">').text('Misses'),
                $('<th style="width: 150px;">').text('Hit Rate'),
                $('<th class="text-end">').text('Size'),
                $('<th class="text-end">').text('Evictions'),
                $('<th class="text-center" style="width: 80px;">').text('Actions')
            )
        );
        $table.append($thead);

        // Body
        var $tbody = $('<tbody>');
        caches.forEach(function (cache) {
            var hitRate = parseFloat(cache.hitRate) * 100;
            var progressClass = getProgressClass(hitRate);

            var $row = $('<tr>');
            $row.append(
                $('<td>').append(
                    $('<span class="fw-bold">').text(cache.displayName),
                    cache.displayName !== cache.cacheName
                        ? $('<br><small class="text-muted">').text(cache.cacheName)
                        : ''
                ),
                $('<td class="text-center">').append(
                    $('<span>').addClass('badge ' + getTtlBadgeClass(cache.ttl)).text(cache.ttl)
                ),
                $('<td class="text-end text-success fw-bold">').text(formatNumber(cache.hitCount)),
                $('<td class="text-end text-danger">').text(formatNumber(cache.missCount)),
                $('<td>').append(
                    $('<div class="d-flex align-items-center">').append(
                        $('<div class="progress flex-grow-1 me-2" style="height: 8px;">').append(
                            $('<div class="progress-bar">').addClass(progressClass)
                                .css('width', hitRate + '%')
                        ),
                        $('<span class="small">').text(cache.hitRatePercent)
                    )
                ),
                $('<td class="text-end">').append(
                    $('<span class="badge bg-secondary">').text(formatNumber(cache.currentSize))
                ),
                $('<td class="text-end">').append(
                    $('<span class="badge bg-warning text-dark">').text(formatNumber(cache.evictionCount))
                ),
                $('<td class="text-center">').append(
                    $('<button class="btn btn-outline-danger btn-sm" title="Evict Cache">')
                        .html('<i data-feather="trash-2"></i>')
                        .on('click', function () {
                            showClearCacheModal(cache.cacheName);
                        })
                )
            );
            $tbody.append($row);
        });
        $table.append($tbody);

        $tableWrapper.append($table);
        return $tableWrapper;
    }

    /**
     * Calculate category statistics
     */
    function calculateCategoryStats(caches) {
        var totalHits = 0;
        var totalMisses = 0;
        var totalSize = 0;

        caches.forEach(function (cache) {
            totalHits += cache.hitCount || 0;
            totalMisses += cache.missCount || 0;
            totalSize += cache.currentSize || 0;
        });

        var hitRate = (totalHits + totalMisses) > 0
            ? ((totalHits / (totalHits + totalMisses)) * 100).toFixed(1)
            : 0;

        return {
            hitRate: hitRate,
            totalSize: totalSize
        };
    }

    /**
     * Show clear single cache modal
     */
    function showClearCacheModal(cacheName) {
        currentCacheToDelete = cacheName;
        $('#clearCacheName').text(cacheName);
        clearCacheModal.show();
    }

    /**
     * Show clear category modal
     */
    function showClearCategoryModal(category) {
        currentCategoryToDelete = category;
        $('#clearCategoryName').text(category);
        clearCategoryModal.show();
    }

    /**
     * Evict single cache
     */
    function clearCache(cacheName) {
        $('#confirmClearCache').prop('disabled', true).html(
            '<span class="spinner-border spinner-border-sm me-1"></span> Evicting...'
        );

        ApiClient.postWithQuery('/admin/cache/clear/' + encodeURIComponent(cacheName))
            .done(function (response) {
                clearCacheModal.hide();
                if (response.success) {
                    showSuccess('Cache evicted successfully');
                    loadCacheStats();
                } else {
                    showError(response.message || 'Failed to evict cache');
                }
            })
            .fail(function (xhr) {
                clearCacheModal.hide();
                showError('Failed to evict cache: ' + (xhr.responseJSON?.message || 'Unknown error'));
            })
            .always(function () {
                $('#confirmClearCache').prop('disabled', false).html(
                    '<i data-feather="trash-2"></i> Evict Cache'
                );
                if (typeof feather !== 'undefined') {
                    feather.replace();
                }
                currentCacheToDelete = null;
            });
    }

    /**
     * Evict category
     */
    function clearCategory(category) {
        $('#confirmClearCategory').prop('disabled', true).html(
            '<span class="spinner-border spinner-border-sm me-1"></span> Evicting...'
        );

        ApiClient.postWithQuery('/admin/cache/clear-category/' + encodeURIComponent(category))
            .done(function (response) {
                clearCategoryModal.hide();
                if (response.success) {
                    showSuccess('Category evicted successfully');
                    loadCacheStats();
                } else {
                    showError(response.message || 'Failed to evict category');
                }
            })
            .fail(function (xhr) {
                clearCategoryModal.hide();
                showError('Failed to evict category: ' + (xhr.responseJSON?.message || 'Unknown error'));
            })
            .always(function () {
                $('#confirmClearCategory').prop('disabled', false).html(
                    '<i data-feather="trash-2"></i> Evict Category'
                );
                if (typeof feather !== 'undefined') {
                    feather.replace();
                }
                currentCategoryToDelete = null;
            });
    }

    /**
     * Evict all caches
     */
    function clearAllCaches() {
        $('#confirmClearAll').prop('disabled', true).html(
            '<span class="spinner-border spinner-border-sm me-1"></span> Evicting...'
        );

        ApiClient.postWithQuery('/admin/cache/clear-all')
            .done(function (response) {
                clearAllModal.hide();
                if (response.success) {
                    showSuccess('All caches evicted successfully');
                    loadCacheStats();
                } else {
                    showError(response.message || 'Failed to evict all caches');
                }
            })
            .fail(function (xhr) {
                clearAllModal.hide();
                showError('Failed to evict all caches: ' + (xhr.responseJSON?.message || 'Unknown error'));
            })
            .always(function () {
                $('#confirmClearAll').prop('disabled', true).html(
                    '<i data-feather="trash-2"></i> Evict All Caches'
                );
                $('#clearAllConfirmInput').val('');
                if (typeof feather !== 'undefined') {
                    feather.replace();
                }
            });
    }

    /**
     * Toggle auto refresh
     */
    function toggleAutoRefresh() {
        isAutoRefreshEnabled = !isAutoRefreshEnabled;
        var $btn = $('#btnAutoRefresh');

        if (isAutoRefreshEnabled) {
            $btn.removeClass('btn-outline-secondary').addClass('btn-success');
            autoRefreshInterval = setInterval(function () {
                loadCacheStats();
            }, 30000); // 30 seconds
            showSuccess('Auto-refresh enabled (30s)');
        } else {
            $btn.removeClass('btn-success').addClass('btn-outline-secondary');
            if (autoRefreshInterval) {
                clearInterval(autoRefreshInterval);
                autoRefreshInterval = null;
            }
            showSuccess('Auto-refresh disabled');
        }
    }

    /**
     * Format number (1234 -> 1,234 or 1.2K)
     */
    function formatNumber(num) {
        if (num === null || num === undefined) return '0';
        if (num >= 1000000) {
            return (num / 1000000).toFixed(1) + 'M';
        }
        if (num >= 10000) {
            return (num / 1000).toFixed(1) + 'K';
        }
        return num.toLocaleString();
    }

    /**
     * Get progress bar class based on hit rate
     */
    function getProgressClass(rate) {
        if (rate >= 80) return 'bg-success';
        if (rate >= 50) return 'bg-warning';
        return 'bg-danger';
    }

    /**
     * Get text class based on hit rate
     */
    function getHitRateClass(rate) {
        if (rate >= 80) return 'text-success';
        if (rate >= 50) return 'text-warning';
        return 'text-danger';
    }

    /**
     * Get TTL badge class
     */
    function getTtlBadgeClass(ttl) {
        if (!ttl || ttl === 'default') return 'bg-secondary';

        // Parse TTL duration
        var match = ttl.match(/^(\d+)([smhd])$/i);
        if (!match) return 'bg-secondary';

        var value = parseInt(match[1]);
        var unit = match[2].toLowerCase();

        // Convert to seconds
        var seconds = value;
        switch (unit) {
            case 'm':
                seconds = value * 60;
                break;
            case 'h':
                seconds = value * 3600;
                break;
            case 'd':
                seconds = value * 86400;
                break;
        }

        if (seconds <= 60) return 'bg-danger';      // <= 1 minute
        if (seconds <= 300) return 'bg-warning';    // <= 5 minutes
        if (seconds <= 3600) return 'bg-info';      // <= 1 hour
        return 'bg-primary';                         // > 1 hour
    }

    /**
     * Escape HTML
     */
    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Show success notification
     */
    function showSuccess(message) {
        if (typeof Toastify !== 'undefined') {
            Toastify({
                text: message,
                duration: 3000,
                gravity: 'top',
                position: 'right',
                backgroundColor: '#28a745'
            }).showToast();
        } else {
            console.log('Success:', message);
        }
    }

    /**
     * Show error notification
     */
    function showError(message) {
        if (typeof Toastify !== 'undefined') {
            Toastify({
                text: message,
                duration: 5000,
                gravity: 'top',
                position: 'right',
                backgroundColor: '#dc3545'
            }).showToast();
        } else {
            console.error('Error:', message);
            alert(message);
        }
    }

})();

