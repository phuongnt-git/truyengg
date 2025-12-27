/**
 * Voler Admin Initialization
 * Handles Feather icons initialization and common admin functionality
 */

(function () {
    'use strict';

    // Initialize Feather icons when DOM is ready
    function initFeatherIcons() {
        if (typeof feather !== 'undefined') {
            feather.replace();
        } else {
            // Retry after a short delay if feather is not loaded yet
            setTimeout(initFeatherIcons, 100);
        }
    }

    // Initialize Perfect Scrollbar for sidebar if available
    function initPerfectScrollbar() {
        if (typeof PerfectScrollbar !== 'undefined') {
            const sidebar = document.querySelector('#sidebar .sidebar-menu');
            if (sidebar) {
                new PerfectScrollbar(sidebar);
            }
        }
    }

    // Initialize sidebar active state
    function initSidebarActiveState() {
        const currentPath = window.location.pathname;

        // Remove active class from all sidebar links
        document.querySelectorAll('.sidebar-link').forEach(function (link) {
            link.classList.remove('active');
        });

        // Map URL paths to page identifiers
        const pathMap = {
            '/admin': 'dashboard',
            '/admin/dashboard': 'dashboard',
            '/admin/users': 'users',
            '/admin/stories': 'stories',
            '/admin/comics': 'comics',
            '/admin/chapters': 'chapters',
            '/admin/duplicates': 'duplicates',
            '/admin/images': 'images',
            '/admin/backup': 'backup',
            '/admin/crawl': 'crawl',
            '/admin/category-crawl': 'category-crawl',
            '/admin/jobs': 'jobs',
            '/admin/reports': 'reports',
            '/admin/settings': 'settings'
        };

        // Find matching page identifier
        let activePage = null;
        for (const [path, page] of Object.entries(pathMap)) {
            if (currentPath === path || currentPath.startsWith(path + '/')) {
                // Special handling for crawl to avoid matching category-crawl or jobs
                if (path === '/admin/crawl') {
                    if (!currentPath.includes('/admin/category-crawl') &&
                        !currentPath.includes('/admin/jobs') &&
                        !currentPath.includes('/admin/crawl-job-detail')) {
                        activePage = page;
                        break;
                    }
                } else {
                    activePage = page;
                    break;
                }
            }
        }

        // Set active class on matching link
        if (activePage) {
            const activeLink = document.querySelector('.sidebar-link[data-page="' + activePage + '"]');
            if (activeLink) {
                activeLink.classList.add('active');
            }
        }
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            initFeatherIcons();
            initPerfectScrollbar();
            initSidebarActiveState();
        });
    } else {
        initFeatherIcons();
        initPerfectScrollbar();
        initSidebarActiveState();
    }

    // Re-initialize Feather icons after dynamic content updates
    if (typeof MutationObserver !== 'undefined') {
        const observer = new MutationObserver(function (mutations) {
            let shouldReplace = false;
            mutations.forEach(function (mutation) {
                if (mutation.addedNodes.length > 0) {
                    shouldReplace = true;
                }
            });
            if (shouldReplace && typeof feather !== 'undefined') {
                feather.replace();
            }
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }
})();

