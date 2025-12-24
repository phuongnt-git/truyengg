/**
 * Account settings page initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }

    loadUserProfile();

    $('.list_acc a[data-section]').on('click', function () {
      const section = $(this).data('section');
      $('.list_acc li').removeClass('active');
      $(this).parent('li').addClass('active');
      loadSection(section);
    });

    $('#personal-info-form').on('submit', function (e) {
      e.preventDefault();
      updateProfile();
    });
  });

  function loadUserProfile() {
    ApiClient.get('/profile', null, true).done(function (response) {
      if (response.success && response.data) {
        const user = response.data;
        $('#username').val(user.username);
        $('#email').val(user.email);
        $('#lastName').val(user.lastName || '');
        $('#firstName').val(user.firstName || '');
        $('#gender').val(user.gender || 'MALE');
      }
    });
  }

  function loadSection(section) {
    // Load section content dynamically
    if (section === 'change-password') {
      $('#content-area').html(`
                    <h2>Đổi Mật Khẩu</h2>
                    <form id="change-password-form">
                        <div class="form-group">
                            <label>Mật khẩu cũ</label>
                            <input type="password" class="form-control" id="oldPassword" required>
                        </div>
                        <div class="form-group">
                            <label>Mật khẩu mới</label>
                            <input type="password" class="form-control" id="newPassword" required>
                        </div>
                        <div class="form-group">
                            <label>Xác nhận mật khẩu mới</label>
                            <input type="password" class="form-control" id="confirmPassword" required>
                        </div>
                        <button type="submit" class="btn btn-primary">Đổi Mật Khẩu</button>
                    </form>
                `);

      $('#change-password-form').on('submit', function (e) {
        e.preventDefault();
        const oldPassword = $('#oldPassword').val();
        const newPassword = $('#newPassword').val();
        const confirmPassword = $('#confirmPassword').val();

        if (newPassword !== confirmPassword) {
          showToast('Mật khẩu mới không khớp', false);
          return;
        }

        ApiClient.put('/profile/password', {
          oldPassword: oldPassword,
          newPassword: newPassword
        }, true).done(function (response) {
          if (response.success) {
            showToast('Đổi mật khẩu thành công', true);
            $('#change-password-form')[0].reset();
          }
        });
      });
    } else if (section === 'comments') {
      $('#content-area').html('<h2>Bình Luận</h2><div id="user-comments">Đang tải...</div>');
      loadUserComments();
    }
  }

  function updateProfile() {
    const data = {
      email: $('#email').val(),
      lastName: $('#lastName').val(),
      firstName: $('#firstName').val(),
      gender: $('#gender').val()
    };

    ApiClient.put('/profile', data, true).done(function (response) {
      if (response.success) {
        showToast('Cập nhật thông tin thành công', true);
      }
    });
  }

  function loadUserComments() {
    ApiClient.get('/profile/comments', null, true).done(function (response) {
      if (response.success && response.data) {
        const comments = response.data.content || [];
        const $container = $('#user-comments');
        $container.empty();

        if (comments.length === 0) {
          $container.html('<p>Bạn chưa có bình luận nào</p>');
          return;
        }

        comments.forEach(function (comment) {
          const commentHtml = `
                            <div class="comment-item mb-3">
                                <p><strong>${comment.comicName || 'N/A'}</strong></p>
                                <p>${comment.content}</p>
                                <small>${timeAgo(comment.createdAt)}</small>
                            </div>
                        `;
          $container.append(commentHtml);
        });
      }
    });
  }
})();

