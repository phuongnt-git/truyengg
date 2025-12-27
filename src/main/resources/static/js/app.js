/**
 * Main application controller for TruyenGG SPA
 * Handles routing, initialization, and global functionality
 */

$(document).ready(function () {
    console.log('TruyenGG SPA Application initialized');

    // Apply saved theme
    applySavedTheme();

    // Load categories for navigation menu
    loadCategories();

    // Initialize lazy loading for images
    if (typeof $.fn.lazy !== 'undefined') {
        $('.lazy-image, .lazy').lazy({
            effect: 'fadeIn',
            effectTime: 300,
            threshold: 0,
            onError: function (element) {
                const placeholder = '/img/placeholder.jpg';
                element.attr('src', placeholder);
            }
        });
    }
});

/**
 * Load categories from API and populate navigation menu
 */
function loadCategories() {
    ApiClient.get('/categories', null, false).done(function (response) {
        if (response.success && response.data && response.data.data && response.data.data.items) {
            const categories = response.data.data.items;
            const $menu = $('#categories-menu');
            $menu.empty();

            let baseId = 37;
            categories.forEach(function (category, index) {
                const id = baseId + index;
                const slug = category.slug || '';
                const name = category.name || '';

                if (slug && name) {
                    const li = `<li><a class="dropdown-item" title="${name}" href="/the-loai?slug=${slug}&id=${id}">${name}</a></li>`;
                    $menu.append(li);
                }
            });
        }
    }).fail(function (xhr) {
        console.error('Failed to load categories:', xhr);
    });
}

/**
 * Router for SPA navigation (hash-based)
 */
const Router = {
    currentRoute: null,

    init: function () {
        // Handle hash changes
        $(window).on('hashchange', function () {
            Router.handleRoute();
        });

        // Handle initial route
        Router.handleRoute();
    },

    handleRoute: function () {
        const hash = window.location.hash.substring(1) || '/';
        Router.currentRoute = hash;

        // Load page content based on route
        // This will be implemented per page
        console.log('Route changed to:', hash);
    },

    navigate: function (path) {
        window.location.hash = path;
    }
};

// Initialize router
Router.init();
