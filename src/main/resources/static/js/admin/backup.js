/**
 * Backup page initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }
    $('#selectAll').on('change', function () {
      $('input[type="checkbox"][name="comic_ids"]').prop('checked', $(this).prop('checked'));
    });
    $('#startBackup').on('click', function () {
      const selected = $('input[type="checkbox"][name="comic_ids"]:checked').map(function () {
        return $(this).val();
      }).get();
      if (selected.length === 0) {
        showToast('Vui lòng chọn ít nhất một truyện', false);
        return;
      }
      $('#backupOutput').html('Chức năng backup đang được phát triển...');
    });
  });
})();

