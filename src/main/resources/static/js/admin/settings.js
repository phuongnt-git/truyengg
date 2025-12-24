/**
 * Settings page initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }
    loadSettings();
    $('#googleSettingsForm, #turnstileSettingsForm').on('submit', function (e) {
      e.preventDefault();
      const formId = $(this).attr('id');
      const key = formId === 'googleSettingsForm' ? 'google_client_id' : 'turnstile_secret_key';
      const value = $('#' + (formId === 'googleSettingsForm' ? 'google_client_id' : 'turnstile_secret_key')).val();
      ApiClient.post('/admin/settings', {key: key, value: value}, true).done(function (response) {
        if (response.success) showToast('Lưu thành công', true);
        else showToast('Lỗi khi lưu', false);
      });
    });
  });

  function loadSettings() {
    ['google_client_id', 'google_client_secret', 'google_redirect_uri', 'turnstile_secret_key'].forEach(function (key) {
      ApiClient.get('/admin/settings/' + key, null, true).done(function (response) {
        if (response.success && response.data) {
          $('#' + key).val(response.data.value || '');
        }
      });
    });
  }
})();

