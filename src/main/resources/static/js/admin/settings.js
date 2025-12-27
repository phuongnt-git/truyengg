/**
 * Settings page initialization script
 * Uses the new AdminSettingsController API endpoints
 */
(function () {
    'use strict';

    // Map old keys to new full_key format
    const keyMapping = {
        'google_client_id': 'integrations.oauth2.google.client_id',
        'google_client_secret': 'integrations.oauth2.google.client_secret',
        'google_redirect_uri': 'integrations.oauth2.google.redirect_uri',
        'turnstile_secret_key': 'security.turnstile.secret_key',
        'turnstile_site_key': 'security.turnstile.site_key'
    };

    $(document).ready(function () {
        if (!ApiClient.getToken()) {
            var currentPath = window.location.pathname;
            window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
            return;
        }

        loadSettings();

        // Google OAuth settings form
        $('#googleSettingsForm').on('submit', function (e) {
            e.preventDefault();
            saveMultipleSettings([
                {id: 'google_client_id', fullKey: keyMapping['google_client_id']},
                {id: 'google_client_secret', fullKey: keyMapping['google_client_secret']},
                {id: 'google_redirect_uri', fullKey: keyMapping['google_redirect_uri']}
            ]);
        });

        // Turnstile settings form
        $('#turnstileSettingsForm').on('submit', function (e) {
            e.preventDefault();
            saveMultipleSettings([
                {id: 'turnstile_secret_key', fullKey: keyMapping['turnstile_secret_key']},
                {id: 'turnstile_site_key', fullKey: keyMapping['turnstile_site_key']}
            ]);
        });
    });

    function loadSettings() {
        Object.entries(keyMapping).forEach(function ([elementId, fullKey]) {
            ApiClient.get('/admin/settings/by-key', {fullKey: fullKey}, true)
                .done(function (response) {
                    if (response.success && response.data) {
                        // Use maskedValue for sensitive fields, value for others
                        var displayValue = response.data.sensitive ? '' : (response.data.value || response.data.maskedValue || '');
                        $('#' + elementId).val(displayValue);
                    }
                })
                .fail(function () {
                    console.warn('Failed to load setting:', fullKey);
                });
        });
    }

    function saveMultipleSettings(settings) {
        var promises = settings.map(function (setting) {
            var value = $('#' + setting.id).val();
            // Skip empty sensitive fields (user didn't change them)
            if (!value && setting.id.includes('secret')) {
                return Promise.resolve();
            }
            return ApiClient.put('/admin/settings', {fullKey: setting.fullKey, value: value}, true);
        });

        Promise.all(promises)
            .then(function () {
                showToast('Settings saved successfully', true);
                loadSettings(); // Reload to show masked values
            })
            .catch(function (error) {
                console.error('Error saving settings:', error);
                showToast('Error saving settings', false);
            });
    }
})();
