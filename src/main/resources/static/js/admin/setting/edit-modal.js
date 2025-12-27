/**
 * Edit Modal Module - Handles the edit modal with constraint validation
 */

let editModal = null;
let currentEditSetting = null;
let isPasswordVisible = false;

/**
 * Initialize the edit modal
 */
export function initEditModal() {
    const modalElement = document.getElementById('editModal');
    if (modalElement) {
        editModal = new bootstrap.Modal(modalElement);
    }

    // Setup save button
    const saveBtn = document.getElementById('saveSettingBtn');
    if (saveBtn) {
        saveBtn.addEventListener('click', saveSetting);
    }

    // Setup value input real-time validation
    const valueInput = document.getElementById('editValue');
    if (valueInput) {
        let debounceTimer;
        valueInput.addEventListener('input', (e) => {
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => {
                validateValue(e.target.value);
            }, 300);
        });
    }

    // Setup password toggle
    const toggleBtn = document.getElementById('togglePassword');
    if (toggleBtn) {
        toggleBtn.addEventListener('click', togglePasswordVisibility);
    }

    // Setup reset to default button
    const resetBtn = document.getElementById('resetToDefault');
    if (resetBtn) {
        resetBtn.addEventListener('click', resetToDefault);
    }
}

/**
 * Edit a setting by ID
 */
export async function editSetting(settingId) {
    try {
        const response = await ApiClient.get(`/admin/settings/${settingId}`, null, true);

        if (response.success) {
            currentEditSetting = response.data;

            if (currentEditSetting.readonly) {
                showToast('This setting is read-only', false);
                return;
            }

            populateEditModal(response.data);
            editModal.show();
        } else {
            showToast('Failed to load setting', false);
        }
    } catch (error) {
        console.error('Error loading setting:', error);
        showToast('Error loading setting', false);
    }
}

/**
 * Populate the edit modal
 */
function populateEditModal(setting) {
    // Full key (readonly)
    document.getElementById('editFullKey').textContent = setting.fullKey;

    // Type badge
    const typeEl = document.getElementById('editType');
    typeEl.textContent = setting.valueType;
    typeEl.className = `badge badge-type badge-type-${setting.valueType.toLowerCase()}`;

    // Value input
    const valueInput = document.getElementById('editValue');
    const isSensitive = setting.sensitive;

    // For sensitive values, show actual value only if user has it
    if (isSensitive && setting.value) {
        valueInput.value = setting.value;
    } else if (!isSensitive) {
        valueInput.value = setting.value || '';
    } else {
        valueInput.value = '';
    }

    // Configure input type based on value type
    configureInputType(setting.valueType, isSensitive);

    // Required indicator
    const requiredIndicator = document.getElementById('editRequiredIndicator');
    requiredIndicator.style.display = setting.required ? 'inline' : 'none';

    // Constraints box
    const constraintsBox = document.getElementById('editConstraintsBox');
    const constraintsInfo = document.getElementById('editConstraintsInfo');

    if (setting.constraints && Object.keys(setting.constraints).length > 0) {
        constraintsBox.style.display = 'block';
        constraintsInfo.innerHTML = renderConstraintsInfo(setting.constraints, setting.valueType);
    } else {
        constraintsBox.style.display = 'none';
    }

    // Default value box
    const defaultBox = document.getElementById('editDefaultBox');
    const defaultValueEl = document.getElementById('editDefaultValue');

    if (setting.defaultValue) {
        defaultBox.style.display = 'block';
        defaultValueEl.textContent = `Default: ${setting.defaultValue}`;
    } else {
        defaultBox.style.display = 'none';
    }

    // Value help text
    const valueHelp = document.getElementById('editValueHelp');
    if (setting.description) {
        valueHelp.textContent = setting.description.split('\n')[0]; // First line only
        valueHelp.style.display = 'block';
    } else {
        valueHelp.style.display = 'none';
    }

    // Reset validation status
    hideValidationStatus();

    // Initial validation
    if (valueInput.value) {
        validateValue(valueInput.value);
    }

    // Reinitialize feather icons
    if (typeof feather !== 'undefined') {
        feather.replace();
    }
}

/**
 * Configure input type based on value type
 */
function configureInputType(valueType, isSensitive) {
    const valueInput = document.getElementById('editValue');
    const toggleBtn = document.getElementById('togglePassword');

    isPasswordVisible = false;

    switch (valueType) {
        case 'SECRET':
        case 'PASSWORD':
            valueInput.type = 'password';
            toggleBtn.style.display = 'block';
            break;
        case 'BOOLEAN':
            valueInput.type = 'text';
            valueInput.placeholder = 'Enter true or false';
            toggleBtn.style.display = 'none';
            break;
        case 'INT':
        case 'LONG':
            valueInput.type = 'number';
            valueInput.step = '1';
            toggleBtn.style.display = 'none';
            break;
        case 'DOUBLE':
            valueInput.type = 'number';
            valueInput.step = 'any';
            toggleBtn.style.display = 'none';
            break;
        case 'URL':
            valueInput.type = 'url';
            valueInput.placeholder = 'https://...';
            toggleBtn.style.display = 'none';
            break;
        case 'EMAIL':
            valueInput.type = 'email';
            valueInput.placeholder = 'email@example.com';
            toggleBtn.style.display = 'none';
            break;
        default:
            valueInput.type = isSensitive ? 'password' : 'text';
            toggleBtn.style.display = isSensitive ? 'block' : 'none';
    }
}

