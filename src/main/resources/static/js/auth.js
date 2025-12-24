/**
 * Authentication module for TruyenGG
 * Handles login, register, logout, and Google OAuth
 */

$(document).ready(function () {
  // Tab switching
  $('.tab_login').on('click', function (e) {
    e.preventDefault();
    popup('login');
    $('.tab_login').addClass('active');
    $('.tab_reg').removeClass('active');
  });

  $('.tab_reg').on('click', function (e) {
    e.preventDefault();
    popup('register');
    $('.tab_reg').addClass('active');
    $('.tab_login').removeClass('active');
  });

  $('.forgot_pass').on('click', function (e) {
    e.preventDefault();
    popup('forgot');
  });

  // Login form submission
  $('#loginFormUnique').on('submit', function (e) {
    e.preventDefault();
    e.stopImmediatePropagation();

    const email = $('#email_login').val().trim();
    const password = $('#password_login').val().trim();
    const $button = $('.button_login');
    const originalText = $button.text();

    if (!email) {
      showToast("Email là bắt buộc nhập.", false);
      return;
    }
    if (!validateEmail(email)) {
      showToast("Email không đúng định dạng.", false);
      return;
    }
    if (!password) {
      showToast("Mật khẩu là bắt buộc nhập.", false);
      return;
    }

    $button.addClass('btn-loading').text('').prop('disabled', true);

    // Get Turnstile token
    const turnstileResponse = $('.cf-turnstile').first().data('cf-turnstile-response');

    ApiClient.post('/auth/login', {
      email: email,
      password: password,
      turnstileResponse: turnstileResponse
    }, false).done(function (response) {
      $button.removeClass('btn-loading').text(originalText).prop('disabled', false);

      if (response.success && response.data) {
        const tokenData = response.data;
        // Handle new TokenResponse format (accessToken, refreshToken)
        if (tokenData.accessToken && tokenData.refreshToken) {
          ApiClient.setToken(tokenData.accessToken, tokenData.refreshToken);
        } else if (tokenData.token) {
          // Legacy support: if only token is provided
          ApiClient.setToken(tokenData.token, null);
        }

        showToast(response.message || 'Đăng nhập thành công', true);
        $('#loginFormUnique')[0].reset();
        reset();

        // Handle redirect if present
        const urlParams = new URLSearchParams(window.location.search);
        const redirectUrl = urlParams.get('redirect');

        setTimeout(function () {
          if (redirectUrl && redirectUrl.startsWith('/')) {
            window.location.href = redirectUrl;
          } else {
            window.location.reload();
          }
        }, 1000);
      } else {
        showToast(response.message || 'Đăng nhập thất bại', false);
      }
    }).fail(function (xhr) {
      $button.removeClass('btn-loading').text(originalText).prop('disabled', false);
      const errorMsg = xhr.responseJSON?.message || 'Đã xảy ra lỗi khi đăng nhập';
      showToast(errorMsg, false);
    });
  });

  // Register form submission
  $('#registerForm').on('submit', function (e) {
    e.preventDefault();
    e.stopImmediatePropagation();

    const email = $('#email_register').val().trim();
    const password = $('#password_register').val().trim();
    const $button = $('#registerForm .btn_login');
    const originalText = $button.text();

    if (!email) {
      showToast("Email là bắt buộc nhập.", false);
      return;
    }
    if (!validateEmail(email)) {
      showToast("Email không đúng định dạng.", false);
      return;
    }
    if (!password) {
      showToast("Mật khẩu là bắt buộc nhập.", false);
      return;
    }
    if (password.length < 6) {
      showToast("Mật khẩu phải có ít nhất 6 ký tự.", false);
      return;
    }

    $button.addClass('btn-loading').text('').prop('disabled', true);

    // Get Turnstile token
    const turnstileResponse = $('.cf-turnstile').last().data('cf-turnstile-response');

    ApiClient.post('/auth/register', {
      email: email,
      password: password,
      turnstileResponse: turnstileResponse
    }, false).done(function (response) {
      $button.removeClass('btn-loading').text(originalText).prop('disabled', false);

      if (response.success) {
        showToast(response.message || 'Đăng ký thành công', true);
        $('#registerForm')[0].reset();
        reset();
        setTimeout(function () {
          window.location.reload();
        }, 2000);
      } else {
        showToast(response.message || 'Đăng ký thất bại', false);
      }
    }).fail(function (xhr) {
      $button.removeClass('btn-loading').text(originalText).prop('disabled', false);
      const errorMsg = xhr.responseJSON?.message || 'Đã xảy ra lỗi khi đăng ký';
      showToast(errorMsg, false);
    });
  });

  // Forgot password form submission
  $('#forgotForm').on('submit', function (e) {
    e.preventDefault();
    e.stopImmediatePropagation();

    const email = $('#email_forgot').val().trim();
    const $button = $('.button_forgot');
    const originalText = $button.text();

    if (!email) {
      showToast("Email là bắt buộc nhập.", false);
      return;
    }
    if (!validateEmail(email)) {
      showToast("Email không đúng định dạng.", false);
      return;
    }

    $button.addClass('btn-loading').text('').prop('disabled', true);

    ApiClient.post('/auth/forgot-password', null, false, false).done(function (response) {
      $button.removeClass('btn-loading').text(originalText).prop('disabled', false);
      showToast(response.message || 'Email đặt lại mật khẩu đã được gửi', true);
      $('#forgotForm')[0].reset();
      reset();
    }).fail(function (xhr) {
      $button.removeClass('btn-loading').text(originalText).prop('disabled', false);
      const errorMsg = xhr.responseJSON?.message || 'Đã xảy ra lỗi';
      showToast(errorMsg, false);
    });
  });

  // Google OAuth button click
  $('#google-login-btn').on('click', function (e) {
    e.preventDefault();
    // Get Google OAuth URL from backend
    ApiClient.get('/auth/google/url').done(function (response) {
      if (response.success && response.data && response.data.url) {
        window.location.href = response.data.url;
      } else {
        showToast('Không thể lấy Google OAuth URL', false);
      }
    }).fail(function () {
      showToast('Lỗi khi kết nối với Google', false);
    });
  });
});

/**
 * Logout function
 * Handles logout for both admin and normal users
 */
function logout() {
  const accessToken = ApiClient.getToken();
  const refreshToken = ApiClient.getRefreshToken();
  const currentPath = window.location.pathname;
  const isAdminPage = currentPath.startsWith('/admin');

  // If we have tokens, call API to blacklist them
  if (accessToken || refreshToken) {
    ApiClient.post('/auth/logout', null, true).done(function () {
      // Clear tokens from localStorage
      ApiClient.clearTokens();

      // Show success message
      if (typeof showToast === 'function') {
        showToast('Đăng xuất thành công', true);
      }

      // Redirect after short delay
      setTimeout(function () {
        if (isAdminPage) {
          window.location.href = '/auth/login';
        } else {
          window.location.href = '/';
        }
      }, 1000);
    }).fail(function () {
      // Even if API call fails, clear tokens and redirect
      ApiClient.clearTokens();

      if (isAdminPage) {
        window.location.href = '/auth/login';
      } else {
        window.location.href = '/';
      }
    });
  } else {
    // No tokens, just redirect
    if (isAdminPage) {
      window.location.href = '/auth/login';
    } else {
      window.location.href = '/';
    }
  }
}
