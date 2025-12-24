/**
 * Users management page initialization script
 */
(function () {
  'use strict';

  let usersTable;
  let currentPage = 0;
  const pageSize = 10;

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }

    loadUsers();

    // Initialize DataTable
    usersTable = $('#usersTable').DataTable({
      "language": {
        "url": "//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json"
      },
      "pageLength": pageSize,
      "responsive": true,
      "columnDefs": [
        {"orderable": false, "targets": 6}
      ],
      "paging": false,
      "info": false,
      "dom": 'Bfrtip'
    });

    // Edit role
    $(document).on('click', '.edit-role', function () {
      const userId = $(this).data('id');
      const role = $(this).data('role');
      $('#edit_user_id').val(userId);
      $('#edit_role').val(role);
      const modal = new bootstrap.Modal(document.getElementById('editRoleModal'));
      modal.show();
    });

    $('#saveRole').on('click', function () {
      const userId = $('#edit_user_id').val();
      const role = $('#edit_role').val();

      ApiClient.post('/admin/users/' + userId + '/role', {role: role}, true).done(function (response) {
        if (response.success) {
          showToast(response.message || 'Cập nhật vai trò thành công', true);
          const modal = bootstrap.Modal.getInstance(document.getElementById('editRoleModal'));
          if (modal) modal.hide();
          loadUsers();
        } else {
          showToast(response.message || 'Lỗi khi cập nhật vai trò', false);
        }
      }).fail(function (xhr) {
        showToast(handleApiError(xhr, 'Lỗi khi cập nhật vai trò'), false);
      });
    });

    // Ban user
    $(document).on('click', '.ban-user', function () {
      const userId = $(this).data('id');
      $('#ban_user_id').val(userId);
      const modal = new bootstrap.Modal(document.getElementById('banUserModal'));
      modal.show();
    });

    $('#saveBan').on('click', function () {
      const userId = $('#ban_user_id').val();
      const duration = $('#ban_duration').val();

      ApiClient.post('/admin/users/' + userId + '/ban', {duration: duration}, true).done(function (response) {
        if (response.success) {
          showToast(response.message || 'Đã cấm user thành công', true);
          const modal = bootstrap.Modal.getInstance(document.getElementById('banUserModal'));
          if (modal) modal.hide();
          loadUsers();
        } else {
          showToast(response.message || 'Lỗi khi cấm user', false);
        }
      }).fail(function (xhr) {
        showToast(handleApiError(xhr, 'Lỗi khi cấm user'), false);
      });
    });

    // Unban user
    $(document).on('click', '.unban-user', function () {
      const userId = $(this).data('id');
      if (confirm('Bạn có chắc chắn muốn gỡ cấm người dùng này?')) {
        ApiClient.post('/admin/users/' + userId + '/unban', null, true).done(function (response) {
          if (response.success) {
            showToast(response.message || 'Đã bỏ cấm user thành công', true);
            loadUsers();
          } else {
            showToast(response.message || 'Lỗi khi gỡ cấm user', false);
          }
        }).fail(function (xhr) {
          showToast(handleApiError(xhr, 'Lỗi khi gỡ cấm user'), false);
        });
      }
    });

    // Delete user
    $(document).on('click', '.delete-user', function () {
      const userId = $(this).data('id');
      $('#delete_user_id').val(userId);
      const modal = new bootstrap.Modal(document.getElementById('deleteUserModal'));
      modal.show();
    });

    $('#confirmDelete').on('click', function () {
      const userId = $('#delete_user_id').val();

      ApiClient.delete('/admin/users/' + userId, null, true).done(function (response) {
        if (response.success) {
          showToast(response.message || 'Đã xóa user thành công', true);
          const modal = bootstrap.Modal.getInstance(document.getElementById('deleteUserModal'));
          if (modal) modal.hide();
          loadUsers();
        } else {
          showToast(response.message || 'Lỗi khi xóa user', false);
        }
      }).fail(function (xhr) {
        showToast(handleApiError(xhr, 'Lỗi khi xóa user'), false);
      });
    });
  });

  function loadUsers() {
    ApiClient.get('/admin/users', {page: currentPage, size: pageSize}, true).done(function (response) {
      if (response.success && response.data) {
        const users = response.data.content || [];
        const $tbody = $('#users-tbody');
        $tbody.empty();

        users.forEach(function (user) {
          const roleBadge = getRoleBadge(user.role);
          const statusHtml = getStatusHtml(user.bannedUntil);
          const actionButtons = getActionButtons(user.id, user.role, user.bannedUntil);

          const row = `
                    <tr>
                        <td>${user.id}</td>
                        <td>${escapeHtml(user.username || '')}</td>
                        <td>${escapeHtml(user.email || '')}</td>
                        <td>${roleBadge}</td>
                        <td>${formatDate(user.createdAt)}</td>
                        <td>${statusHtml}</td>
                        <td>${actionButtons}</td>
                    </tr>
                `;
          $tbody.append(row);
        });

        // Update DataTable
        usersTable.clear().rows.add($tbody.find('tr')).draw();
        
        // Re-initialize Feather icons for new buttons
        if (typeof feather !== 'undefined') {
          feather.replace();
        }
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi tải danh sách người dùng'), false);
    });
  }

  function getRoleBadge(role) {
    if (role === 'ADMIN') {
      return '<span class="badge badge-admin">Quản trị viên</span>';
    } else if (role === 'TRANSLATOR') {
      return '<span class="badge badge-translator">Nhóm dịch</span>';
    } else {
      return '<span class="badge badge-user">Người dùng</span>';
    }
  }

  function getStatusHtml(bannedUntil) {
    if (bannedUntil) {
      const bannedDate = new Date(bannedUntil);
      const now = new Date();
      if (bannedDate > now) {
        return '<span class="banned">Bị cấm đến ' + formatDate(bannedUntil) + '</span>';
      }
    }
    return '<span class="active">Hoạt động</span>';
  }

  function getActionButtons(userId, role, bannedUntil) {
    let buttons = '<button class="btn btn-sm btn-primary edit-role" data-id="' + userId + '" data-role="' + role + '"><i data-feather="edit"></i> Vai trò</button> ';

    if (bannedUntil) {
      const bannedDate = new Date(bannedUntil);
      const now = new Date();
      if (bannedDate > now) {
        buttons += '<button class="btn btn-sm btn-success unban-user" data-id="' + userId + '"><i data-feather="unlock"></i> Gỡ cấm</button> ';
      } else {
        buttons += '<button class="btn btn-sm btn-warning ban-user" data-id="' + userId + '"><i data-feather="ban"></i> Cấm</button> ';
      }
    } else {
      buttons += '<button class="btn btn-sm btn-warning ban-user" data-id="' + userId + '"><i data-feather="ban"></i> Cấm</button> ';
    }

    buttons += '<button class="btn btn-sm btn-danger delete-user" data-id="' + userId + '"><i data-feather="trash-2"></i> Xóa</button>';

    return buttons;
  }

  function formatDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN') + ' ' + date.toLocaleTimeString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function escapeHtml(text) {
    const map = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    };
    return text ? text.replace(/[&<>"']/g, m => map[m]) : '';
  }
})();

