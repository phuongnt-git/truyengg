/**
 * Settings Table Module - Handles the settings data table with sort/filter/pagination
 */

let currentPage = 0;
let pageSize = 20;
let sortBy = 'fullKey';
let sortDir = 'asc';
let currentCategoryId = null;
let searchQuery = '';
let typeFilter = '';

/**
 * Initialize the settings table
 */
export function initSettingsTable() {
    setupTableHeaders();
    setupFilters();
    setupPagination();
    loadSettings();
}

/**
 * Setup table header click handlers for sorting
 */
function setupTableHeaders() {
    document.querySelectorAll('.settings-table th[data-sort]').forEach(th => {
        th.addEventListener('click', () => {
            const field = th.dataset.sort;

            if (sortBy === field) {
                sortDir = sortDir === 'asc' ? 'desc' : 'asc';
            } else {
                sortBy = field;
                sortDir = 'asc';
            }

            updateSortIndicators();
            loadSettings();
        });
    });
}

/**
 * Update sort indicators in table headers
 */
function updateSortIndicators() {
    document.querySelectorAll('.settings-table th[data-sort]').forEach(th => {
        const icon = th.querySelector('.sort-icon');
        if (th.dataset.sort === sortBy) {
            th.classList.add('sorted');
            if (icon) {
                icon.setAttribute('data-feather', sortDir === 'asc' ? 'chevron-up' : 'chevron-down');
            }
        } else {
            th.classList.remove('sorted');
            if (icon) {
                icon.setAttribute('data-feather', 'chevron-up');
            }
        }
    });

    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Setup filter controls
 */
function setupFilters() {
    // Search input
    const searchInput = document.getElementById('settingSearch');
    if (searchInput) {
        let debounceTimer;
        searchInput.addEventListener('input', (e) => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                searchQuery = e.target.value;
                currentPage = 0;
                loadSettings();
            }, 300);
        });
    }

    // Type filter
    const typeFilterSelect = document.getElementById('typeFilter');
    if (typeFilterSelect) {
        typeFilterSelect.addEventListener('change', (e) => {
            typeFilter = e.target.value;
            currentPage = 0;
            loadSettings();
        });
    }
}

/**
 * Setup pagination controls
 */
function setupPagination() {
    const pageSizeSelect = document.getElementById('pageSize');
    if (pageSizeSelect) {
        pageSizeSelect.addEventListener('change', (e) => {
            pageSize = parseInt(e.target.value);
            currentPage = 0;
            loadSettings();
        });
    }
}

/**
 * Load settings from API
 */
export async function loadSettings() {
    const tableBody = document.getElementById('settingsTableBody');

    tableBody.innerHTML = `
    <tr>
      <td colspan="7" class="text-center p-4 text-muted">
        <div class="spinner-border spinner-border-sm" role="status"></div>
        <div class="mt-2">Loading settings...</div>
      </td>
    </tr>
  `;

    try {
        let endpoint;
        const params = new URLSearchParams({
            page: currentPage,
            size: pageSize,
            sortBy: sortBy,
            sortDir: sortDir
        });

        if (searchQuery) {
            // Use search endpoint with server-side pagination
            const searchParams = new URLSearchParams({
                query: searchQuery,
                page: currentPage,
                size: pageSize
            });
            endpoint = `/admin/settings/search?${searchParams}`;
        } else {
            // Use list endpoint with optional categoryId
            if (currentCategoryId) {
                params.append('categoryId', currentCategoryId);
            }
            endpoint = `/admin/settings/list?${params}`;
        }

        const response = await ApiClient.get(endpoint, null, true);

        if (response.success) {
            let content = response.data.content;

            // Filter by type if needed (client-side)
            if (typeFilter) {
                content = content.filter(s => s.valueType === typeFilter);
            }

            renderSettingsTable(content);
            renderPagination(response.data.totalElements, currentPage, pageSize);
        } else {
            showTableError('Failed to load settings');
        }
    } catch (error) {
        console.error('Error loading settings:', error);
        showTableError('Error loading settings');
    }
}

/**
 * Render settings table
 */
