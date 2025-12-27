/**
 * Setting Manager - Main Entry Point
 * Initializes all modules for the Setting Management UI
 */

import {initCategoryTree, refreshCategories, selectCategory, toggleCategory} from './category-tree.js';

import {goToPage, initSettingsTable, loadSettingsByCategory, refreshSettings} from './settings-table.js';

import {initViewModal, viewSetting} from './view-modal.js';

import {editSetting, initEditModal} from './edit-modal.js';

/**
 * Setting Manager Global Object
 */
const settingManager = {
    // Category tree methods
    toggleCategory,
    selectCategory,
    refreshCategories,

    // Settings table methods
    goToPage,
    loadSettingsByCategory,
    refreshSettings,

    // Modal methods
    viewSetting,
    editSetting
};

// Expose to window for onclick handlers
window.settingManager = settingManager;

// Also expose refresh function globally
window.refreshSettings = refreshSettings;

/**
 * Initialize the Setting Manager
 */
async function init() {
    // Check authentication
    if (!ApiClient.getToken()) {
        const currentPath = window.location.pathname;
        window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
        return;
    }

    console.log('Initializing Setting Manager...');

    try {
        // Initialize all modules
        await initCategoryTree();
        initSettingsTable();
        initViewModal();
        initEditModal();

        // Setup global event listeners
        setupGlobalListeners();

        console.log('Setting Manager initialized successfully');
    } catch (error) {
        console.error('Failed to initialize Setting Manager:', error);
        showToast('Failed to initialize Setting Manager', false);
    }
}

/**
 * Setup global event listeners
 */
function setupGlobalListeners() {
    // Keyboard shortcuts
    document.addEventListener('keydown', (e) => {
        // ESC to close modals
        if (e.key === 'Escape') {
            const viewModal = bootstrap.Modal.getInstance(document.getElementById('viewModal'));
            const editModal = bootstrap.Modal.getInstance(document.getElementById('editModal'));

            if (viewModal) viewModal.hide();
            if (editModal) editModal.hide();
        }

        // Ctrl+F to focus search
        if (e.ctrlKey && e.key === 'f') {
            e.preventDefault();
            const searchInput = document.getElementById('settingSearch');
            if (searchInput) {
                searchInput.focus();
                searchInput.select();
            }
        }
    });

    // Mobile sidebar toggle
    const mobileSidebarToggle = document.querySelector('.mobile-sidebar-toggle');
    if (mobileSidebarToggle) {
        mobileSidebarToggle.addEventListener('click', () => {
            const sidebar = document.getElementById('settingSidebar');
            sidebar.classList.toggle('open');
        });
    }
}

/**
 * Show toast notification using Toastify library
 */
function showToast(message, isSuccess) {
    if (typeof Toastify !== 'undefined') {
        Toastify({
            text: message,
            duration: 3000,
            gravity: 'top',
            position: 'right',
            backgroundColor: isSuccess ? '#28a745' : '#dc3545',
            stopOnFocus: true,
            close: true
        }).showToast();
    } else {
        // Fallback if Toastify not loaded
        console.log(`[Toast ${isSuccess ? 'success' : 'error'}] ${message}`);
        alert(message);
    }
}

// Make showToast available globally
window.showToast = showToast;

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init().then(() => {
    });
}

