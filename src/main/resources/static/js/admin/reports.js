/**
 * Reports page initialization script
 */
(function () {
    'use strict';

    $(document).ready(function () {
        if (!ApiClient.getToken()) {
            var currentPath = window.location.pathname;
            window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
            return;
        }
        $('#reportsTable').DataTable({language: {url: "//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json"}});
    });
})();

