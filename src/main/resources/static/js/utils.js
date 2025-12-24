/**
 * Utility functions for TruyenGG
 */

/**
 * Format time ago (e.g., "2 giờ trước", "3 ngày trước")
 */
function timeAgo(dateString) {
  if (!dateString) return 'Chưa cập nhật';

  try {
    const updateTime = new Date(dateString);
    const currentTime = new Date();
    const diffMs = currentTime - updateTime;
    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffSecs / 60);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);
    const diffMonths = Math.floor(diffDays / 30);
    const diffYears = Math.floor(diffDays / 365);

    if (diffYears > 0) return diffYears + ' năm trước';
    if (diffMonths > 0) return diffMonths + ' tháng trước';
    if (diffDays > 0) return diffDays + ' ngày trước';
    if (diffHours > 0) return diffHours + ' giờ trước';
    if (diffMins > 0) return diffMins + ' phút trước';
    return 'Vừa xong';
  } catch (e) {
    console.error('Error calculating timeAgo:', e);
    return 'Chưa cập nhật';
  }
}

/**
 * Format number (e.g., 1000 -> "1K", 1000000 -> "1M")
 */
function formatNumber(number) {
  if (!number) return '0';
  if (number >= 1000000) {
    return (number / 1000000).toFixed(1) + 'M';
  }
  if (number >= 1000) {
    return (number / 1000).toFixed(1) + 'K';
  }
  return number.toString();
}

/**
 * Create slug from string
 */
function createSlug(text) {
  if (!text) return '';
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * Show toast notification
 */
function showToast(message, isSuccess = true) {
  const toastClass = isSuccess ? 'toast-success' : 'toast-error';
  const toast = $('<div class="toast ' + toastClass + '" role="alert" aria-live="assertive" aria-atomic="true" data-delay="3000">' +
    '<div class="toast-header">' +
    '<strong class="mr-auto">' + (isSuccess ? 'Thành công' : 'Lỗi') + '</strong>' +
    '<button type="button" class="ml-2 mb-1 close" data-dismiss="toast" aria-label="Close"><span aria-hidden="true">×</span></button>' +
    '</div>' +
    '<div class="toast-body">' + message + '</div>' +
    '</div>');

  $('.toast-container').append(toast);
  toast.toast('show');
  toast.on('hidden.bs.toast', function () {
    $(this).remove();
  });
}

/**
 * Validate email format
 */
function validateEmail(email) {
  const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return re.test(email);
}

/**
 * Toggle theme (light/dark)
 */
function toggleTheme(element) {
  const body = document.body;
  const isDark = body.classList.contains('dark-style');
  if (isDark) {
    body.classList.remove('dark-style');
    body.classList.add('light-style');
    if (element) element.innerHTML = '<i class="bi bi-moon-stars-fill"></i>';
    localStorage.setItem('theme', 'light');
  } else {
    body.classList.remove('light-style');
    body.classList.add('dark-style');
    if (element) element.innerHTML = '<i class="bi bi-brightness-high-fill"></i>';
    localStorage.setItem('theme', 'dark');
  }
}

/**
 * Apply saved theme on page load
 */
function applySavedTheme() {
  const savedTheme = localStorage.getItem('theme') || 'dark';
  const body = document.body;
  const themeSwitch = document.getElementById('setting_darkness');
  if (savedTheme === 'light') {
    body.classList.remove('dark-style');
    body.classList.add('light-style');
    if (themeSwitch) themeSwitch.innerHTML = '<i class="bi bi-moon-stars-fill"></i>';
  } else {
    body.classList.remove('light-style');
    body.classList.add('dark-style');
    if (themeSwitch) themeSwitch.innerHTML = '<i class="bi bi-brightness-high-fill"></i>';
  }
}

/**
 * Popup functions for login/register modals
 */
function popup(type) {
  const popup = document.getElementById('popupContainer');
  const loginForm = document.querySelector('.show_login');
  const regForm = document.querySelector('.show_reg');
  const passForm = document.querySelector('.show_pass');

  if (loginForm) loginForm.classList.remove('active');
  if (regForm) regForm.classList.remove('active');
  if (passForm) passForm.classList.remove('active');

  if (type === 'login' && loginForm) {
    loginForm.classList.add('active');
  } else if (type === 'register' && regForm) {
    regForm.classList.add('active');
  } else if (type === 'forgot' && passForm) {
    passForm.classList.add('active');
  }

  if (popup) popup.classList.add('active');
}

function reset() {
  const popup = document.getElementById('popupContainer');
  const loginForm = document.querySelector('.show_login');
  const regForm = document.querySelector('.show_reg');
  const passForm = document.querySelector('.show_pass');

  if (popup) popup.classList.remove('active');
  if (loginForm) loginForm.classList.remove('active');
  if (regForm) regForm.classList.remove('active');
  if (passForm) passForm.classList.remove('active');

  $('#register_message').html('');
  $('#email_register').val('');
  $('#password_register').val('');
  $('#email_login').val('');
  $('#password_login').val('');
  $('#email_forgot').val('');
}

/**
 * Setting type book (placeholder)
 */
function setting_type_book(element) {
  console.log('Toggle type book');
  // TODO: Implement book type switching
}

/**
 * Show loading spinner in container
 */
function showLoading(containerSelector, message = 'Đang tải...') {
  const $container = $(containerSelector);
  $container.html(`
        <div class="col-12 text-center">
            <div class="spinner-border text-primary" role="status">
                <span class="sr-only">${message}</span>
            </div>
            <p class="mt-2">${message}</p>
        </div>
    `);
}

/**
 * Show error message in container
 */
function showError(containerSelector, message, xhr = null) {
  const $container = $(containerSelector);
  let errorMsg = message;

  if (xhr) {
    if (xhr.responseJSON && xhr.responseJSON.message) {
      errorMsg = xhr.responseJSON.message;
    } else if (xhr.status === 0) {
      errorMsg = 'Không thể kết nối đến server. Vui lòng kiểm tra kết nối mạng.';
    } else if (xhr.status === 404) {
      errorMsg = 'Không tìm thấy dữ liệu.';
    } else if (xhr.status === 403) {
      errorMsg = 'Bạn không có quyền thực hiện thao tác này.';
    } else if (xhr.status >= 500) {
      errorMsg = 'Lỗi server. Vui lòng thử lại sau.';
    }
  }

  $container.html(`
        <div class="col-12 text-center">
            <p class="text-danger">${errorMsg}</p>
            <button class="btn btn-primary btn-sm mt-2" onclick="location.reload()">Thử lại</button>
        </div>
    `);
}

/**
 * Handle API error with proper error messages
 */
function handleApiError(xhr, defaultMessage = 'Đã xảy ra lỗi. Vui lòng thử lại.') {
  let errorMsg = defaultMessage;

  if (xhr.responseJSON) {
    if (xhr.responseJSON.message) {
      errorMsg = xhr.responseJSON.message;
    } else if (xhr.responseJSON.error) {
      errorMsg = xhr.responseJSON.error;
    }
  } else if (xhr.status === 0) {
    errorMsg = 'Không thể kết nối đến server. Vui lòng kiểm tra kết nối mạng.';
  } else if (xhr.status === 404) {
    errorMsg = 'Không tìm thấy dữ liệu.';
  } else if (xhr.status === 403) {
    errorMsg = 'Bạn không có quyền thực hiện thao tác này.';
  } else if (xhr.status === 401) {
    errorMsg = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  } else if (xhr.status >= 500) {
    errorMsg = 'Lỗi server. Vui lòng thử lại sau.';
  }

  return errorMsg;
}