function renderSettingsTable(settings) {
    const tableBody = document.getElementById('settingsTableBody');

    if (!settings || settings.length === 0) {
        tableBody.innerHTML = `
      <tr>
        <td colspan="7" class="text-center p-4 text-muted">
          <i data-feather="inbox" style="width: 48px; height: 48px; opacity: 0.5;"></i>
          <p class="mt-2">No settings found</p>
        </td>
      </tr>
    `;
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
        return;
    }

    tableBody.innerHTML = settings.map(setting => `
    <tr>
      <td class="key-cell">${escapeHtml(setting.key)}</td>
      <td class="key-cell">${escapeHtml(setting.fullKey)}</td>
      <td class="value-cell" title="${escapeHtml(setting.maskedValue || '')}">${escapeHtml(setting.maskedValue || '-')}</td>
      <td>
        <span class="badge badge-type badge-type-${setting.valueType.toLowerCase()}">${setting.valueType}</span>
      </td>
      <td>
        ${renderStatusIndicators(setting)}
      </td>
      <td>${formatDate(setting.updatedAt)}</td>
      <td>
        <div class="btn-group btn-group-sm">
          <button class="btn btn-outline-primary" onclick="window.settingManager.viewSetting(${setting.id})" title="View">
            <i data-feather="eye"></i>
          </button>
          ${!setting.readonly ? `
            <button class="btn btn-outline-secondary" onclick="window.settingManager.editSetting(${setting.id})" title="Edit">
              <i data-feather="edit-2"></i>
            </button>
          ` : ''}
        </div>
      </td>
    </tr>
  `).join('');

    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Render status indicators using CSS classes from setting.css
 */
function renderStatusIndicators(setting) {
    const indicators = [];

    if (setting.required) {
        indicators.push('<span class="small me-2"><span class="status-dot status-dot-required me-1"></span>Req</span>');
    }
    if (setting.sensitive) {
        indicators.push('<span class="small me-2"><span class="status-dot status-dot-sensitive me-1"></span>Sens</span>');
    }
    if (setting.readonly) {
        indicators.push('<span class="small"><span class="status-dot status-dot-readonly me-1"></span>RO</span>');
    }

    return indicators.length > 0 ? indicators.join('') : '<span class="text-muted">-</span>';
}

/**
 * Render pagination
 */
function renderPagination(totalItems, currentPage, pageSize) {
    const totalPages = Math.ceil(totalItems / pageSize);
    const start = currentPage * pageSize + 1;
    const end = Math.min((currentPage + 1) * pageSize, totalItems);

    // Update info
    document.getElementById('pageStart').textContent = totalItems > 0 ? start : 0;
    document.getElementById('pageEnd').textContent = end;
    document.getElementById('totalItems').textContent = totalItems;

    // Update pagination buttons
    const pagination = document.getElementById('pagination');
    let html = '';

    // Previous button
    html += `
    <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
      <a class="page-link" href="#" onclick="window.settingManager.goToPage(${currentPage - 1}); return false;">&laquo;</a>
    </li>
  `;

    // Page numbers
    const maxVisiblePages = 5;
    let startPage = Math.max(0, currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(totalPages - 1, startPage + maxVisiblePages - 1);

    if (endPage - startPage < maxVisiblePages - 1) {
        startPage = Math.max(0, endPage - maxVisiblePages + 1);
    }

    if (startPage > 0) {
        html += `<li class="page-item"><a class="page-link" href="#" onclick="window.settingManager.goToPage(0); return false;">1</a></li>`;
        if (startPage > 1) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `
      <li class="page-item ${i === currentPage ? 'active' : ''}">
        <a class="page-link" href="#" onclick="window.settingManager.goToPage(${i}); return false;">${i + 1}</a>
      </li>
    `;
    }

    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
        html += `<li class="page-item"><a class="page-link" href="#" onclick="window.settingManager.goToPage(${totalPages - 1}); return false;">${totalPages}</a></li>`;
    }

    // Next button
    html += `
    <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
      <a class="page-link" href="#" onclick="window.settingManager.goToPage(${currentPage + 1}); return false;">&raquo;</a>
    </li>
  `;

    pagination.innerHTML = html;
}

/**
 * Go to specific page
 */
export function goToPage(page) {
    if (page < 0) return;
    currentPage = page;
    loadSettings();
}

/**
 * Load settings by category
 */
export function loadSettingsByCategory(categoryId) {
    currentCategoryId = categoryId;
    currentPage = 0;
    loadSettings();
}

/**
 * Refresh settings
 */
export function refreshSettings() {
    loadSettings();
}

/**
 * Show table error
 */
function showTableError(message) {
    const tableBody = document.getElementById('settingsTableBody');
    tableBody.innerHTML = `
    <tr>
      <td colspan="7" class="text-center p-4 text-danger">
        <i data-feather="alert-circle"></i>
        <p class="mt-2">${escapeHtml(message)}</p>
        <button class="btn btn-sm btn-outline-primary" onclick="window.settingManager.refreshSettings()">
          Retry
        </button>
      </td>
    </tr>
  `;
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Format date
 */
function formatDate(dateString) {
    if (!dateString) return '-';
    try {
        const date = new Date(dateString);
        return date.toLocaleDateString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return dateString;
    }
}

/**
 * Escape HTML
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

