/**
 * JobsModule - Quản lý danh sách crawl jobs với filter, search và pagination
 */
var JobsModule = (function () {
  var currentPage = 0;
  var pageSize = 10;
  var currentFilter = {};
  var refreshInterval = null;
  var broadcastChannel = null; // Store channel instance for reuse

  /**
   * Load danh sách jobs với filter và pagination
   */
  function loadJobs(page, filter) {
    currentPage = page || 0;
    currentFilter = filter || {};

    var params = {
      page: currentPage,
      size: pageSize
    };

    // Add filter params
    if (currentFilter.status) params.status = currentFilter.status;
    if (currentFilter.source) params.source = currentFilter.source;
    if (currentFilter.createdBy) params.createdBy = currentFilter.createdBy;
    if (currentFilter.search) params.search = currentFilter.search;
    if (currentFilter.fromDate) params.fromDate = currentFilter.fromDate;
    if (currentFilter.toDate) params.toDate = currentFilter.toDate;
    if (currentFilter.includeDeleted) params.includeDeleted = currentFilter.includeDeleted;

    var queryString = Object.keys(params).map(function (key) {
      return encodeURIComponent(key) + '=' + encodeURIComponent(params[key]);
    }).join('&');

        ApiClient.get('/api/admin/comic-crawls?' + queryString, null, true)
      .done(function (response) {
        if (response.success && response.data) {
          renderJobsTable(response.data);
          renderPagination(response.data);
        } else {
          showError('Lỗi khi tải danh sách jobs: ' + (response.message || 'Unknown error'));
        }
      })
      .fail(function (xhr) {
        showError('Lỗi khi tải danh sách jobs: ' + xhr.status);
      });
  }

  /**
   * Render jobs table
   */
  function renderJobsTable(pageData) {
    var tbody = $('#jobsTableBody');
    tbody.empty();

    if (!pageData.content || pageData.content.length === 0) {
      tbody.append('<tr><td colspan="9" class="text-center">Không có dữ liệu</td></tr>');
      startSmartRefresh(0); // No active jobs
      return;
    }

    // Count active jobs for smart refresh
    var activeJobsCount = 0;

    pageData.content.forEach(function (job) {
      // Count active jobs (RUNNING or PAUSED)
      // PENDING jobs are not counted as active but will be monitored for status changes
      if (job.status === 'RUNNING' || job.status === 'PAUSED') {
        activeJobsCount++;
      }

      var row = $('<tr>');

      // Checkbox
      row.append('<td><input type="checkbox" class="job-checkbox" value="' + job.id + '"></td>');

      // ID (shortened)
      var shortId = job.id.substring(0, 8) + '...';
      row.append('<td><a href="#" class="view-job-detail" data-job-id="' + job.id + '" title="Xem chi tiết">' + shortId + '</a></td>');

      // Status
      var statusClass = 'status-' + job.status.toLowerCase();
      var statusText = getStatusText(job.status);
      row.append('<td><span class="' + statusClass + '">' + statusText + '</span></td>');

      // Source
      row.append('<td>' + (job.source || '-') + '</td>');

      // URL (truncated)
      var urlText = job.url && job.url.length > 50 ? job.url.substring(0, 50) + '...' : (job.url || '-');
      row.append('<td title="' + (job.url || '') + '">' + urlText + '</td>');

      // Created by
      row.append('<td>' + (job.createdByUsername || '-') + '</td>');

      // Progress
      var progressHtml = renderProgress(job);
      row.append('<td class="progress-cell">' + progressHtml + '</td>');

      // Created at
      var createdAt = job.createdAt ? formatDateTime(job.createdAt) : '-';
      row.append('<td>' + createdAt + '</td>');

      // Actions
      var actionsHtml = renderActions(job);
      row.append('<td class="job-actions">' + actionsHtml + '</td>');

      tbody.append(row);
    });

    // Bind event handlers
    $('.view-job-detail').on('click', function (e) {
      e.preventDefault();
      var jobId = $(this).data('job-id');
      JobDetailModal.show(jobId);
    });

    // Start smart refresh based on active jobs
    startSmartRefresh(activeJobsCount);
  }

  /**
   * Start smart refresh with interval based on active jobs
   */
  function startSmartRefresh(activeJobsCount) {
    // Clear existing interval
    if (refreshInterval) {
      clearInterval(refreshInterval);
      refreshInterval = null;
    }

    // Set interval based on active jobs
    // If there are active jobs, refresh more frequently (5 seconds)
    // Otherwise, refresh less frequently (30 seconds)
    var interval = activeJobsCount > 0 ? 5000 : 30000;

    console.log('Starting smart refresh with interval:', interval, 'ms (active jobs:', activeJobsCount + ')');

    refreshInterval = setInterval(function () {
      loadJobs(currentPage, currentFilter);
    }, interval);
  }

  /**
   * Render progress bar
   */
  function renderProgress(job) {
    if (job.status === 'PENDING') {
      return '<div class="progress progress-bar-sm">' +
        '<div class="progress-bar bg-secondary progress-bar-striped progress-bar-animated" style="width: 100%">Đang chờ...</div>' +
        '</div>' +
        '<small class="text-muted">Job đang chờ slot để bắt đầu</small>';
    } else if (job.status === 'COMPLETED') {
      return '<div class="progress progress-bar-sm"><div class="progress-bar bg-success" style="width: 100%">100%</div></div>';
    } else if (job.status === 'FAILED' || job.status === 'CANCELLED') {
      return '<div class="progress progress-bar-sm"><div class="progress-bar bg-danger" style="width: 100%">0%</div></div>';
    } else if (job.totalChapters > 0) {
      var percentage = Math.round((job.downloadedChapters / job.totalChapters) * 100);
      return '<div class="progress progress-bar-sm">' +
        '<div class="progress-bar bg-info" style="width: ' + percentage + '%">' + percentage + '%</div>' +
        '</div>' +
        '<small>' + job.downloadedChapters + '/' + job.totalChapters + ' chương</small>';
    } else {
      return '<div class="progress progress-bar-sm"><div class="progress-bar bg-info progress-bar-striped progress-bar-animated" style="width: 100%">Đang xử lý...</div></div>';
    }
  }

  /**
   * Render action buttons
   */
  function renderActions(job) {
    var html = '';

    // View detail (open modal)
    html += '<button class="btn btn-sm btn-info view-job-detail" data-job-id="' + job.id + '" title="Xem chi tiết">' +
      '<i class="fas fa-eye"></i></button>';

    // Pause (only for RUNNING)
    if (job.status === 'RUNNING') {
      html += '<button class="btn btn-sm btn-warning btn-pause-job" data-job-id="' + job.id + '" title="Tạm dừng">' +
        '<i class="fas fa-pause"></i></button>';
    }

    // Resume (only for PAUSED)
    if (job.status === 'PAUSED') {
      html += '<button class="btn btn-sm btn-success btn-resume-job" data-job-id="' + job.id + '" title="Tiếp tục">' +
        '<i class="fas fa-play"></i></button>';
    }

    // Cancel (only for RUNNING, PAUSED, or PENDING)
    if (job.status === 'RUNNING' || job.status === 'PAUSED' || job.status === 'PENDING') {
      html += '<button class="btn btn-sm btn-danger btn-cancel-job" data-job-id="' + job.id + '" title="Hủy">' +
        '<i class="fas fa-stop"></i></button>';
    }

    // Retry (only for FAILED, COMPLETED, or CANCELLED - not running)
    if (job.status === 'FAILED' || job.status === 'COMPLETED' || job.status === 'CANCELLED') {
      html += '<button class="btn btn-sm btn-primary btn-retry-job" data-job-id="' + job.id + '" title="Retry">' +
        '<i class="fas fa-redo"></i></button>';
    }

    // Delete
    html += '<button class="btn btn-sm btn-danger btn-delete-job" data-job-id="' + job.id + '" title="Xóa">' +
      '<i class="fas fa-trash"></i></button>';

    return html;
  }

  /**
   * Render pagination
   */
  function renderPagination(pageData) {
    var pagination = $('#pagination');
    pagination.empty();

    if (!pageData.totalPages || pageData.totalPages <= 1) {
      return;
    }

    // Update info
    var start = pageData.number * pageData.size + 1;
    var end = Math.min(start + pageData.numberOfElements - 1, pageData.totalElements);
    $('#jobsTable_info').text('Hiển thị ' + start + ' đến ' + end + ' trong tổng số ' + pageData.totalElements + ' bản ghi');

    // Previous button
    var prevDisabled = pageData.first ? 'disabled' : '';
    pagination.append('<li class="page-item ' + prevDisabled + '">' +
      '<a class="page-link" href="#" data-page="' + (pageData.number - 1) + '">Trước</a></li>');

    // Page numbers
    var startPage = Math.max(0, pageData.number - 2);
    var endPage = Math.min(pageData.totalPages - 1, pageData.number + 2);

    if (startPage > 0) {
      pagination.append('<li class="page-item"><a class="page-link" href="#" data-page="0">1</a></li>');
      if (startPage > 1) {
        pagination.append('<li class="page-item disabled"><span class="page-link">...</span></li>');
      }
    }

    for (var i = startPage; i <= endPage; i++) {
      var active = i === pageData.number ? 'active' : '';
      pagination.append('<li class="page-item ' + active + '">' +
        '<a class="page-link" href="#" data-page="' + i + '">' + (i + 1) + '</a></li>');
    }

    if (endPage < pageData.totalPages - 1) {
      if (endPage < pageData.totalPages - 2) {
        pagination.append('<li class="page-item disabled"><span class="page-link">...</span></li>');
      }
      pagination.append('<li class="page-item"><a class="page-link" href="#" data-page="' + (pageData.totalPages - 1) + '">' + pageData.totalPages + '</a></li>');
    }

    // Next button
    var nextDisabled = pageData.last ? 'disabled' : '';
    pagination.append('<li class="page-item ' + nextDisabled + '">' +
      '<a class="page-link" href="#" data-page="' + (pageData.number + 1) + '">Sau</a></li>');

    // Bind pagination click
    pagination.find('.page-link').on('click', function (e) {
      e.preventDefault();
      var page = $(this).data('page');
      if (page !== undefined && !$(this).parent().hasClass('disabled')) {
        loadJobs(page, currentFilter);
      }
    });
  }

  /**
   * Get status text in Vietnamese
   */
  function getStatusText(status) {
    var statusMap = {
      'PENDING': 'Đang chờ',
      'RUNNING': 'Đang chạy',
      'PAUSED': 'Tạm dừng',
      'COMPLETED': 'Hoàn thành',
      'FAILED': 'Thất bại',
      'CANCELLED': 'Đã hủy'
    };
    return statusMap[status] || status;
  }

  /**
   * Format datetime
   */
  function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    try {
      var date = new Date(dateTimeStr);
      return date.toLocaleString('vi-VN');
    } catch (e) {
      return dateTimeStr;
    }
  }

  /**
   * Show error message
   */
  function showError(message) {
    $('#jobsTableBody').html('<tr><td colspan="9" class="text-center text-danger">' + message + '</td></tr>');
  }

  /**
   * Initialize module
   */
  function init() {
    // Setup cross-tab communication
    setupCrossTabCommunication();

    // Load initial jobs
    loadJobs(0, {});

    // Filter button
    $('#btnFilter').on('click', function () {
      var filter = {
        status: $('#filterStatus').val(),
        source: $('#filterSource').val(),
        createdBy: $('#filterCreatedBy').val() ? parseInt($('#filterCreatedBy').val()) : null,
        search: $('#filterSearch').val(),
        includeDeleted: false
      };
      loadJobs(0, filter);
    });

    // Reset filter button
    $('#btnResetFilter').on('click', function () {
      $('#filterForm')[0].reset();
      loadJobs(0, {});
    });

    // Refresh button
    $('#btnRefresh').on('click', function () {
      loadJobs(currentPage, currentFilter);
    });

    // Delete all button
    $('#btnDeleteAll').on('click', function () {
      deleteAllJobs();
    });

    // Select all checkbox
    $('#selectAll').on('change', function () {
      $('.job-checkbox').prop('checked', $(this).prop('checked'));
    });

    // Smart refresh is handled in startSmartRefresh() based on active jobs
  }

  /**
   * Public refresh function - can be called from other modules
   * @param {string} expectedJobId - Optional job ID to check if it exists in the list after refresh
   * @param {number} retryCount - Internal retry counter (default: 0)
   */
  function refresh(expectedJobId, retryCount) {
    retryCount = retryCount || 0;
    console.log('Manual refresh triggered', expectedJobId ? '(checking for job: ' + expectedJobId + ')' : '');

    var originalDone = null;
    var originalFail = null;

    // Call loadJobs with a custom callback to check for expected job
    if (expectedJobId && retryCount < 3) {
      loadJobsWithCallback(currentPage, currentFilter, function (response) {
        if (response.success && response.data) {
          // Check if expected job exists in the list
          var jobFound = false;
          if (response.data.content) {
            for (var i = 0; i < response.data.content.length; i++) {
              if (response.data.content[i].id === expectedJobId) {
                jobFound = true;
                console.log('Expected job found in list after refresh');
                break;
              }
            }
          }

          // If job not found and we haven't exceeded retry limit, retry with exponential backoff
          if (!jobFound && retryCount < 3) {
            var delay = Math.pow(2, retryCount) * 500; // 500ms, 1000ms, 2000ms
            console.log('Job not found, retrying in ' + delay + 'ms (attempt ' + (retryCount + 1) + '/3)');
            setTimeout(function () {
              refresh(expectedJobId, retryCount + 1);
            }, delay);
          } else if (!jobFound) {
            console.warn('Job not found after 3 retry attempts');
          }
        }
      });
    } else {
      loadJobs(currentPage, currentFilter);
    }
  }

  /**
   * Load jobs with optional callback
   */
  function loadJobsWithCallback(page, filter, callback) {
    currentPage = page || 0;
    currentFilter = filter || {};

    var params = {
      page: currentPage,
      size: pageSize
    };

    // Add filter params
    if (currentFilter.status) params.status = currentFilter.status;
    if (currentFilter.source) params.source = currentFilter.source;
    if (currentFilter.createdBy) params.createdBy = currentFilter.createdBy;
    if (currentFilter.search) params.search = currentFilter.search;
    if (currentFilter.fromDate) params.fromDate = currentFilter.fromDate;
    if (currentFilter.toDate) params.toDate = currentFilter.toDate;
    if (currentFilter.includeDeleted) params.includeDeleted = currentFilter.includeDeleted;

    var queryString = Object.keys(params).map(function (key) {
      return encodeURIComponent(key) + '=' + encodeURIComponent(params[key]);
    }).join('&');

        ApiClient.get('/api/admin/comic-crawls?' + queryString, null, true)
      .done(function (response) {
        if (response.success && response.data) {
          renderJobsTable(response.data);
          renderPagination(response.data);
          if (callback) callback(response);
        } else {
          showError('Lỗi khi tải danh sách jobs: ' + (response.message || 'Unknown error'));
          if (callback) callback(response);
        }
      })
      .fail(function (xhr) {
        showError('Lỗi khi tải danh sách jobs: ' + xhr.status);
        if (callback) callback({success: false});
      });
  }

  /**
   * Setup cross-tab communication using BroadcastChannel
   */
  function setupCrossTabCommunication() {
    // Use BroadcastChannel if available (modern browsers)
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        broadcastChannel = new BroadcastChannel('truyengg-jobs-refresh');
        broadcastChannel.onmessage = function (event) {
          if (event.data === 'refresh-jobs') {
            console.log('Received refresh signal from another tab');
            refresh();
          }
        };
        
        broadcastChannel.onerror = function (error) {
          console.warn('BroadcastChannel error (non-critical):', error);
          // Fallback to localStorage if BroadcastChannel fails
          broadcastChannel = null;
        };
      } catch (error) {
        console.warn('Failed to create BroadcastChannel (non-critical):', error);
        broadcastChannel = null;
      }
    } else {
      // Fallback to localStorage events for older browsers
      window.addEventListener('storage', function (event) {
        if (event.key === 'truyengg-jobs-refresh' && event.newValue) {
          console.log('Received refresh signal from another tab via localStorage');
          refresh();
        }
      });
    }
  }

  /**
   * Broadcast refresh signal to other tabs
   */
  function broadcastRefresh() {
    // Use BroadcastChannel if available
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        // Reuse existing channel or create new one
        if (!broadcastChannel) {
          broadcastChannel = new BroadcastChannel('truyengg-jobs-refresh');
        }
        
        // Only post message if channel is in a valid state
        if (broadcastChannel && broadcastChannel.readyState === 'open') {
          broadcastChannel.postMessage('refresh-jobs');
        }
      } catch (error) {
        console.warn('BroadcastChannel postMessage error (non-critical):', error);
        // Fallback to localStorage if BroadcastChannel fails
        broadcastChannel = null;
        localStorage.setItem('truyengg-jobs-refresh', Date.now().toString());
        setTimeout(function() {
          localStorage.removeItem('truyengg-jobs-refresh');
        }, 100);
      }
    } else {
      // Fallback to localStorage
      localStorage.setItem('truyengg-jobs-refresh', Date.now().toString());
      setTimeout(function() {
        localStorage.removeItem('truyengg-jobs-refresh');
      }, 100);
    }
  }

  /**
   * Delete all jobs
   */
  function deleteAllJobs() {
    ConfirmationModal.show({
      title: 'Xác nhận xóa tất cả',
      message: 'Bạn có chắc muốn xóa tất cả jobs? Hành động này không thể hoàn tác nếu chọn xóa vĩnh viễn.',
      confirmText: 'Xóa',
      cancelText: 'Hủy',
      showHardDeleteCheckbox: true,
      onConfirm: function (data) {
        var url = '/api/admin/comic-crawls?hardDelete=' + (data.hardDelete || false);

        ApiClient.delete(url, null, true)
          .done(function (response) {
            if (response.success) {
              showSuccess(response.message || 'Đã xóa tất cả jobs');
              loadJobs(0, currentFilter);
            } else {
              showError('Lỗi: ' + (response.message || 'Không thể xóa jobs'));
            }
          })
          .fail(function (xhr) {
            var errorMsg = xhr.responseJSON?.message || 'Lỗi khi xóa jobs';
            showError(errorMsg);
          });
      }
    });
  }

  return {
    init: init,
    loadJobs: loadJobs,
    refresh: refresh,
    broadcastRefresh: broadcastRefresh
  };
})();

// Initialize on document ready
$(document).ready(function () {
  JobsModule.init();
});

