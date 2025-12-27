/**
 * Category Tree Module - Handles the collapsible category tree sidebar
 * Uses Bootstrap/Voler classes for consistent styling
 */

let categoryData = [];
let selectedCategoryId = null;
let totalSettings = 0;
let totalCategories = 0;

/**
 * Initialize the category tree
 */
export async function initCategoryTree() {
    await loadCategories();
    setupCategorySearch();
}

/**
 * Load categories from API (includes stats)
 */
async function loadCategories() {
    const treeContainer = document.getElementById('categoryTree');

    try {
        const response = await ApiClient.get('/admin/settings/categories', null, true);

        if (response.success) {
            // Response contains: categories, totalCategories, totalSettings
            categoryData = response.data.categories;
            totalCategories = response.data.totalCategories;
            totalSettings = response.data.totalSettings;

            renderCategoryTree(categoryData);
            updateFooterStats();
        } else {
            showError(treeContainer, 'Failed to load categories');
        }
    } catch (error) {
        console.error('Error loading categories:', error);
        showError(treeContainer, 'Error loading categories');
    }
}

/**
 * Render the category tree using Bootstrap list-group
 */
function renderCategoryTree(categories, filter = '') {
    const treeContainer = document.getElementById('categoryTree');

    if (!categories || categories.length === 0) {
        treeContainer.innerHTML = `
      <div class="text-center p-4 text-muted">
        <p class="mb-0">No categories found</p>
      </div>
    `;
        return;
    }

    const filteredCategories = filterCategories(categories, filter);

    if (filteredCategories.length === 0) {
        treeContainer.innerHTML = `
      <div class="text-center p-4 text-muted">
        <p class="mb-0">No matching categories</p>
      </div>
    `;
        return;
    }

    // Build tree HTML using Bootstrap classes
    let html = `<div class="list-group list-group-flush">`;

    // Add "All Settings" option
    html += `
    <a href="#" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center ${selectedCategoryId === null ? 'active' : ''}" 
       data-category-id="" 
       onclick="window.settingManager.selectCategory(null); return false;">
      <span class="fw-semibold">
        <i data-feather="grid" class="me-2" style="width: 16px; height: 16px;"></i>All Settings
      </span>
      <span class="badge bg-primary rounded-pill">${totalSettings}</span>
    </a>
  `;

    html += filteredCategories.map(cat => renderCategoryNode(cat, 0)).join('');
    html += `</div>`;

    treeContainer.innerHTML = html;

    // Reinitialize feather icons
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Render a single category node with its children
 */
function renderCategoryNode(category, level) {
    const hasChildren = category.children && category.children.length > 0;
    const totalCount = getTotalSettingCount(category);
    const isExpanded = level < 1;
    const levelClass = level > 0 ? `level-${level}` : '';

    let html = '';

    if (hasChildren) {
        // Parent with children - expandable
        html += `
      <div class="tree-node">
        <a href="#" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center ${levelClass}"
           onclick="window.settingManager.toggleCategory(this, '${category.id}'); return false;">
          <span>
            <i data-feather="chevron-down" class="tree-toggle ${isExpanded ? '' : 'collapsed'} me-1" style="width: 16px; height: 16px;"></i>
            <i data-feather="folder" class="me-1 text-warning" style="width: 16px; height: 16px;"></i>
            ${escapeHtml(category.name)}
          </span>
          <span class="badge bg-secondary rounded-pill">${totalCount}</span>
        </a>
        <div class="tree-children ${isExpanded ? 'show' : ''}" id="children-${category.id}">
    `;

        // Direct settings link if category has its own settings
        if (category.settingCount > 0) {
            html += `
        <a href="#" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center level-${level + 1} ${selectedCategoryId === category.id ? 'active' : ''}"
           data-category-id="${category.id}"
           onclick="window.settingManager.selectCategory(${category.id}); return false;">
          <span>
            <i data-feather="file-text" class="me-1 text-muted" style="width: 14px; height: 14px;"></i>
            ${escapeHtml(category.name)} (direct)
          </span>
          <span class="badge bg-light text-dark rounded-pill">${category.settingCount}</span>
        </a>
      `;
        }

        // Render children
        html += category.children.map(child => renderCategoryNode(child, level + 1)).join('');
        html += `</div></div>`;
    } else {
        // Leaf node - no children
        html += `
      <a href="#" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center ${levelClass} ${selectedCategoryId === category.id ? 'active' : ''}"
         data-category-id="${category.id}"
         onclick="window.settingManager.selectCategory(${category.id}); return false;">
        <span>
          <i data-feather="settings" class="me-1 text-muted" style="width: 14px; height: 14px;"></i>
          ${escapeHtml(category.name)}
        </span>
        <span class="badge bg-light text-dark rounded-pill">${category.settingCount}</span>
      </a>
    `;
    }

    return html;
}

/**
 * Get total setting count including children
 */
function getTotalSettingCount(category) {
    let count = category.settingCount || 0;
    if (category.children) {
        for (const child of category.children) {
            count += getTotalSettingCount(child);
        }
    }
    return count;
}

/**
 * Filter categories by search term
 */
function filterCategories(categories, filter) {
    if (!filter) return categories;

    const lowerFilter = filter.toLowerCase();

    return categories.filter(cat => {
        const nameMatch = cat.name.toLowerCase().includes(lowerFilter);
        const codeMatch = cat.code.toLowerCase().includes(lowerFilter);
        const childMatch = cat.children && filterCategories(cat.children, filter).length > 0;

        return nameMatch || codeMatch || childMatch;
    }).map(cat => ({
        ...cat,
        children: cat.children ? filterCategories(cat.children, filter) : []
    }));
}

/**
 * Toggle category expansion
 */
export function toggleCategory(element, categoryId) {
    const toggle = element.querySelector('.tree-toggle');
    const children = document.getElementById(`children-${categoryId}`);

    if (toggle && children) {
        const isCollapsed = toggle.classList.contains('collapsed');

        if (isCollapsed) {
            toggle.classList.remove('collapsed');
            children.classList.add('show');
        } else {
            toggle.classList.add('collapsed');
            children.classList.remove('show');
        }
    }
}

/**
 * Select a category
 */
export function selectCategory(categoryId) {
    selectedCategoryId = categoryId;

    // Update visual selection
    document.querySelectorAll('.category-tree .list-group-item').forEach(item => {
        item.classList.remove('active');
        const itemCatId = item.dataset.categoryId;
        if (itemCatId === String(categoryId || '')) {
            item.classList.add('active');
        }
    });

    // Update title
    const titleEl = document.getElementById('currentCategoryTitle');
    if (titleEl) {
        if (categoryId === null) {
            titleEl.textContent = 'All Settings';
        } else {
            const cat = findCategory(categoryData, categoryId);
            titleEl.textContent = cat ? cat.name : 'Settings';
        }
    }

    // Trigger settings reload
    if (window.settingManager && window.settingManager.loadSettingsByCategory) {
        window.settingManager.loadSettingsByCategory(categoryId);
    }
}

/**
 * Find category by ID
 */
function findCategory(categories, id) {
    for (const cat of categories) {
        if (cat.id === id) return cat;
        if (cat.children) {
            const found = findCategory(cat.children, id);
            if (found) return found;
        }
    }
    return null;
}

/**
 * Setup category search
 */
function setupCategorySearch() {
    const searchInput = document.getElementById('categorySearch');
    if (!searchInput) return;

    let debounceTimer;
    searchInput.addEventListener('input', (e) => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            renderCategoryTree(categoryData, e.target.value);
        }, 300);
    });
}

/**
 * Update footer statistics using backend data
 */
function updateFooterStats() {
    const footerCategories = document.getElementById('footerCategories');
    const footerSettings = document.getElementById('footerSettings');

    if (footerCategories) footerCategories.textContent = totalCategories;
    if (footerSettings) footerSettings.textContent = totalSettings;
}

/**
 * Show error message
 */
function showError(container, message) {
    container.innerHTML = `
    <div class="text-center p-4">
      <i data-feather="alert-circle" class="text-danger mb-2"></i>
      <p class="text-danger mb-2">${escapeHtml(message)}</p>
      <button class="btn btn-sm btn-outline-primary" onclick="window.settingManager.refreshCategories()">
        <i data-feather="refresh-cw" class="me-1"></i>Retry
      </button>
    </div>
  `;
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Refresh categories
 */
export async function refreshCategories() {
    await loadCategories();
}

/**
 * Get selected category ID
 */
export function getSelectedCategoryId() {
    return selectedCategoryId;
}
