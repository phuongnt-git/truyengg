/**
 * View Modal Module - Handles the view detail modal
 */

let viewModal = null;
let currentSettingId = null;
let currentSettingData = null;

/**
 * Initialize the view modal
 */
export function initViewModal() {
    const modalElement = document.getElementById('viewModal');
    if (modalElement) {
        viewModal = new bootstrap.Modal(modalElement);
    }

    // Setup edit button click
    const editBtn = document.getElementById('viewEditBtn');
    if (editBtn) {
        editBtn.addEventListener('click', () => {
            if (currentSettingId) {
                viewModal.hide();
                window.settingManager.editSetting(currentSettingId);
            }
        });
    }
}

/**
 * View a setting by ID
 */
export async function viewSetting(settingId) {
    currentSettingId = settingId;

    try {
        const response = await ApiClient.get(`/admin/settings/${settingId}`, null, true);

        if (response.success) {
            currentSettingData = response.data;
            populateViewModal(response.data);
            viewModal.show();
        } else {
            showToast('Failed to load setting details', false);
        }
    } catch (error) {
        console.error('Error loading setting:', error);
        showToast('Error loading setting details', false);
    }
}

/**
 * Populate the view modal with setting data
 */
function populateViewModal(setting) {
    // Basic info
    document.getElementById('viewFullKey').textContent = setting.fullKey || '-';

    // Category breadcrumb
    const categoryEl = document.getElementById('viewCategory');
    if (setting.category) {
        const pathParts = setting.category.path.split('.');
        categoryEl.innerHTML = pathParts.map((part, index) => {
            const isLast = index === pathParts.length - 1;
            return `<span>${escapeHtml(part)}</span>${!isLast ? '<span class="separator">/</span>' : ''}`;
        }).join('');
    } else {
        categoryEl.textContent = '-';
    }

    // Type badge
    const typeEl = document.getElementById('viewType');
    typeEl.textContent = setting.valueType;
    typeEl.className = `badge badge-type badge-type-${setting.valueType.toLowerCase()}`;

    // Value (use maskedValue for display)
    document.getElementById('viewValue').textContent = setting.maskedValue || '-';

    // Default value
    document.getElementById('viewDefault').textContent = setting.defaultValue || '(none)';

    // Constraints
    const constraintsSection = document.getElementById('viewConstraintsSection');
    const constraintsEl = document.getElementById('viewConstraints');

    if (setting.constraints && Object.keys(setting.constraints).length > 0) {
        constraintsSection.style.display = 'block';
        constraintsEl.innerHTML = renderConstraints(setting.constraints, setting.valueType);
    } else {
        constraintsSection.style.display = 'none';
    }

    // Description
    const descEl = document.getElementById('viewDescription');
    if (setting.description) {
        // Support multi-line description
        descEl.innerHTML = escapeHtml(setting.description).replace(/\n/g, '<br>');
    } else {
        descEl.textContent = 'No description';
    }

    // Status indicators
    updateStatusIndicator('viewRequired', 'required', setting.required);
    updateStatusIndicator('viewSensitive', 'sensitive', setting.sensitive);
    updateStatusIndicator('viewReadonly', 'readonly', setting.readonly);

    // Audit info
    document.getElementById('viewCreatedAt').textContent = formatDateTime(setting.createdAt);
    document.getElementById('viewUpdatedAt').textContent = formatDateTime(setting.updatedAt);

    // Edit button visibility
    const editBtn = document.getElementById('viewEditBtn');
    if (editBtn) {
        editBtn.style.display = setting.readonly ? 'none' : 'inline-block';
    }

    // Reinitialize feather icons
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Render constraints as HTML
 */
function renderConstraints(constraints, valueType) {
    const items = [];

    if (constraints.min !== undefined) {
        const unit = constraints.unit ? ` ${constraints.unit}` : '';
        items.push(`<div class="constraint-item"><i data-feather="minus-circle" class="constraint-icon"></i> Min: ${constraints.min}${unit}</div>`);
    }

    if (constraints.max !== undefined) {
        const unit = constraints.unit ? ` ${constraints.unit}` : '';
        items.push(`<div class="constraint-item"><i data-feather="plus-circle" class="constraint-icon"></i> Max: ${constraints.max}${unit}</div>`);
    }

    if (constraints.minLength !== undefined) {
        items.push(`<div class="constraint-item"><i data-feather="hash" class="constraint-icon"></i> Min Length: ${constraints.minLength} characters</div>`);
    }

    if (constraints.maxLength !== undefined) {
        items.push(`<div class="constraint-item"><i data-feather="hash" class="constraint-icon"></i> Max Length: ${constraints.maxLength} characters</div>`);
    }

    if (constraints.allowedValues && Array.isArray(constraints.allowedValues)) {
        items.push(`<div class="constraint-item"><i data-feather="list" class="constraint-icon"></i> Allowed: ${constraints.allowedValues.join(', ')}</div>`);
    }

    if (constraints.pattern) {
        items.push(`<div class="constraint-item"><i data-feather="code" class="constraint-icon"></i> Pattern: ${escapeHtml(constraints.pattern)}</div>`);
    }

    if (constraints.unit) {
        items.push(`<div class="constraint-item"><i data-feather="info" class="constraint-icon"></i> Unit: ${constraints.unit}</div>`);
    }

    return items.length > 0 ? items.join('') : '<p class="text-muted mb-0">No constraints defined</p>';
}

/**
 * Update status indicator
 */
function updateStatusIndicator(elementId, className, isActive) {
    const el = document.getElementById(elementId);
    if (el) {
        el.classList.toggle(className, isActive);
        el.style.opacity = isActive ? '1' : '0.4';
    }
}

/**
 * Format date time
 */
function formatDateTime(dateString) {
    if (!dateString) return '-';
    try {
        const date = new Date(dateString);
        return date.toLocaleString('vi-VN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
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

/**
 * Show toast notification
 */
function showToast(message, isSuccess) {
    // Use existing toast function if available, otherwise use alert
    if (typeof window.showToast === 'function') {
        window.showToast(message, isSuccess);
    } else {
        alert(message);
    }
}

/**
 * Get current setting data
 */
export function getCurrentSettingData() {
    return currentSettingData;
}

