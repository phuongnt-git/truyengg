/**
 * Dashboard initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }

    loadDashboardStats();
  });

  function loadDashboardStats() {
    // Load total users
    ApiClient.get('/admin/users', {page: 0, size: 1}, true).done(function (response) {
      if (response.success && response.data) {
        $('#total-users').text(response.data.totalElements || 0);
      }
    });

    // Load total admins (need to filter by role)
    ApiClient.get('/admin/users', {page: 0, size: 1000}, true).done(function (response) {
      if (response.success && response.data) {
        const admins = (response.data.content || []).filter(function (user) {
          return user.role === 'ADMIN';
        });
        $('#total-admins').text(admins.length);
      }
    });

    // Load total stories (last 30 days)
    ApiClient.get('/admin/comics', {page: 0, size: 1}, true).done(function (response) {
      if (response.success && response.data) {
        $('#total-stories').text(response.data.totalElements || 0);
      }
    });

    // Load total reports
    ApiClient.get('/api/reports', {page: 0, size: 1}, true).done(function (response) {
      if (response.success && response.data) {
        $('#total-reports').text(response.data.totalElements || 0);
      }
    }).fail(function () {
      $('#total-reports').text('0');
    });
  }
})();

