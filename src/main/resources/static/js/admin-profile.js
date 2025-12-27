/**
 * Admin Profile Modal JavaScript
 * Handles profile editing, password change, and passkey management
 */
const AdminProfile = {
    // Modal instances
    profileModal: null,
    addPasskeyModal: null,
    renamePasskeyModal: null,
    deletePasskeyModal: null,

    /**
     * Initialize the profile modal and event listeners
     */
    init() {
        // Wait for DOM to be ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => this.setup());
        } else {
            this.setup();
        }
    },

    /**
     * Setup event listeners and modal instances
     */
    setup() {

        // Get modal elements
        const profileModalEl = document.getElementById('profileModal');
        const addPasskeyModalEl = document.getElementById('addPasskeyModal');
        const renamePasskeyModalEl = document.getElementById('renamePasskeyModal');
        const deletePasskeyModalEl = document.getElementById('deletePasskeyModal');

            profile: !!profileModalEl,
            addPasskey: !!addPasskeyModalEl,
            renamePasskey: !!renamePasskeyModalEl,
            deletePasskey: !!deletePasskeyModalEl
        });

        if (!profileModalEl) {
            return;
        }

        // Store modal element references (we'll get instances when needed)
        this.profileModalEl = profileModalEl;
        this.addPasskeyModalEl = addPasskeyModalEl;
        this.renamePasskeyModalEl = renamePasskeyModalEl;
        this.deletePasskeyModalEl = deletePasskeyModalEl;


        // Load profile data when modal is shown
        profileModalEl.addEventListener('shown.bs.modal', () => {
            this.loadProfile();
            this.checkPasskeySupport();
            // Re-render feather icons for modal content - safely
            this.safeFeatherReplace();

            // Check if passkey tab is active (e.g., from URL hash #tab-passkey)
            const passkeyTabContent = document.getElementById('tab-passkey');
            const passkeyTabLink = document.getElementById('tab-passkey-link');
            const isPasskeyActive = (passkeyTabContent && (passkeyTabContent.classList.contains('active') || passkeyTabContent.classList.contains('show'))) ||
                (passkeyTabLink && passkeyTabLink.classList.contains('active'));
            if (isPasskeyActive) {
                this.loadPasskeys();
            }
        });

        // Tab change event - load passkeys when passkey tab is clicked
        const passkeyTab = document.getElementById('tab-passkey-link');
        if (passkeyTab) {
            passkeyTab.addEventListener('click', () => {
                // Small delay to ensure tab is visible
                setTimeout(() => this.loadPasskeys(), 100);
            });
        }

        // Avatar file input change
        const avatarInput = document.getElementById('avatarInput');
        if (avatarInput) {
            avatarInput.addEventListener('change', (e) => this.previewAvatar(e.target.files[0]));
        }

        // Save profile button
        const saveProfileBtn = document.getElementById('saveProfileBtn');
        if (saveProfileBtn) {
            saveProfileBtn.addEventListener('click', () => this.saveProfile());
        }

        // Change password button
        const changePasswordBtn = document.getElementById('changePasswordBtn');
        if (changePasswordBtn) {
            changePasswordBtn.addEventListener('click', () => this.changePassword());
        }

        // Add passkey button
        const addPasskeyBtn = document.getElementById('addPasskeyBtn');
        if (addPasskeyBtn) {
            addPasskeyBtn.addEventListener('click', () => {
                this.showAddPasskeyModal();
            });
        }

        // Confirm add passkey
        const confirmAddPasskeyBtn = document.getElementById('confirmAddPasskeyBtn');
        if (confirmAddPasskeyBtn) {
            confirmAddPasskeyBtn.addEventListener('click', () => this.registerPasskey());
        }

        // Confirm rename passkey
        const confirmRenamePasskeyBtn = document.getElementById('confirmRenamePasskeyBtn');
        if (confirmRenamePasskeyBtn) {
            confirmRenamePasskeyBtn.addEventListener('click', () => this.confirmRenamePasskey());
        }

        // Confirm delete passkey
        const confirmDeletePasskeyBtn = document.getElementById('confirmDeletePasskeyBtn');
        if (confirmDeletePasskeyBtn) {
            confirmDeletePasskeyBtn.addEventListener('click', () => this.confirmDeletePasskey());
        }

    },

    /**
     * Load user profile data
     */
    async loadProfile() {
        try {
            const response = await ApiClient.get('/profile', null, true);
            if (response.success && response.data) {
                const user = response.data;
                document.getElementById('profileEmail').value = user.email || '';
                document.getElementById('profileUsername').value = user.username || '';
                document.getElementById('profileFirstName').value = user.firstName || '';
                document.getElementById('profileLastName').value = user.lastName || '';

                if (user.avatar) {
                    document.getElementById('avatarPreview').src = user.avatar;
                }
            }
        } catch (error) {
            this.showToast('Failed to load profile', 'error');
        }
    },

    /**
     * Preview avatar before upload
     */
    previewAvatar(file) {
        if (!file) return;

        // Validate file type
        if (!file.type.startsWith('image/')) {
            this.showToast('Please select an image file', 'error');
            return;
        }

        // Validate file size (max 5MB)
        if (file.size > 5 * 1024 * 1024) {
            this.showToast('Image must be less than 5MB', 'error');
            return;
        }

        const reader = new FileReader();
        reader.onload = (e) => {
            document.getElementById('avatarPreview').src = e.target.result;
        };
        reader.readAsDataURL(file);
    },

    /**
     * Save profile changes
     */
    async saveProfile() {
        const btn = document.getElementById('saveProfileBtn');
        const originalText = btn.innerHTML;

        try {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Saving...';

            // Check if avatar needs to be uploaded
            const avatarInput = document.getElementById('avatarInput');
            let avatarUrl = null;

            if (avatarInput.files && avatarInput.files[0]) {
                avatarUrl = await this.uploadAvatar(avatarInput.files[0]);
            }

            // Prepare profile data
            const profileData = {
                username: document.getElementById('profileUsername').value.trim(),
                firstName: document.getElementById('profileFirstName').value.trim(),
                lastName: document.getElementById('profileLastName').value.trim(),
                avatar: avatarUrl
            };

            const response = await ApiClient.put('/profile', profileData, true);

            if (response.success) {
                this.showToast('Profile updated successfully', 'success');
                // Clear file input
                avatarInput.value = '';
            } else {
                this.showToast(response.message || 'Failed to update profile', 'error');
            }
        } catch (error) {
            this.showToast('Failed to save profile', 'error');
        } finally {
            btn.disabled = false;
            btn.innerHTML = originalText;
            this.safeFeatherReplace();
        }
    },

    /**
     * Upload avatar to server
     */
    async uploadAvatar(file) {
        const formData = new FormData();
        formData.append('file', file);

        // Get token using ApiClient method
        const token = ApiClient.getToken();
        const headers = {};
        if (token) {
            headers['Authorization'] = token.startsWith('Bearer ') ? token : 'Bearer ' + token;
        }

        const response = await fetch('/api/profile/avatar', {
            method: 'POST',
            headers: headers,
            body: formData
        });

        const result = await response.json();
        if (result.success && result.data && result.data.avatar) {
            return result.data.avatar;
        }
        return null;
    },

    /**
     * Change password
     */
    async changePassword() {
        const currentPassword = document.getElementById('currentPassword').value;
        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        // Validation
        if (!currentPassword || !newPassword || !confirmPassword) {
            this.showToast('Please fill in all password fields', 'error');
            return;
        }

        if (newPassword.length < 8) {
            this.showToast('New password must be at least 8 characters', 'error');
            return;
        }

        if (newPassword !== confirmPassword) {
            this.showToast('New passwords do not match', 'error');
            return;
        }

        const btn = document.getElementById('changePasswordBtn');
        const originalText = btn.innerHTML;

        try {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Changing...';

            const response = await ApiClient.post('/profile/password', {
                oldPassword: currentPassword,
                newPassword: newPassword
            }, true);

            if (response.success) {
                this.showToast('Password changed successfully', 'success');
                // Clear form
                document.getElementById('currentPassword').value = '';
                document.getElementById('newPassword').value = '';
                document.getElementById('confirmPassword').value = '';
            } else {
                this.showToast(response.message || 'Failed to change password', 'error');
            }
        } catch (error) {
            this.showToast('Failed to change password', 'error');
        } finally {
            btn.disabled = false;
            btn.innerHTML = originalText;
            this.safeFeatherReplace();
        }
    },

    /**
     * Check if WebAuthn/Passkey is supported
     */
    checkPasskeySupport() {
        const notSupportedEl = document.getElementById('passkeyNotSupported');
        const addBtn = document.getElementById('addPasskeyBtn');

        if (typeof PasskeyClient !== 'undefined' && PasskeyClient.isSupported()) {
            notSupportedEl?.classList.add('d-none');
            if (addBtn) addBtn.disabled = false;
        } else {
            notSupportedEl?.classList.remove('d-none');
            if (addBtn) addBtn.disabled = true;
        }
    },

     // Track loading state to prevent concurrent loads
    _isLoadingPasskeys: false,
    _loadPasskeysTimeout: null,

    /**
     * Load user's passkeys
     */
    loadPasskeys() {
        const self = this;
        const loadingEl = document.getElementById('passkeyLoading');
        const emptyEl = document.getElementById('passkeyEmpty');
        const listEl = document.getElementById('passkeyList');


        // Prevent concurrent loads
        if (this._isLoadingPasskeys) {
            return;
        }

        // Clear any existing timeout
        if (this._loadPasskeysTimeout) {
            clearTimeout(this._loadPasskeysTimeout);
            this._loadPasskeysTimeout = null;
        }

        this._isLoadingPasskeys = true;

        if (loadingEl) loadingEl.classList.remove('d-none');
        if (emptyEl) emptyEl.classList.add('d-none');
        if (listEl) listEl.innerHTML = '';

        // Show empty state helper
        const showEmptyState = () => {
            if (loadingEl) loadingEl.classList.add('d-none');
            if (emptyEl) emptyEl.classList.remove('d-none');
            if (listEl) listEl.innerHTML = '';
        };

        // Show passkeys helper
        const showPasskeys = (passkeys) => {
            if (loadingEl) loadingEl.classList.add('d-none');
            
            if (passkeys && Array.isArray(passkeys) && passkeys.length > 0) {
                if (emptyEl) emptyEl.classList.add('d-none');
                if (listEl) {
                    listEl.innerHTML = '';
                    passkeys.forEach(passkey => {
                        listEl.innerHTML += self.renderPasskeyItem(passkey);
                    });
                }
                // No need to call feather.replace() - icons are rendered as inline SVG
            } else {
                showEmptyState();
            }
        };

        // Finish loading helper
        const finishLoading = () => {
            self._isLoadingPasskeys = false;
            if (self._loadPasskeysTimeout) {
                clearTimeout(self._loadPasskeysTimeout);
                self._loadPasskeysTimeout = null;
            }
        };

        // Check if PasskeyClient is available
        if (typeof PasskeyClient === 'undefined') {
            showEmptyState();
            finishLoading();
            return;
        }

        // Set timeout for loading
        this._loadPasskeysTimeout = setTimeout(() => {
            if (self._isLoadingPasskeys) {
                showEmptyState();
                finishLoading();
            }
        }, 5000);

        PasskeyClient.listPasskeys()
            .then(passkeys => {
                finishLoading();
                showPasskeys(passkeys);
            })
            .catch(error => {
                finishLoading();
                showEmptyState();
            });
    },

    /**
     * Safely call feather.replace() wrapped in try-catch
     */
    safeFeatherReplace() {
        try {
            if (typeof feather !== 'undefined' && feather.replace) {
                feather.replace();
            }
        } catch (e) {
        }
    },

    /**
     * Helper to create feather icon SVG directly
     */
    createIcon(iconName, className = '', width = 16) {
        try {
            if (typeof feather !== 'undefined' && feather.icons && feather.icons[iconName]) {
                return feather.icons[iconName].toSvg({ class: className, width: width, height: width });
            }
        } catch (e) {
        }
        return `<i data-feather="${iconName}" class="${className}" width="${width}"></i>`;
    },

    /**
     * Render a single passkey item
     */
    renderPasskeyItem(passkey) {
        const lastUsed = passkey.lastUsedAt
            ? new Date(passkey.lastUsedAt).toLocaleDateString('vi-VN', {
                year: 'numeric', month: 'short', day: 'numeric',
                hour: '2-digit', minute: '2-digit'
            })
            : 'Never';

        const createdAt = new Date(passkey.createdAt).toLocaleDateString('vi-VN', {
            year: 'numeric', month: 'short', day: 'numeric'
        });

        // Create icons directly as SVG
        const keyIcon = this.createIcon('key', '', 18);
        const clockIcon = this.createIcon('clock', '', 12);
        const calendarIcon = this.createIcon('calendar', '', 12);
        const editIcon = this.createIcon('edit-2', '', 14);
        const trashIcon = this.createIcon('trash-2', '', 14);

        return `
      <div class="list-group-item d-flex justify-content-between align-items-center">
        <div class="d-flex align-items-center">
          <div class="avatar bg-light-primary me-3">
            <span class="avatar-content">${keyIcon}</span>
          </div>
          <div>
            <h6 class="mb-0">${this.escapeHtml(passkey.deviceName)}</h6>
            <small class="text-muted">
              ${clockIcon} Last used: ${lastUsed} | 
              ${calendarIcon} Created: ${createdAt}
            </small>
          </div>
        </div>
        <div>
          <button class="btn btn-sm btn-light-secondary me-1" onclick="AdminProfile.showRenamePasskeyModal('${passkey.id}', '${this.escapeHtml(passkey.deviceName)}')" title="Rename">
            ${editIcon}
          </button>
          <button class="btn btn-sm btn-light-danger" onclick="AdminProfile.showDeletePasskeyModal('${passkey.id}', '${this.escapeHtml(passkey.deviceName)}')" title="Delete">
            ${trashIcon}
          </button>
        </div>
      </div>
    `;
    },

  /**
   * Show add passkey modal with device detection
   */
  showAddPasskeyModal() {
    const deviceNameInput = document.getElementById('passkeyDeviceName');
    const detectedDeviceName = document.getElementById('detectedDeviceName');
    const detectedDeviceIconContainer = document.getElementById('detectedDeviceInfo');
    const suggestionsContainer = document.getElementById('deviceSuggestions');
    
    // Clear previous input
    if (deviceNameInput) deviceNameInput.value = '';
    
    // Helper to create feather icon SVG directly (avoid feather.replace issues)
    const createIcon = (iconName, className = '', width = 16) => {
      if (typeof feather !== 'undefined' && feather.icons && feather.icons[iconName]) {
        return feather.icons[iconName].toSvg({ class: className, width: width, height: width });
      }
      // Fallback to i tag if feather not available
      return `<i data-feather="${iconName}" class="${className}" width="${width}"></i>`;
    };
    
    // Detect device and populate suggestions
    if (typeof PasskeyClient !== 'undefined' && PasskeyClient.detectDevice) {
      const deviceInfo = PasskeyClient.detectDevice();
      
      // Update detected device display
      if (detectedDeviceName) {
        detectedDeviceName.textContent = deviceInfo.suggestedName || 'Unknown Device';
      }
      
      // Update icon by replacing the icon element entirely
      if (detectedDeviceIconContainer) {
        const iconEl = detectedDeviceIconContainer.querySelector('#detectedDeviceIcon, svg');
        if (iconEl && iconEl.parentNode) {
          const newIcon = document.createElement('span');
          newIcon.id = 'detectedDeviceIcon';
          newIcon.className = 'me-2';
          newIcon.innerHTML = createIcon(deviceInfo.icon || 'monitor', '', 20);
          iconEl.parentNode.replaceChild(newIcon, iconEl);
        }
      }
      
      // Pre-fill with suggested name
      if (deviceNameInput && deviceInfo.suggestedName) {
        deviceNameInput.value = deviceInfo.suggestedName;
      }
      
      // Populate suggestions with inline SVG icons
      if (suggestionsContainer && deviceInfo.suggestions) {
        suggestionsContainer.innerHTML = '';
        deviceInfo.suggestions.forEach((suggestion, index) => {
          const btn = document.createElement('button');
          btn.type = 'button';
          // Use Voler's btn-light-primary for unselected, btn-primary for selected
          btn.className = index === 0 ? 'btn btn-sm btn-primary' : 'btn btn-sm btn-light-primary';
          btn.innerHTML = createIcon(suggestion.icon, 'me-1', 14) + suggestion.name;
          btn.addEventListener('click', () => {
            if (deviceNameInput) {
              deviceNameInput.value = suggestion.name;
              // Highlight selected using Voler classes
              suggestionsContainer.querySelectorAll('.btn').forEach(b => {
                b.classList.remove('btn-primary');
                b.classList.add('btn-light-primary');
              });
              btn.classList.remove('btn-light-primary');
              btn.classList.add('btn-primary');
            }
          });
          suggestionsContainer.appendChild(btn);
        });
      }
    }
    
    // Use jQuery to show modal (works with Voler's bundled Bootstrap)
    const modalEl = this.addPasskeyModalEl || document.getElementById('addPasskeyModal');
    if (modalEl && typeof $ !== 'undefined') {
      $(modalEl).modal('show');
    } else {
    }
  },

    /**
     * Register a new passkey
     */
    registerPasskey() {
        const self = this;
        const deviceNameInput = document.getElementById('passkeyDeviceName');
        const deviceName = deviceNameInput ? deviceNameInput.value.trim() : '';

        if (!deviceName) {
            this.showToast('Please enter a device name', 'error');
            return;
        }

        const btn = document.getElementById('confirmAddPasskeyBtn');
        if (!btn) return;

        const originalText = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Registering...';

        const resetBtn = () => {
            btn.disabled = false;
            btn.innerHTML = originalText;
            self.safeFeatherReplace();
        };

        PasskeyClient.register(deviceName)
            .then(() => {
                // Hide modal using jQuery
                const modalEl = self.addPasskeyModalEl || document.getElementById('addPasskeyModal');
                if (modalEl && typeof $ !== 'undefined') $(modalEl).modal('hide');
                self.showToast('Passkey registered successfully', 'success');
                self.loadPasskeys();
                resetBtn();
            })
            .catch(error => {
                self.showToast(error.message || 'Failed to register passkey', 'error');
                resetBtn();
            });
    },

    /**
     * Show rename passkey modal
     */
    showRenamePasskeyModal(id, currentName) {
        document.getElementById('renamePasskeyId').value = id;
        document.getElementById('newPasskeyName').value = currentName;
        const modalEl = this.renamePasskeyModalEl || document.getElementById('renamePasskeyModal');
        if (modalEl && typeof $ !== 'undefined') $(modalEl).modal('show');
        this.safeFeatherReplace();
    },

    /**
     * Confirm rename passkey
     */
    confirmRenamePasskey() {
        const self = this;
        const id = document.getElementById('renamePasskeyId').value;
        const newName = document.getElementById('newPasskeyName').value.trim();

        if (!newName) {
            this.showToast('Please enter a name', 'error');
            return;
        }

        const btn = document.getElementById('confirmRenamePasskeyBtn');
        const originalText = btn.innerHTML;

        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Saving...';

        PasskeyClient.renamePasskey(id, newName)
            .then(() => {
                const modalEl = self.renamePasskeyModalEl || document.getElementById('renamePasskeyModal');
                if (modalEl && typeof $ !== 'undefined') $(modalEl).modal('hide');
                self.showToast('Passkey renamed successfully', 'success');
                self.loadPasskeys();
            })
            .catch(error => {
                self.showToast(error.message || 'Failed to rename passkey', 'error');
            })
            .finally(() => {
                btn.disabled = false;
                btn.innerHTML = originalText;
            });
    },

    /**
     * Show delete passkey modal
     */
    showDeletePasskeyModal(id, deviceName) {
        document.getElementById('deletePasskeyId').value = id;
        document.getElementById('deletePasskeyName').textContent = deviceName;
        const modalEl = this.deletePasskeyModalEl || document.getElementById('deletePasskeyModal');
        if (modalEl && typeof $ !== 'undefined') $(modalEl).modal('show');
        this.safeFeatherReplace();
    },

    /**
     * Confirm delete passkey
     */
    confirmDeletePasskey() {
        const self = this;
        const id = document.getElementById('deletePasskeyId').value;

        const btn = document.getElementById('confirmDeletePasskeyBtn');
        const originalText = btn.innerHTML;

        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Deleting...';

        PasskeyClient.deletePasskey(id)
            .then(() => {
                const modalEl = self.deletePasskeyModalEl || document.getElementById('deletePasskeyModal');
                if (modalEl && typeof $ !== 'undefined') $(modalEl).modal('hide');
                self.showToast('Passkey deleted successfully', 'success');
                self.loadPasskeys();
            })
            .catch(error => {
                self.showToast(error.message || 'Failed to delete passkey', 'error');
            })
            .finally(() => {
                btn.disabled = false;
                btn.innerHTML = originalText;
            });
    },

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    },

    /**
     * Show toast notification
     */
    showToast(message, type = 'info') {
        const bgClass = type === 'success' ? 'bg-success' : type === 'error' ? 'bg-danger' : 'bg-info';

        const toastHtml = `
      <div class="position-fixed bottom-0 end-0 p-3" style="z-index: 9999">
        <div class="toast show align-items-center text-white ${bgClass}" role="alert">
          <div class="d-flex">
            <div class="toast-body">${message}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
          </div>
        </div>
      </div>
    `;

        const container = document.createElement('div');
        container.innerHTML = toastHtml;
        document.body.appendChild(container);

        setTimeout(() => {
            container.remove();
        }, 3000);
    }
};

// Initialize when script loads
AdminProfile.init();

