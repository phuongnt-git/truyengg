/**
 * Reports page initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }
    $('#reportsTable').DataTable({language: {url: "//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json"}});
  });
})();