/**
 * Render constraints info
 */
function renderConstraintsInfo(constraints, valueType) {
    const items = [];

    if (constraints.min !== undefined) {
        const unit = constraints.unit ? ` ${constraints.unit}` : '';
        items.push(`Min: ${constraints.min}${unit}`);
    }

    if (constraints.max !== undefined) {
        const unit = constraints.unit ? ` ${constraints.unit}` : '';
        items.push(`Max: ${constraints.max}${unit}`);
    }

    if (constraints.minLength !== undefined) {
        items.push(`Min Length: ${constraints.minLength} characters`);
    }

    if (constraints.allowedValues && Array.isArray(constraints.allowedValues)) {
        items.push(`Allowed: ${constraints.allowedValues.join(', ')}`);
    }

    return items.map(item => `<div class="mb-1">${escapeHtml(item)}</div>`).join('');
}

/**
 * Toggle password visibility
 */
function togglePasswordVisibility() {
    const valueInput = document.getElementById('editValue');
    const toggleBtn = document.getElementById('togglePassword');
    const icon = toggleBtn.querySelector('svg') || toggleBtn.querySelector('i');

    isPasswordVisible = !isPasswordVisible;
    valueInput.type = isPasswordVisible ? 'text' : 'password';

    if (icon) {
        icon.setAttribute('data-feather', isPasswordVisible ? 'eye-off' : 'eye');
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
    }
}

/**
 * Reset to default value
 */
function resetToDefault() {
    if (currentEditSetting && currentEditSetting.defaultValue) {
        const valueInput = document.getElementById('editValue');
        valueInput.value = currentEditSetting.defaultValue;
        validateValue(currentEditSetting.defaultValue);
    }
}

/**
 * Validate value against constraints
 */
async function validateValue(value) {
    if (!currentEditSetting) return;

    const statusEl = document.getElementById('validationStatus');
    const messageEl = document.getElementById('validationMessage');

    try {
        const response = await ApiClient.post('/admin/settings/validate', {
            fullKey: currentEditSetting.fullKey,
            value: value
        }, true);

        if (response.success) {
            const result = response.data;

            statusEl.style.display = 'flex';

            if (result.valid) {
                statusEl.classList.remove('invalid');
                statusEl.classList.add('valid');

                let message = 'Valid';
                if (result.currentLength) {
                    message += ` (${result.currentLength})`;
                }
                messageEl.textContent = message;

                // Update icon
                const icon = statusEl.querySelector('svg, i');
                if (icon) {
                    icon.setAttribute('data-feather', 'check-circle');
                }
            } else {
                statusEl.classList.remove('valid');
                statusEl.classList.add('invalid');

                messageEl.textContent = result.errors.join('; ');

                // Update icon
                const icon = statusEl.querySelector('svg, i');
                if (icon) {
                    icon.setAttribute('data-feather', 'x-circle');
                }
            }

            if (typeof feather !== 'undefined') {
                feather.replace();
            }
        }
    } catch (error) {
        console.error('Validation error:', error);
        hideValidationStatus();
    }
}

/**
 * Hide validation status
 */
function hideValidationStatus() {
    const statusEl = document.getElementById('validationStatus');
    if (statusEl) {
        statusEl.style.display = 'none';
    }
}

/**
 * Save setting
 */
async function saveSetting() {
    if (!currentEditSetting) return;

    const valueInput = document.getElementById('editValue');
    const value = valueInput.value;

    // Validate required
    if (currentEditSetting.required && !value.trim()) {
        showToast('Value is required', false);
        return;
    }

    const saveBtn = document.getElementById('saveSettingBtn');
    const originalText = saveBtn.innerHTML;
    saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Saving...';
    saveBtn.disabled = true;

    try {
        const response = await ApiClient.put('/admin/settings', {
            fullKey: currentEditSetting.fullKey,
            value: value
        }, true);

        if (response.success) {
            showToast('Setting saved successfully', true);
            editModal.hide();

            // Refresh the settings table
            if (window.settingManager && window.settingManager.refreshSettings) {
                window.settingManager.refreshSettings();
            }
        } else {
            showToast(response.message || 'Failed to save setting', false);
        }
    } catch (error) {
        console.error('Error saving setting:', error);
        showToast('Error saving setting', false);
    } finally {
        saveBtn.innerHTML = originalText;
        saveBtn.disabled = false;
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
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
    if (typeof window.showToast === 'function') {
        window.showToast(message, isSuccess);
    } else {
        alert(message);
    }
}

