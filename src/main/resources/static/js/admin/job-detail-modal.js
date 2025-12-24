/**
 * JobDetailModal - Hiển thị chi tiết job trong modal với real-time progress updates
 */
var JobDetailModal = (function () {
  var currentJobId = null;
  var stompClient = null;
  var socket = null;
  var wsSubscription = null;

  // WebSocket connection state
  var wsConnectionState = 'disconnected'; // disconnected, connecting, connected, error
  var reconnectAttempts = 0;
  var maxReconnectAttempts = 10;
  var reconnectDelay = 1000; // Start with 1 second
  var reconnectTimer = null;
  var heartbeatInterval = null;
  var lastHeartbeatTime = null;

  // Statistics tracking
  var progressHistory = [];
  var lastUpdateTime = null;
  var lastDownloadedImages = 0;

  // Realtime update intervals
  var elapsedTimeInterval = null;
  var jobStartTime = null; // Timestamp khi job bắt đầu
  var lastProgressUpdateTime = null;

  /**
   * Show job detail modal
   */
  function show(jobId) {
    currentJobId = jobId;
    $('#jobDetailModal').modal('show');
    $('#jobDetailContent').html(
      '<div class="text-center" style="padding: 50px;">' +
      '<i class="fas fa-spinner fa-spin fa-3x"></i>' +
      '<p>Đang tải thông tin job...</p>' +
      '</div>'
    );

    // Cleanup any existing WebSocket connection
    cleanup();

    // Reset statistics tracking
    progressHistory = [];
    lastUpdateTime = null;
    lastDownloadedImages = 0;
    jobStartTime = null;
    lastProgressUpdateTime = null;

    // Pagination state for chapters
    var currentChapterPage = 0;
    var chapterPageSize = 10;

    function loadJobDetail(page) {
      if (page === undefined) page = 0;
      currentChapterPage = page;
      
      var url = '/api/admin/comic-crawls/' + jobId + '?page=' + page + '&size=' + chapterPageSize;
      ApiClient.get(url, null, true)
        .done(function (response) {
          if (response.success && response.data) {
            renderJobDetail(response.data);

          // Lưu jobStartTime để tính elapsed time realtime
          if (response.data.crawl.startTime) {
            jobStartTime = response.data.crawl.startTime;
          }

          // Luôn hiển thị progress từ API (đã có trong renderJobDetail)
          // Progress được hiển thị trong renderJobDetail nếu có (line 253-255)

          // Start WebSocket chỉ nếu job đang RUNNING hoặc PAUSED (để nhận realtime updates)
          if (response.data.crawl.status === 'RUNNING' ||
            response.data.crawl.status === 'PAUSED') {
            console.log('job-detail-modal.js: Job is RUNNING or PAUSED, starting WebSocket connection');
            startWebSocketConnection(jobId);
            // Start elapsed time timer cho job đang chạy
            if (jobStartTime) {
              startElapsedTimeTimer();
            }
          } else if (response.data.crawl.status === 'COMPLETED' ||
            response.data.crawl.status === 'FAILED' ||
            response.data.crawl.status === 'CANCELLED') {
            console.log('job-detail-modal.js: Job is ' + response.data.crawl.status + ', not starting WebSocket');
            console.log('job-detail-modal.js: Job is ' + response.data.crawl.status + ', not starting WebSocket');
            // Job đã hoàn thành, hiển thị elapsed time cố định từ startTime đến endTime
            if (jobStartTime && response.data.crawl.endTime) {
              var start = new Date(jobStartTime).getTime();
              var end = new Date(response.data.crawl.endTime).getTime();
              var elapsedMs = end - start;
              var elapsedSeconds = Math.floor(elapsedMs / 1000);
              var hours = Math.floor(elapsedSeconds / 3600);
              var minutes = Math.floor((elapsedSeconds % 3600) / 60);
              var seconds = elapsedSeconds % 60;
              var timeText = '';
              if (hours > 0) timeText += hours + ' giờ ';
              if (minutes > 0) timeText += minutes + ' phút ';
              timeText += seconds + ' giây';
              $('#modalElapsedTime').text(timeText);
            }
          }
        } else {
          $('#jobDetailContent').html(
            '<div class="alert alert-danger">Lỗi: ' + (response.message || 'Không thể tải chi tiết job') + '</div>'
          );
        }
      })
      .fail(function (xhr) {
        var errorMsg = xhr.responseJSON?.message || 'Lỗi khi tải chi tiết job';
        $('#jobDetailContent').html('<div class="alert alert-danger">' + errorMsg + '</div>');
      });
    }

    // Load initial page
    loadJobDetail(0);
  }

  /**
   * Render job detail content
   */
  function renderJobDetail(detail) {
    console.log('job-detail-modal.js: renderJobDetail called with:', detail);
    var job = detail.crawl;
    var checkpoint = detail.checkpoint;
    var progress = detail.currentProgress;
    var files = detail.downloadedFiles || [];
    var detailsPage = detail.details; // Page<ChapterCrawlDto>
    var details = detailsPage && detailsPage.content ? detailsPage.content : []; // Chapter crawl details
    var metrics = detail.metrics || null; // Metrics data

    console.log('job-detail-modal.js: details array:', details);
    console.log('job-detail-modal.js: details pagination:', detailsPage ? {
      totalPages: detailsPage.totalPages,
      currentPage: detailsPage.number,
      totalElements: detailsPage.totalElements
    } : 'no pagination');
    console.log('job-detail-modal.js: metrics:', metrics);

    var html = '';

    // Job Info Card
    html += '<div class="card mb-3">';
    html += '<div class="card-header"><h5 class="card-title mb-0"><i class="fas fa-info-circle"></i> Thông tin Job</h5></div>';
    html += '<div class="card-body">';
    html += '<div class="row">';
    html += '<div class="col-md-6">';
    html += '<p><strong>Job ID:</strong> <code>' + escapeHtml(job.id) + '</code></p>';
    html += '<p><strong>Trạng thái:</strong> <span class="status-' + job.status.toLowerCase() + '" id="modalJobStatus">' + getStatusText(job.status) + '</span></p>';
    html += '<p><strong>URL:</strong> <a href="' + escapeHtml(job.url || '#') + '" target="_blank">' + escapeHtml(job.url || '-') + '</a></p>';
    if (job.downloadMode) {
      html += '<p><strong>Download Mode:</strong> ' + escapeHtml(job.downloadMode) + '</p>';
    }
    if (job.partStart !== null && job.partEnd !== null) {
      html += '<p><strong>Phần:</strong> ' + job.partStart + ' - ' + job.partEnd + '</p>';
    }
    html += '</div>';
    html += '<div class="col-md-6">';
    html += '<p><strong>Người tạo:</strong> ' + escapeHtml(job.createdByUsername || '-') + '</p>';
    html += '<p><strong>Ngày tạo:</strong> ' + formatDateTime(job.createdAt) + '</p>';
    if (job.startTime) {
      html += '<p><strong>Bắt đầu:</strong> <span id="modalJobStartTime">' + formatDateTime(job.startTime) + '</span></p>';
    }
    if (job.endTime) {
      html += '<p><strong>Kết thúc:</strong> <span id="modalJobEndTime">' + formatDateTime(job.endTime) + '</span></p>';
    }
    html += '<p><strong>Tổng số chương:</strong> <span id="modalJobTotalChapters">' + (job.totalChapters || 0) + '</span></p>';
    html += '</div>';
    html += '</div>';

    // Checkpoint info (if paused)
    if (checkpoint) {
      html += '<div class="row mt-3">';
      html += '<div class="col-12">';
      html += '<div class="alert alert-warning">';
      html += '<h6><i class="fas fa-pause-circle"></i> Thông tin Checkpoint</h6>';
      html += '<p><strong>Chương hiện tại:</strong> ' + (checkpoint.currentChapterIndex + 1) + '</p>';
      html += '<p><strong>Đã crawl:</strong> ' + (checkpoint.crawledChapters ? checkpoint.crawledChapters.length : 0) + ' chương</p>';
      html += '<p><strong>Thời điểm pause:</strong> ' + formatDateTime(checkpoint.pausedAt) + '</p>';
      if (checkpoint.pauseReason) {
        html += '<p><strong>Lý do:</strong> ' + escapeHtml(checkpoint.pauseReason) + '</p>';
      }
      html += '</div>';
      html += '</div>';
      html += '</div>';
    }

    // Error message (if failed)
    if (job.errorMessage) {
      html += '<div class="row mt-3">';
      html += '<div class="col-12">';
      html += '<div class="alert alert-danger">';
      html += '<h6><i class="fas fa-exclamation-triangle"></i> Lỗi</h6>';
      html += '<p>' + escapeHtml(job.errorMessage) + '</p>';
      html += '</div>';
      html += '</div>';
      html += '</div>';
    }

    html += '</div>'; // card-body
    html += '</div>'; // card

    // Statistics Cards
    html += '<div class="row mb-3">';
    html += '<div class="col-md-6">';
    html += '<div class="card text-center" style="border: 1px solid #dee2e6; border-radius: 0.25rem; padding: 10px;">';
    html += '<div style="font-size: 1.5rem; color: #007bff; margin-bottom: 5px;"><i class="fas fa-tachometer-alt"></i></div>';
    html += '<div style="font-size: 1.25rem; font-weight: bold; color: #007bff;" id="modalDownloadSpeed">0 images/s</div>';
    html += '<div style="font-size: 0.875rem; color: #6c757d;">Tốc độ tải</div>';
    html += '</div>';
    html += '</div>';
    html += '<div class="col-md-6">';
    html += '<div class="card text-center" style="border: 1px solid #dee2e6; border-radius: 0.25rem; padding: 10px;">';
    html += '<div style="font-size: 1.5rem; color: #ffc107; margin-bottom: 5px;"><i class="fas fa-clock"></i></div>';
    html += '<div style="font-size: 1.25rem; font-weight: bold; color: #ffc107;" id="modalEta">-</div>';
    html += '<div style="font-size: 0.875rem; color: #6c757d;">Thời gian còn lại</div>';
    html += '</div>';
    html += '</div>';
    html += '</div>';
    html += '<div class="row mb-3">';
    html += '<div class="col-md-6">';
    html += '<div class="card text-center" style="border: 1px solid #dee2e6; border-radius: 0.25rem; padding: 10px;">';
    html += '<div style="font-size: 1.5rem; color: #28a745; margin-bottom: 5px;"><i class="fas fa-hdd"></i></div>';
    html += '<div style="font-size: 1.25rem; font-weight: bold; color: #28a745;" id="modalFileSize">0 MB</div>';
    html += '<div style="font-size: 0.875rem; color: #6c757d;">Kích thước file</div>';
    html += '</div>';
    html += '</div>';
    html += '<div class="col-md-6">';
    html += '<div class="card text-center" style="border: 1px solid #dee2e6; border-radius: 0.25rem; padding: 10px;">';
    html += '<div style="font-size: 1.5rem; color: #17a2b8; margin-bottom: 5px;"><i class="fas fa-network-wired"></i></div>';
    html += '<div style="font-size: 1.25rem; font-weight: bold; color: #17a2b8;" id="modalNetworkStats">0 / 0</div>';
    html += '<div style="font-size: 0.875rem; color: #6c757d;">Requests / Errors</div>';
    html += '</div>';
    html += '</div>';
    html += '</div>';

    // Progress Card
    html += '<div class="card mb-3">';
    html += '<div class="card-header">';
    html += '<h5 class="card-title mb-0"><i class="fas fa-tasks"></i> Tiến độ</h5>';
    html += '<div class="card-tools" style="float: right;">';
    html += '<span id="wsConnectionStatus" class="badge badge-secondary"><i class="fas fa-circle"></i> Đã ngắt kết nối</span>';
    html += '</div>';
    html += '</div>';
    html += '<div class="card-body">';
    html += '<div id="wsConnectionStatusText" class="text-muted small mb-2"></div>';

    // Chapter Progress
    html += '<div class="mb-3">';
    html += '<label>Tiến độ chương: <span id="modalChapterProgressText">0 / 0 (0%)</span></label>';
    html += '<div class="progress">';
    html += '<div id="modalChapterProgressBar" class="progress-bar progress-bar-striped progress-bar-animated bg-primary" role="progressbar" style="width: 0%">0%</div>';
    html += '</div>';
    html += '</div>';

    // Image Progress
    html += '<div class="mb-3">';
    html += '<label>Tiến độ hình ảnh: <span id="modalImageProgressText">0 / 0 (0%)</span></label>';
    html += '<div class="progress">';
    html += '<div id="modalImageProgressBar" class="progress-bar progress-bar-striped progress-bar-animated bg-success" role="progressbar" style="width: 0%">0%</div>';
    html += '</div>';
    html += '</div>';

    // Elapsed Time
    html += '<div class="mb-3">';
    html += '<p><strong>Thời gian đã trôi qua:</strong> <span id="modalElapsedTime">0 giây</span></p>';
    html += '</div>';

    // Current Message
    html += '<div class="mb-3">';
    html += '<p><strong>Thông báo hiện tại:</strong></p>';
    html += '<div id="modalCurrentMessage" class="alert alert-info" style="margin-bottom: 0;">Đang khởi tạo...</div>';
    html += '</div>';

    html += '</div>'; // card-body
    html += '</div>'; // card

    // Chapter Details Table Card
    html += '<div class="card mb-3">';
    html += '<div class="card-header"><h5 class="card-title mb-0"><i class="fas fa-list-alt"></i> Chi tiết Chapters</h5></div>';
    html += '<div class="card-body">';
    html += '<div class="table-responsive">';
    html += '<table class="table table-sm table-hover">';
    html += '<thead>';
    html += '<tr>';
    html += '<th>#</th>';
    html += '<th>Tên chương</th>';
    html += '<th>Trạng thái</th>';
    html += '<th>Tiến độ</th>';
    html += '<th>Hình ảnh</th>';
    html += '<th>Hình ảnh gốc</th>';
    html += '<th>Kích thước</th>';
    html += '<th>Thời gian</th>';
    html += '<th>Tốc độ</th>';
    html += '<th>Retry</th>';
    html += '<th>Lỗi</th>';
    html += '<th>Thao tác</th>';
    html += '</tr>';
    html += '</thead>';
    html += '<tbody id="modalChaptersTableBody">';
    html += '<tr><td colspan="12" class="text-center text-muted">Đang tải...</td></tr>';
    html += '</tbody>';
    html += '</table>';
    html += '<div id="modalChaptersPagination" class="mt-3"></div>';
    html += '</div>';
    html += '</div>'; // card-body
    html += '</div>'; // card

    // Messages/Logs Card
    html += '<div class="card mb-3">';
    html += '<div class="card-header"><h5 class="card-title mb-0"><i class="fas fa-list"></i> Nhật ký</h5></div>';
    html += '<div class="card-body">';
    html += '<div id="modalMessagesScrollBox" style="max-height: 400px; overflow-y: auto; border: 1px solid #dee2e6; padding: 15px; background-color: #fff; border-radius: 4px; font-family: \'Courier New\', monospace; font-size: 13px;">';
    html += '<div class="text-muted text-center">Chưa có thông báo nào...</div>';
    html += '</div>';
    html += '</div>'; // card-body
    html += '</div>'; // card

    // Actions Card
    html += '<div class="card">';
    html += '<div class="card-header"><h5 class="card-title mb-0"><i class="fas fa-cog"></i> Thao tác</h5></div>';
    html += '<div class="card-body">';
    html += '<div class="btn-group" id="modalActionButtons">';

    if (job.status === 'RUNNING') {
      if (job.status !== 'CANCELLED' && job.status !== 'COMPLETED' && job.status !== 'FAILED') {
        html += '<button class="btn btn-warning btn-pause-job-modal" data-job-id="' + job.id + '"><i class="fas fa-pause"></i> Tạm dừng</button>';
      }
    }
    if (job.status === 'PAUSED') {
      if (job.status !== 'CANCELLED' && job.status !== 'COMPLETED' && job.status !== 'FAILED') {
        html += '<button class="btn btn-success btn-resume-job-modal" data-job-id="' + job.id + '"><i class="fas fa-play"></i> Tiếp tục</button>';
      }
    }
    if (job.status === 'RUNNING' || job.status === 'PAUSED') {
      if (job.status !== 'CANCELLED' && job.status !== 'COMPLETED' && job.status !== 'FAILED') {
        html += '<button class="btn btn-danger btn-cancel-job-modal" data-job-id="' + job.id + '"><i class="fas fa-stop"></i> Hủy</button>';
      }
    }
    // Retry (only for FAILED, COMPLETED, or CANCELLED - not running)
    if (job.status === 'FAILED' || job.status === 'COMPLETED' || job.status === 'CANCELLED') {
      html += '<button class="btn btn-primary btn-retry-job-modal" data-job-id="' + job.id + '"><i class="fas fa-redo"></i> Retry</button>';
    }
    html += '<button class="btn btn-danger btn-delete-job-modal" data-job-id="' + job.id + '"><i class="fas fa-trash"></i> Xóa</button>';

    html += '</div>';
    html += '</div>'; // card-body
    html += '</div>'; // card

    $('#jobDetailContent').html(html);

    // Render chapter details table if available
    if (details && Array.isArray(details) && details.length > 0) {
      console.log('job-detail-modal.js: Rendering ' + details.length + ' chapters');
      renderChapterDetailsTable(details, job.url);
      // Render pagination controls
      if (detailsPage) {
        renderChapterPagination(detailsPage);
      }
    } else {
      console.log('job-detail-modal.js: No details to render');
      $('#modalChaptersTableBody').html('<tr><td colspan="12" class="text-center text-muted">Chưa có dữ liệu chapters</td></tr>');
      $('#modalChaptersPagination').empty();
    }

    // Render metrics if available
    if (metrics) {
      console.log('job-detail-modal.js: Rendering metrics:', metrics);
      renderMetrics(metrics);
    }

    // Render messages from currentProgress if available
    if (progress) {
      console.log('job-detail-modal.js: currentProgress:', progress);
      console.log('job-detail-modal.js: progress.messages:', progress.messages);
      console.log('job-detail-modal.js: progress.currentMessage:', progress.currentMessage);
      
      var messagesBox = $('#modalMessagesScrollBox');
      messagesBox.empty();
      
      // Render currentMessage as the first message if available
      if (progress.currentMessage) {
        addMessage('info', progress.currentMessage);
        $('#modalCurrentMessage').text(progress.currentMessage);
      }
      
      // Render messages array if available
      if (progress.messages && Array.isArray(progress.messages) && progress.messages.length > 0) {
        console.log('job-detail-modal.js: Rendering ' + progress.messages.length + ' messages from currentProgress');
        progress.messages.forEach(function(msg) {
          if (msg && msg !== progress.currentMessage) { // Avoid duplicate if currentMessage is in messages
            addMessage('info', msg);
          }
        });
      } else {
        console.log('job-detail-modal.js: No messages in currentProgress (messages is null, empty, or not an array)');
        // If no messages, show a placeholder
        if (!progress.currentMessage) {
          messagesBox.html('<div class="text-muted text-center">Chưa có thông báo nào...</div>');
        }
      }
    } else {
      console.log('job-detail-modal.js: No currentProgress available');
      var messagesBox = $('#modalMessagesScrollBox');
      messagesBox.html('<div class="text-muted text-center">Chưa có thông báo nào...</div>');
    }

    // Bind action buttons
    bindActionButtons();

    // Update initial progress if available
    if (progress) {
      updateProgressUI(progress);
    } else if (job.status === 'COMPLETED' || job.status === 'FAILED') {
      // Try to load progress from API as fallback for completed/failed jobs
      ApiClient.get('/api/admin/crawl/progress/' + job.id + '/poll', null, true)
        .done(function (response) {
          if (response.success && response.data) {
            updateProgressUI(response.data);
          }
        })
        .fail(function () {
          // Progress might not exist, ignore error
        });
    }
  }

  /**
   * Bind action buttons in modal
   */
  function bindActionButtons() {
    $('.btn-pause-job-modal').off('click').on('click', function () {
      var jobId = $(this).data('job-id');
      JobActionsModule.pauseJob(jobId, null, function () {
        // Refresh modal after pause
        show(jobId);
      });
    });

    $('.btn-resume-job-modal').off('click').on('click', function () {
      var jobId = $(this).data('job-id');
      JobActionsModule.resumeJob(jobId, function () {
        // Refresh modal after resume
        show(jobId);
      });
    });

    $('.btn-cancel-job-modal').off('click').on('click', function () {
      var jobId = $(this).data('job-id');
      JobActionsModule.cancelJob(jobId, null, function () {
        // Refresh modal after cancel
        show(jobId);
      });
    });

    $('.btn-retry-job-modal').off('click').on('click', function () {
      var jobId = $(this).data('job-id');
      JobActionsModule.retryJob(jobId, function () {
        // Refresh modal after retry
        show(jobId);
      });
    });

    $('.btn-delete-job-modal').off('click').on('click', function () {
      var jobId = $(this).data('job-id');
      JobActionsModule.deleteJob(jobId, false, function () {
        // Close modal after delete
        $('#jobDetailModal').modal('hide');
        JobsModule.refresh();
      });
    });
  }

  /**
   * Update WebSocket connection status UI
   */
  function updateConnectionStatus(state, message) {
    wsConnectionState = state;
    var statusBadge = $('#wsConnectionStatus');
    if (statusBadge.length === 0) {
      // Status badge will be added in HTML template
      return;
    }

    statusBadge.removeClass('badge-success badge-info badge-warning badge-danger');
    switch (state) {
      case 'connected':
        statusBadge.addClass('badge-success').html('<i class="fas fa-circle"></i> Đã kết nối');
        break;
      case 'connecting':
        statusBadge.addClass('badge-info').html('<i class="fas fa-spinner fa-spin"></i> Đang kết nối...');
        break;
      case 'disconnected':
        statusBadge.addClass('badge-secondary').html('<i class="fas fa-circle"></i> Đã ngắt kết nối');
        break;
      case 'error':
        statusBadge.addClass('badge-danger').html('<i class="fas fa-exclamation-circle"></i> Lỗi kết nối');
        break;
    }

    if (message) {
      var statusText = $('#wsConnectionStatusText');
      if (statusText.length > 0) {
        statusText.text(message);
      }
    }
  }

  /**
   * Start heartbeat monitoring
   */
  function startHeartbeat() {
    stopHeartbeat();
    lastHeartbeatTime = Date.now();

    heartbeatInterval = setInterval(function () {
      if (stompClient && stompClient.connected) {
        var now = Date.now();
        // Check if we haven't received a message in 30 seconds
        if (lastProgressUpdateTime && (now - lastProgressUpdateTime) > 30000) {
          console.warn('No progress update received in 30 seconds, connection may be stale');
          // Try to reconnect
          scheduleReconnect(currentJobId);
        }
      } else {
        stopHeartbeat();
      }
    }, 10000); // Check every 10 seconds
  }

  /**
   * Stop heartbeat monitoring
   */
  function stopHeartbeat() {
    if (heartbeatInterval) {
      clearInterval(heartbeatInterval);
      heartbeatInterval = null;
    }
  }

  /**
   * Schedule reconnection with exponential backoff
   */
  function scheduleReconnect(jobId) {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
    }

    if (reconnectAttempts >= maxReconnectAttempts) {
      console.error('Max reconnection attempts reached');
      updateConnectionStatus('error', 'Không thể kết nối sau ' + maxReconnectAttempts + ' lần thử');
      addMessage('error', 'Không thể kết nối WebSocket. Đang thử kết nối lại...');
      return;
    }

    reconnectAttempts++;
    var delay = Math.min(reconnectDelay * Math.pow(2, reconnectAttempts - 1), 30000); // Max 30 seconds

    console.log('Scheduling reconnection attempt ' + reconnectAttempts + ' in ' + delay + 'ms');
    updateConnectionStatus('connecting', 'Đang thử kết nối lại... (' + reconnectAttempts + '/' + maxReconnectAttempts + ')');

    reconnectTimer = setTimeout(function () {
      if (currentJobId === jobId) {
        startWebSocketConnection(jobId, true);
      }
    }, delay);
  }

  /**
   * Start WebSocket connection for real-time progress updates
   */
  function startWebSocketConnection(jobId, isReconnect) {
    // Ensure jobId is a string
    var jobIdStr = String(jobId);

    if (!isReconnect) {
      reconnectAttempts = 0;
      reconnectDelay = 1000;
    }

    console.log('job-detail-modal.js: Starting WebSocket connection for job:', jobIdStr, isReconnect ? '(reconnect attempt ' + reconnectAttempts + ')' : '');

    // Get JWT token from cookie
    var accessToken = ApiClient.getToken();
    if (!accessToken) {
      console.error('No access token found for WebSocket connection');
      addMessage('error', 'Lỗi: Không tìm thấy token xác thực');
      updateConnectionStatus('error', 'Không có token xác thực');
      return;
    }

    // Remove "Bearer " prefix if present
    if (accessToken.startsWith('Bearer ')) {
      accessToken = accessToken.substring(7);
    }

    // Close existing connection if any (unless it's a reconnect)
    if (!isReconnect) {
      cleanup();
    } else if (stompClient && stompClient.connected) {
      // If already connected, don't reconnect
      return;
    }

    updateConnectionStatus('connecting', 'Đang kết nối...');

    try {
      // Create SockJS connection
      socket = new SockJS('/ws');
      console.log('SockJS socket created');

      // Add SockJS event handlers
      socket.onopen = function () {
        console.log('SockJS connection opened');
      };

      socket.onclose = function (event) {
        console.log('SockJS connection closed', event);
        updateConnectionStatus('disconnected', 'Kết nối đã đóng');

        // Try to reconnect if we're still on the same job
        if (currentJobId === jobIdStr && wsConnectionState !== 'disconnected') {
          scheduleReconnect(jobIdStr);
        }
      };

      socket.onerror = function (error) {
        console.error('SockJS error:', error);
        updateConnectionStatus('error', 'Lỗi SockJS');
      };

      stompClient = Stomp.over(socket);

      // Disable STOMP heartbeat (we'll use our own)
      stompClient.heartbeat.outgoing = 0;
      stompClient.heartbeat.incoming = 0;

      // Enable debug logs for troubleshooting
      stompClient.debug = function (str) {
        if (str && str.toLowerCase().includes('error')) {
          console.error('STOMP Error:', str);
        } else {
          console.log('STOMP:', str);
        }
      };

      // Connect with JWT token in headers
      var headers = {
        'Authorization': 'Bearer ' + accessToken,
        'token': accessToken
      };

      console.log('Attempting STOMP connection with headers:', Object.keys(headers));
      stompClient.connect(
        headers,
        function (frame) {
          console.log('STOMP connection successful, frame:', frame);
          reconnectAttempts = 0; // Reset on successful connection
          reconnectDelay = 1000;
          updateConnectionStatus('connected', 'Kết nối thành công');
          addMessage('info', 'Đã kết nối WebSocket. Đang chờ cập nhật tiến độ...');

          // Verify STOMP client is connected before subscribing
          if (!stompClient || !stompClient.connected) {
            console.error('STOMP client not connected, cannot subscribe');
            addMessage('error', 'Lỗi: STOMP client chưa kết nối');
            updateConnectionStatus('error', 'STOMP chưa kết nối');
            scheduleReconnect(jobIdStr);
            return;
          }

          // Start heartbeat monitoring
          startHeartbeat();

          // Subscribe to progress updates
          var destination = '/topic/crawl-progress/' + jobIdStr;
          console.log('Subscribing to:', destination);
          console.log('STOMP client connected state:', stompClient.connected);

          try {
            wsSubscription = stompClient.subscribe(destination, function (message) {
              console.log('job-detail-modal.js: ✓ WebSocket message received for job:', jobIdStr);
              console.log('Message body:', message.body);
              lastHeartbeatTime = Date.now();

              try {
                var progress = JSON.parse(message.body);
                console.log('Progress update received:', progress.status, 'chapter:', progress.currentChapter + '/' + progress.totalChapters);
                updateProgressUI(progress);

                // Update last progress update time
                lastProgressUpdateTime = Date.now();

                // Handle job completion/failure/pause
                if (progress.status === 'completed') {
                  addMessage('info', 'Job đã hoàn thành!');
                  stopElapsedTimeTimer();
                  stopHeartbeat();
                  updateProgressUI(progress);
                } else if (progress.status === 'failed') {
                  addMessage('error', 'Job đã thất bại!');
                  stopElapsedTimeTimer();
                  stopHeartbeat();
                  updateProgressUI(progress);
                } else if (progress.status === 'paused') {
                  addMessage('warning', 'Job đã được tạm dừng');
                  stopElapsedTimeTimer();
                  updateProgressUI(progress);
                }
              } catch (e) {
                console.error('Error parsing WebSocket message:', e, message.body);
                addMessage('error', 'Lỗi khi xử lý thông điệp: ' + e.message);
              }
            });

            console.log('Subscription result:', wsSubscription);
            console.log('Subscription ID:', wsSubscription ? wsSubscription.id : 'null');

            if (wsSubscription) {
              console.log('✓ Subscription successful');
            } else {
              console.error('✗ Subscription failed - returned null');
              addMessage('error', 'Không thể đăng ký nhận cập nhật tiến độ');
              updateConnectionStatus('error', 'Lỗi đăng ký');
              scheduleReconnect(jobIdStr);
            }
          } catch (subscribeError) {
            console.error('Error during subscription:', subscribeError);
            addMessage('error', 'Lỗi khi đăng ký WebSocket: ' + subscribeError.message);
            updateConnectionStatus('error', 'Lỗi đăng ký');
            scheduleReconnect(jobIdStr);
          }
        },
        function (error) {
          console.error('WebSocket connection error:', error);
          var errorMsg = error.headers?.message || error.toString();
          addMessage('error', 'Lỗi kết nối WebSocket: ' + errorMsg);
          updateConnectionStatus('error', 'Lỗi kết nối: ' + errorMsg);

          // Schedule reconnection
          scheduleReconnect(jobIdStr);
        }
      );
    } catch (e) {
      console.error('Error creating WebSocket connection:', e);
      addMessage('error', 'Lỗi khi tạo kết nối WebSocket: ' + e.message);
      updateConnectionStatus('error', 'Lỗi: ' + e.message);
      scheduleReconnect(jobIdStr);
    }
  }


  /**
   * Calculate statistics from progress data
   */
  function calculateStatistics(progress) {
    var now = Date.now();
    var stats = {
      speed: 0, // images/second
      eta: null, // seconds
      estimatedFileSize: 0, // MB
      totalEstimatedFileSize: 0, // MB
      networkStats: {
        requests: 0,
        errors: 0,
        retries: 0
      }
    };

    // Calculate speed
    if (lastUpdateTime && lastDownloadedImages < progress.downloadedImages) {
      var timeDiff = (now - lastUpdateTime) / 1000; // seconds
      var imagesDiff = progress.downloadedImages - lastDownloadedImages;
      if (timeDiff > 0) {
        stats.speed = imagesDiff / timeDiff;
      }
    }

    // Calculate ETA
    if (stats.speed > 0 && progress.totalImages > progress.downloadedImages) {
      stats.eta = (progress.totalImages - progress.downloadedImages) / stats.speed;
    }

    // Estimate file size (assume 500KB per image)
    var avgImageSize = 500; // KB
    stats.estimatedFileSize = (progress.downloadedImages * avgImageSize) / 1024; // MB
    stats.totalEstimatedFileSize = (progress.totalImages * avgImageSize) / 1024; // MB

    // Parse network stats from messages
    if (progress.messages) {
      progress.messages.forEach(function (msg) {
        var lowerMsg = msg.toLowerCase();
        if (lowerMsg.includes('error') || lowerMsg.includes('lỗi') || lowerMsg.includes('failed')) {
          stats.networkStats.errors++;
        }
        if (lowerMsg.includes('retry') || lowerMsg.includes('thử lại')) {
          stats.networkStats.retries++;
        }
        if (lowerMsg.includes('download') || lowerMsg.includes('tải') || lowerMsg.includes('request')) {
          stats.networkStats.requests++;
        }
      });
    }

    // Update history
    progressHistory.push({
      timestamp: now,
      chapterPercent: progress.totalChapters > 0 ?
        (progress.currentChapter / progress.totalChapters * 100) : 0,
      imagePercent: progress.totalImages > 0 ?
        (progress.downloadedImages / progress.totalImages * 100) : 0,
      speed: stats.speed
    });

    // Keep only last 100 points (smaller for modal)
    if (progressHistory.length > 100) {
      progressHistory.shift();
    }

    lastUpdateTime = now;
    lastDownloadedImages = progress.downloadedImages;

    return stats;
  }

  /**
   * Update statistics UI
   */
  function updateStatisticsUI(stats) {
    // Download Speed
    var speedText = stats.speed > 0 ? stats.speed.toFixed(2) + ' images/s' : '0 images/s';
    $('#modalDownloadSpeed').text(speedText);

    // ETA
    var etaText = '-';
    if (stats.eta !== null && stats.eta > 0) {
      var hours = Math.floor(stats.eta / 3600);
      var minutes = Math.floor((stats.eta % 3600) / 60);
      var seconds = Math.floor(stats.eta % 60);
      if (hours > 0) {
        etaText = hours + 'h ' + minutes + 'm';
      } else if (minutes > 0) {
        etaText = minutes + 'm ' + seconds + 's';
      } else {
        etaText = seconds + 's';
      }
    }
    $('#modalEta').text(etaText);

    // File Size
    var fileSizeText = formatFileSize(stats.estimatedFileSize);
    if (stats.totalEstimatedFileSize > 0) {
      fileSizeText += ' / ' + formatFileSize(stats.totalEstimatedFileSize);
    }
    $('#modalFileSize').text(fileSizeText);

    // Network Stats
    var networkText = stats.networkStats.requests + ' / ' + stats.networkStats.errors;
    if (stats.networkStats.retries > 0) {
      networkText += ' (' + stats.networkStats.retries + ')';
    }
    $('#modalNetworkStats').text(networkText);
  }

  /**
   * Format file size
   */
  function formatFileSize(mb) {
    if (mb < 1) {
      return (mb * 1024).toFixed(2) + ' KB';
    } else if (mb < 1024) {
      return mb.toFixed(2) + ' MB';
    } else {
      return (mb / 1024).toFixed(2) + ' GB';
    }
  }

  /**
   * Update progress UI
   */
  function updateProgressUI(progress) {
    if (!progress) return;

    // Calculate and update statistics
    var stats = calculateStatistics(progress);
    updateStatisticsUI(stats);

    // Chapter Progress
    var chapterPercent = 0;
    if (progress.totalChapters > 0) {
      chapterPercent = Math.round((progress.currentChapter / progress.totalChapters) * 100);
    }
    $('#modalChapterProgressBar').css('width', chapterPercent + '%').text(chapterPercent + '%');
    $('#modalChapterProgressText').text(
      progress.currentChapter + ' / ' + progress.totalChapters + ' (' + chapterPercent + '%)'
    );

    // Image Progress
    var imagePercent = 0;
    if (progress.totalImages > 0) {
      imagePercent = Math.round((progress.downloadedImages / progress.totalImages) * 100);
    }
    $('#modalImageProgressBar').css('width', imagePercent + '%').text(imagePercent + '%');
    $('#modalImageProgressText').text(
      progress.downloadedImages + ' / ' + progress.totalImages + ' (' + imagePercent + '%)'
    );

    // Update chapter progress list if available
    if (progress.chapterProgress && Object.keys(progress.chapterProgress).length > 0) {
      updateChapterProgressList(progress.chapterProgress);
    }

    // Elapsed Time
    // Nếu có jobStartTime, timer sẽ cập nhật realtime
    // Nếu không, dùng elapsedSeconds từ progress
    if (!jobStartTime) {
      var elapsedSeconds = progress.elapsedSeconds || 0;
      var hours = Math.floor(elapsedSeconds / 3600);
      var minutes = Math.floor((elapsedSeconds % 3600) / 60);
      var seconds = elapsedSeconds % 60;
      var timeText = '';
      if (hours > 0) timeText += hours + ' giờ ';
      if (minutes > 0) timeText += minutes + ' phút ';
      timeText += seconds + ' giây';
      $('#modalElapsedTime').text(timeText);
    }
    // Nếu có jobStartTime, updateElapsedTimeRealtime() sẽ cập nhật

    // Current Message
    if (progress.currentMessage) {
      $('#modalCurrentMessage').text(progress.currentMessage);
      // Update alert class based on status
      $('#modalCurrentMessage').removeClass('alert-info alert-success alert-danger alert-warning');
      if (progress.status === 'completed') {
        $('#modalCurrentMessage').addClass('alert-success');
      } else if (progress.status === 'failed') {
        $('#modalCurrentMessage').addClass('alert-danger');
      } else if (progress.status === 'paused') {
        $('#modalCurrentMessage').addClass('alert-warning');
      } else {
        $('#modalCurrentMessage').addClass('alert-info');
      }
    }

    // Messages
    if (progress.messages && progress.messages.length > 0) {
      var existingMessages = $('#modalMessagesScrollBox .message-item').length;
      if (progress.messages.length > existingMessages) {
        for (var i = existingMessages; i < progress.messages.length; i++) {
          var message = progress.messages[i];
          var messageType = getMessageType(message);
          addMessage(messageType, message);
        }
      }
    }
  }

  /**
   * Format message để hiển thị đẹp hơn
   */
  function formatMessage(message) {
    if (!message) return message;

    // Phân tích message để format đẹp hơn
    var msg = message.toLowerCase();

    // Messages về ảnh đã tải
    if (msg.includes('ảnh') || msg.includes('image') || msg.includes('hình ảnh')) {
      // Tìm pattern như "đã tải X/Y ảnh" hoặc "X/Y hình ảnh"
      var imagePattern = /(\d+)\s*\/\s*(\d+)\s*(ảnh|hình ảnh|image)/i;
      var match = message.match(imagePattern);
      if (match) {
        var downloaded = match[1];
        var total = match[2];
        var percent = total > 0 ? Math.round((downloaded / total) * 100) : 0;
        return '<i class="fas fa-image text-info"></i> <strong>' + downloaded + '</strong> / ' + total + ' hình ảnh <span class="badge badge-info">' + percent + '%</span>';
      }
      return '<i class="fas fa-image text-info"></i> ' + escapeHtml(message);
    }

    // Messages về chương
    if (msg.includes('chương') || msg.includes('chapter')) {
      return '<i class="fas fa-book text-primary"></i> ' + escapeHtml(message);
    }

    // Messages về lỗi
    if (msg.includes('lỗi') || msg.includes('error') || msg.includes('failed')) {
      return '<i class="fas fa-exclamation-triangle text-danger"></i> ' + escapeHtml(message);
    }

    // Messages về thành công
    if (msg.includes('hoàn thành') || msg.includes('completed') || msg.includes('thành công')) {
      return '<i class="fas fa-check-circle text-success"></i> ' + escapeHtml(message);
    }

    return escapeHtml(message);
  }

  /**
   * Get message type from message content
   */
  function getMessageType(message) {
    if (!message) return 'info';
    var msg = message.toLowerCase();
    if (msg.includes('lỗi') || msg.includes('error') || msg.includes('failed')) {
      return 'error';
    } else if (msg.includes('cảnh báo') || msg.includes('warning')) {
      return 'warning';
    }
    return 'info';
  }

  /**
   * Add message to scroll box
   */
  function addMessage(type, message) {
    var messagesBox = $('#modalMessagesScrollBox');

    // Remove "no messages" placeholder
    if (messagesBox.find('.text-muted').length > 0) {
      messagesBox.empty();
    }

    var timestamp = new Date().toLocaleTimeString('vi-VN');
    var messageClass = 'message-' + type;

    // Format message trước khi hiển thị
    var formattedMessage = formatMessage(message);

    var messageItem = $('<div class="message-item ' + messageClass + '">')
      .html('<span class="message-timestamp">[' + timestamp + ']</span> ' + formattedMessage);

    messagesBox.append(messageItem);

    // Auto-scroll to bottom
    messagesBox.scrollTop(messagesBox[0].scrollHeight);
  }


  /**
   * Update chapter progress list
   */
  function updateChapterProgressList(chapterProgress) {
    var container = $('#modalChapterProgressList');
    if (container.length === 0) return;

    // Sort chapters by index
    var chapters = Object.values(chapterProgress).sort(function (a, b) {
      return a.chapterIndex - b.chapterIndex;
    });

    container.empty();

    chapters.forEach(function (chapter) {
      var statusClass = 'badge-secondary';
      var statusText = 'Chờ';
      if (chapter.status === 'downloading') {
        statusClass = 'badge-info';
        statusText = 'Đang tải';
      } else if (chapter.status === 'completed') {
        statusClass = 'badge-success';
        statusText = 'Hoàn thành';
      } else if (chapter.status === 'failed') {
        statusClass = 'badge-danger';
        statusText = 'Thất bại';
      }

      var progressPercent = 0;
      if (chapter.totalImages > 0) {
        progressPercent = Math.round((chapter.downloadedImages / chapter.totalImages) * 100);
      }

      var chapterHtml = '<div class="chapter-item mb-2 p-2 border rounded" data-chapter-index="' + chapter.chapterIndex + '">';
      chapterHtml += '<div class="d-flex justify-content-between align-items-center mb-1">';
      chapterHtml += '<span><strong>Chương ' + (chapter.chapterIndex + 1) + '</strong></span>';
      chapterHtml += '<span class="badge ' + statusClass + '">' + statusText + '</span>';
      chapterHtml += '</div>';
      chapterHtml += '<div class="progress progress-sm">';
      chapterHtml += '<div class="progress-bar ' + (chapter.status === 'downloading' ? 'progress-bar-striped progress-bar-animated bg-info' : 'bg-success') + '" ';
      chapterHtml += 'style="width: ' + progressPercent + '%">' + progressPercent + '%</div>';
      chapterHtml += '</div>';
      chapterHtml += '<div class="mt-1">';
      chapterHtml += '<small class="text-muted">';
      chapterHtml += '<i class="fas fa-images"></i> ';
      chapterHtml += '<strong>' + chapter.downloadedImages + '</strong> / ' + chapter.totalImages + ' hình ảnh';
      if (chapter.totalImages > 0) {
        chapterHtml += ' <span class="badge badge-light">' + progressPercent + '%</span>';
      }
      chapterHtml += '</small>';
      chapterHtml += '</div>';
      chapterHtml += '</div>';

      container.append(chapterHtml);
    });

    // Scroll to active chapter (downloading)
    var activeChapter = container.find('.chapter-item[data-chapter-index]').filter(function () {
      return $(this).find('.badge-info').length > 0;
    }).first();
    if (activeChapter.length > 0) {
      container.scrollTop(activeChapter.position().top + container.scrollTop() - 50);
    }
  }

  /**
   * Start elapsed time timer to update realtime
   */
  function startElapsedTimeTimer() {
    // Clear existing interval if any
    stopElapsedTimeTimer();

    // Update immediately
    updateElapsedTimeRealtime();

    // Update every second
    elapsedTimeInterval = setInterval(function () {
      updateElapsedTimeRealtime();
    }, 1000);
  }

  /**
   * Stop elapsed time timer
   */
  function stopElapsedTimeTimer() {
    if (elapsedTimeInterval) {
      clearInterval(elapsedTimeInterval);
      elapsedTimeInterval = null;
    }
  }

  /**
   * Update elapsed time realtime
   */
  function updateElapsedTimeRealtime() {
    if (!jobStartTime) return;

    var now = Date.now();
    var start = new Date(jobStartTime).getTime();
    var elapsedMs = now - start;
    var elapsedSeconds = Math.floor(elapsedMs / 1000);

    // Format và cập nhật UI
    var hours = Math.floor(elapsedSeconds / 3600);
    var minutes = Math.floor((elapsedSeconds % 3600) / 60);
    var seconds = elapsedSeconds % 60;
    var timeText = '';
    if (hours > 0) timeText += hours + ' giờ ';
    if (minutes > 0) timeText += minutes + ' phút ';
    timeText += seconds + ' giây';

    $('#modalElapsedTime').text(timeText);
  }

  /**
   * Cleanup WebSocket connection
   */
  function cleanup() {
    // Stop all intervals and timers
    stopElapsedTimeTimer();
    stopHeartbeat();

    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }

    reconnectAttempts = 0;

    if (wsSubscription) {
      wsSubscription.unsubscribe();
      wsSubscription = null;
    }

    if (stompClient && stompClient.connected) {
      try {
        stompClient.disconnect();
      } catch (e) {
        console.error('Error disconnecting STOMP client:', e);
      }
    }

    if (socket) {
      try {
        socket.close();
      } catch (e) {
        console.error('Error closing socket:', e);
      }
      socket = null;
    }

    stompClient = null;
    updateConnectionStatus('disconnected', 'Đã ngắt kết nối');
    jobStartTime = null;
    lastProgressUpdateTime = null;
  }

  /**
   * Get status text in Vietnamese
   */
  function getStatusText(status) {
    var statusMap = {
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
   * Escape HTML
   */
  function escapeHtml(text) {
    if (!text) return '';
    var map = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    };
    return String(text).replace(/[&<>"']/g, function (m) {
      return map[m];
    });
  }

  /**
   * Convert MinIO relative path to proxy URL
   * Format: comics/{comicId}/{chapterId}/{imageName}
   * -> /api/images/proxy/{comicId}/{chapterId}/{imageName}
   */
  function convertToProxyUrl(minioPath) {
    if (!minioPath) return minioPath;
    
    // Check if it's already a relative path (format: comics/{comicId}/{chapterId}/{imageName})
    var relativePathPattern = /^comics\/([^\/]+)\/([^\/]+)\/([^\/]+)$/;
    var match = minioPath.match(relativePathPattern);
    
    if (match) {
      var comicId = match[1];
      var chapterId = match[2];
      var imageName = match[3];
      return '/api/images/proxy/' + comicId + '/' + chapterId + '/' + encodeURIComponent(imageName);
    }
    
    // Legacy: Check if it's a full MinIO URL (for backward compatibility)
    var minioPattern = /\/truyengg-images\/comics\/([^\/]+)\/([^\/]+)\/([^\/]+)$/;
    match = minioPath.match(minioPattern);
    if (match) {
      var comicId = match[1];
      var chapterId = match[2];
      var imageName = match[3];
      return '/api/images/proxy/' + comicId + '/' + chapterId + '/' + encodeURIComponent(imageName);
    }
    
    // If not a recognized format, return as is
    return minioPath;
  }

  /**
   * Convert original image URL to proxy URL with referer
   * Format: /api/images/original-proxy/{base64EncodedUrl}?referer={domain}
   */
  function convertOriginalUrlToProxy(originalUrl, referer) {
    if (!originalUrl) return originalUrl;
    
    try {
      // Encode URL to base64 for path parameter
      var encodedUrl = btoa(originalUrl).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
      var proxyUrl = '/api/images/original-proxy/' + encodedUrl;
      if (referer) {
        proxyUrl += '?referer=' + encodeURIComponent(referer);
      }
      return proxyUrl;
    } catch (e) {
      console.error('Error converting original URL to proxy:', e);
      return originalUrl;
    }
  }

  /**
   * Extract domain from URL for referer header
   */
  function extractDomainFromUrl(url) {
    if (!url) return null;
    try {
      var urlObj = new URL(url);
      return urlObj.protocol + '//' + urlObj.host;
    } catch (e) {
      // If URL parsing fails, try simple extraction
      var match = url.match(/^(https?:\/\/[^\/]+)/);
      return match ? match[1] : null;
    }
  }

  /**
   * Render chapter details table
   */
  function renderChapterDetailsTable(details, jobUrl) {
    console.log('job-detail-modal.js: renderChapterDetailsTable called with', details.length, 'chapters');
    var tbody = $('#modalChaptersTableBody');
    tbody.empty();

    if (!details || details.length === 0) {
      tbody.html('<tr><td colspan="12" class="text-center text-muted">Chưa có dữ liệu chapters</td></tr>');
      return;
    }

    // Extract referer from job URL or first chapter URL
    var referer = null;
    if (jobUrl) {
      referer = extractDomainFromUrl(jobUrl);
    } else if (details.length > 0 && details[0].chapterUrl) {
      referer = extractDomainFromUrl(details[0].chapterUrl);
    }

    details.forEach(function (detail) {
      var row = $('<tr>');
      
      // Chapter Index
      row.append($('<td>').text(detail.chapterIndex + 1));
      
      // Chapter Name/URL
      var chapterNameCell = $('<td>');
      if (detail.chapterName) {
        chapterNameCell.text(detail.chapterName);
      } else {
        var urlLink = $('<a>').attr('href', detail.chapterUrl).attr('target', '_blank')
          .text(detail.chapterUrl.length > 50 ? detail.chapterUrl.substring(0, 50) + '...' : detail.chapterUrl);
        chapterNameCell.append(urlLink);
      }
      row.append(chapterNameCell);
      
      // Status
      row.append($('<td>').html(getStatusBadgeForChapter(detail.status, detail.retryCount)));
      
      // Progress
      var progressText = detail.downloadedImages + ' / ' + detail.totalImages;
      if (detail.totalImages > 0) {
        var progressPercent = Math.round((detail.downloadedImages / detail.totalImages) * 100);
        progressText += ' (' + progressPercent + '%)';
      }
      row.append($('<td>').text(progressText));
      
      // Images (MinIO URLs)
      var imagesCell = $('<td>');
      if (detail.imagePaths && detail.imagePaths.length > 0) {
        var imageCount = detail.imagePaths.length;
        var imagePreview = $('<div>').addClass('image-preview-container');
        
        // Show first 3 images as thumbnails
        var previewCount = Math.min(3, imageCount);
        var previewContainer = $('<div>').addClass('d-flex gap-1 mb-1');
        for (var i = 0; i < previewCount; i++) {
          (function(index) {
            var originalPath = detail.imagePaths[index];
            // Convert relative path to proxy URL
            var imgUrl = convertToProxyUrl(originalPath);
            var img = $('<img>')
              .attr('src', imgUrl)
              .attr('alt', 'Image ' + (index + 1))
              .addClass('img-thumbnail')
              .css({
                'width': '40px',
                'height': '40px',
                'object-fit': 'cover',
                'cursor': 'pointer'
              })
              .on('error', function() {
                // If image fails to load (403), show placeholder
                $(this).attr('src', 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjQwIiBoZWlnaHQ9IjQwIiBmaWxsPSIjZGRkIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxMCIgZmlsbD0iIzk5OSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPjQwMzwvdGV4dD48L3N2Zz4=');
                $(this).css('opacity', '0.5');
              })
              .on('click', function() {
                // Convert all paths to proxy URLs for the modal
                var proxyUrls = detail.imagePaths.map(function(path) {
                  return convertToProxyUrl(path);
                });
                showImageModal(imgUrl, proxyUrls, detail.chapterName || 'Chapter ' + (detail.chapterIndex + 1));
              });
            previewContainer.append(img);
          }(i));
        }
        imagePreview.append(previewContainer);
        
        // Show total count with view all button
        // Convert all paths to proxy URLs
        var proxyUrls = detail.imagePaths.map(function(path) {
          return convertToProxyUrl(path);
        });
        var viewAllBtn = $('<button>')
          .addClass('btn btn-sm btn-info')
          .text('Xem tất cả (' + imageCount + ')')
          .on('click', (function(paths, chapterIdx, chapterName) {
            return function() {
              showImageModal(null, paths, chapterName || 'Chapter ' + (chapterIdx + 1));
            };
          })(proxyUrls, detail.chapterIndex, detail.chapterName));
        imagePreview.append(viewAllBtn);
        
        imagesCell.append(imagePreview);
      } else {
        imagesCell.text('-');
      }
      row.append(imagesCell);
      
      // Original Images
      var originalImagesCell = $('<td>');
      if (detail.originalImagePaths && detail.originalImagePaths.length > 0) {
        var originalImageCount = detail.originalImagePaths.length;
        var originalImagePreview = $('<div>').addClass('image-preview-container');
        var originalPreviewContainer = $('<div>').addClass('d-flex gap-1 mb-1');
        
        // Show first 3 original images as thumbnails
        var originalPreviewCount = Math.min(3, originalImageCount);
        for (var j = 0; j < originalPreviewCount; j++) {
          (function(origIndex) {
            var originalUrl = detail.originalImagePaths[origIndex];
            var originalProxyUrl = convertOriginalUrlToProxy(originalUrl, referer);
            var origImg = $('<img>')
              .attr('src', originalProxyUrl)
              .attr('alt', 'Original ' + (origIndex + 1))
              .addClass('img-thumbnail')
              .css({
                'width': '40px',
                'height': '40px',
                'object-fit': 'cover',
                'cursor': 'pointer'
              })
              .on('error', function() {
                $(this).attr('src', 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjQwIiBoZWlnaHQ9IjQwIiBmaWxsPSIjZGRkIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxMCIgZmlsbD0iIzk5OSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPjQwMzwvdGV4dD48L3N2Zz4=');
                $(this).css('opacity', '0.5');
              })
              .on('click', function() {
                var allOriginalProxyUrls = detail.originalImagePaths.map(function(url) {
                  return convertOriginalUrlToProxy(url, referer);
                });
                showImageModal(originalProxyUrl, allOriginalProxyUrls, 'Original Images - ' + (detail.chapterName || 'Chapter ' + (detail.chapterIndex + 1)));
              });
            originalPreviewContainer.append(origImg);
          }(j));
        }
        originalImagePreview.append(originalPreviewContainer);
        
        // Show total count with view all button
        if (originalImageCount > originalPreviewCount) {
          var allOriginalProxyUrls = detail.originalImagePaths.map(function(url) {
            return convertOriginalUrlToProxy(url, referer);
          });
          var viewAllOriginalBtn = $('<button>')
            .addClass('btn btn-sm btn-secondary')
            .text('Xem tất cả (' + originalImageCount + ')')
            .on('click', function() {
              showImageModal(null, allOriginalProxyUrls, 'Original Images - ' + (detail.chapterName || 'Chapter ' + (detail.chapterIndex + 1)));
            });
          originalImagePreview.append(viewAllOriginalBtn);
        }
        originalImagesCell.append(originalImagePreview);
      } else {
        originalImagesCell.text('-');
      }
      row.append(originalImagesCell);
      
      // File Size
      row.append($('<td>').text(detail.fileSizeFormatted || '-'));
      
      // Download Time
      row.append($('<td>').text(detail.downloadTimeFormatted || '-'));
      
      // Download Speed
      row.append($('<td>').text(detail.downloadSpeedFormatted || '-'));
      
      // Retry Count
      var retryCell = $('<td>');
      if (detail.retryCount > 0) {
        retryCell.html('<span class="badge badge-warning">' + detail.retryCount + '</span>');
      } else {
        retryCell.text('0');
      }
      row.append(retryCell);
      
      // Error
      var errorCell = $('<td>');
      if (detail.errorMessages && detail.errorMessages.length > 0) {
        var errorText = detail.errorMessages.join('; ');
        var errorTitle = errorText.length > 100 ? errorText.substring(0, 100) + '...' : errorText;
        var errorBadge = $('<span>')
          .addClass('badge badge-danger')
          .attr('title', errorTitle)
          .text(detail.errorMessages.length + ' lỗi')
          .css('cursor', 'pointer')
          .on('click', function() {
            showErrorModal(detail.errorMessages, detail.chapterName || 'Chapter ' + (detail.chapterIndex + 1));
          });
        errorCell.append(errorBadge);
      } else if (detail.errorCount > 0) {
        errorCell.html('<span class="badge badge-warning">' + detail.errorCount + '</span>');
      } else {
        errorCell.text('-');
      }
      row.append(errorCell);
      
      // Actions
      var actionsCell = $('<td>');
      var viewBtn = $('<button>')
        .addClass('btn btn-sm btn-info')
        .html('<i class="fas fa-eye"></i>')
        .attr('title', 'Xem chi tiết')
        .on('click', function() {
          showChapterDetailModal(detail);
        });
      actionsCell.append(viewBtn);
      row.append(actionsCell);
      
      tbody.append(row);
    });
  }

  /**
   * Render pagination controls for chapter details
   */
  function renderChapterPagination(detailsPage) {
    var paginationContainer = $('#modalChaptersPagination');
    paginationContainer.empty();
    
    if (!detailsPage || detailsPage.totalPages <= 1) {
      return;
    }
    
    var pagination = $('<nav>').attr('aria-label', 'Chapter pagination');
    var ul = $('<ul>').addClass('pagination justify-content-center');
    
    // Previous button
    var prevLi = $('<li>').addClass('page-item');
    if (detailsPage.first) {
      prevLi.addClass('disabled');
    }
    var prevLink = $('<a>').addClass('page-link').attr('href', '#').text('Trước');
    if (!detailsPage.first) {
      prevLink.on('click', function(e) {
        e.preventDefault();
        loadJobDetail(detailsPage.number - 1);
      });
    }
    prevLi.append(prevLink);
    ul.append(prevLi);
    
    // Page numbers
    var startPage = Math.max(0, detailsPage.number - 2);
    var endPage = Math.min(detailsPage.totalPages - 1, detailsPage.number + 2);
    
    if (startPage > 0) {
      var firstLi = $('<li>').addClass('page-item');
      var firstLink = $('<a>').addClass('page-link').attr('href', '#').text('1');
      firstLink.on('click', function(e) {
        e.preventDefault();
        loadJobDetail(0);
      });
      firstLi.append(firstLink);
      ul.append(firstLi);
      
      if (startPage > 1) {
        var ellipsisLi = $('<li>').addClass('page-item disabled');
        ellipsisLi.append($('<span>').addClass('page-link').text('...'));
        ul.append(ellipsisLi);
      }
    }
    
    for (var i = startPage; i <= endPage; i++) {
      var pageLi = $('<li>').addClass('page-item');
      if (i === detailsPage.number) {
        pageLi.addClass('active');
      }
      var pageLink = $('<a>').addClass('page-link').attr('href', '#').text(i + 1);
      if (i !== detailsPage.number) {
        pageLink.on('click', function(e, page) {
          e.preventDefault();
          loadJobDetail(page);
        }.bind(null, null, i));
      }
      pageLi.append(pageLink);
      ul.append(pageLi);
    }
    
    if (endPage < detailsPage.totalPages - 1) {
      if (endPage < detailsPage.totalPages - 2) {
        var ellipsisLi = $('<li>').addClass('page-item disabled');
        ellipsisLi.append($('<span>').addClass('page-link').text('...'));
        ul.append(ellipsisLi);
      }
      
      var lastLi = $('<li>').addClass('page-item');
      var lastLink = $('<a>').addClass('page-link').attr('href', '#').text(detailsPage.totalPages);
      lastLink.on('click', function(e) {
        e.preventDefault();
        loadJobDetail(detailsPage.totalPages - 1);
      });
      lastLi.append(lastLink);
      ul.append(lastLi);
    }
    
    // Next button
    var nextLi = $('<li>').addClass('page-item');
    if (detailsPage.last) {
      nextLi.addClass('disabled');
    }
    var nextLink = $('<a>').addClass('page-link').attr('href', '#').text('Sau');
    if (!detailsPage.last) {
      nextLink.on('click', function(e) {
        e.preventDefault();
        loadJobDetail(detailsPage.number + 1);
      });
    }
    nextLi.append(nextLink);
    ul.append(nextLi);
    
    pagination.append(ul);
    paginationContainer.append(pagination);
    
    // Add page info
    var pageInfo = $('<div>').addClass('text-center text-muted mt-2')
      .text('Trang ' + (detailsPage.number + 1) + ' / ' + detailsPage.totalPages + ' (' + detailsPage.totalElements + ' chapters)');
    paginationContainer.append(pageInfo);
  }

  /**
   * Get status badge for chapter
   */
  function getStatusBadgeForChapter(status, retryCount) {
    var statusClass = 'badge-';
    var statusText = '';
    
    switch (status ? status.toUpperCase() : '') {
      case 'PENDING':
        statusClass += 'secondary';
        statusText = 'Chờ';
        break;
      case 'DOWNLOADING':
        statusClass += 'primary';
        statusText = 'Đang tải';
        break;
      case 'COMPLETED':
        statusClass += 'success';
        statusText = 'Hoàn thành';
        break;
      case 'FAILED':
        statusClass += 'danger';
        statusText = 'Thất bại';
        break;
      default:
        statusClass += 'secondary';
        statusText = status || 'Unknown';
    }
    
    var badge = '<span class="badge ' + statusClass + '">' + statusText + '</span>';
    if (retryCount > 0) {
      badge += ' <span class="badge badge-warning" title="Số lần thử lại">' + retryCount + '</span>';
    }
    return badge;
  }

  /**
   * Render metrics
   */
  function renderMetrics(metrics) {
    if (!metrics) return;
    
    // Update statistics cards if metrics are available
    if (metrics.averageDownloadSpeedFormatted) {
      $('#modalDownloadSpeed').text(metrics.averageDownloadSpeedFormatted);
    }
    if (metrics.totalFileSizeFormatted) {
      $('#modalFileSize').text(metrics.totalFileSizeFormatted);
    }
    if (metrics.totalRequestCount !== undefined && metrics.totalErrorCount !== undefined) {
      $('#modalNetworkStats').text(metrics.totalRequestCount + ' / ' + metrics.totalErrorCount);
    }
  }

  /**
   * Show image modal
   */
  function showImageModal(currentImageUrl, allImages, title) {
    if (!allImages || allImages.length === 0) return;
    
    var currentIndex = currentImageUrl ? allImages.indexOf(currentImageUrl) : 0;
    if (currentIndex === -1) currentIndex = 0;
    
    var modalHtml = '<div class="modal fade" id="imageViewerModal" tabindex="-1" role="dialog" data-backdrop="false" style="z-index: 1060;">' +
      '<div class="modal-dialog modal-xl" role="document">' +
      '<div class="modal-content">' +
      '<div class="modal-header">' +
      '<h5 class="modal-title">' + (title || 'Hình ảnh') + ' (<span id="imageCounter"></span>)</h5>' +
      '<button type="button" class="close" data-dismiss="modal"><span>&times;</span></button>' +
      '</div>' +
      '<div class="modal-body text-center" style="position: relative;">' +
      '<div id="imageLoadingSpinner" class="spinner-border text-primary" role="status" style="display: none; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 10;">' +
      '<span class="sr-only">Loading...</span>' +
      '</div>' +
      '<img id="modalImage" src="" class="img-fluid" style="max-height: 70vh;" onerror="this.onerror=null; this.src=\'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgZmlsbD0iI2RkZCIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTQiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5JbWFnZSBub3QgYXZhaWxhYmxlPC90ZXh0Pjwvc3ZnPg==\';" />' +
      '</div>' +
      '<div class="modal-footer justify-content-between">' +
      '<button type="button" class="btn btn-secondary" id="prevImage"><i class="fas fa-chevron-left"></i> Trước</button>' +
      '<div><span id="imageIndex"></span> / <span id="imageTotal"></span></div>' +
      '<button type="button" class="btn btn-secondary" id="nextImage">Sau <i class="fas fa-chevron-right"></i></button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
    
    // Remove any existing image viewer modal and backdrop
    $('#imageViewerModal').remove();
    $('.modal-backdrop').not('#jobDetailModal').remove();
    
    $('body').append(modalHtml);
    var modal = $('#imageViewerModal');
    var modalImage = $('#modalImage');
    var loadingSpinner = $('#imageLoadingSpinner');
    var currentImageObj = null; // Track current Image object to cancel if needed
    var updateImageTimeout = null; // For debouncing rapid clicks
    var preloadedImages = {}; // Cache for preloaded images: {url: Image object}
    var preloadQueue = []; // Queue of images to preload
    
    // Preload images in the background
    function preloadAdjacentImages(currentIdx) {
      // Preload next 2 and previous 2 images
      var indicesToPreload = [];
      for (var i = 1; i <= 2; i++) {
        var nextIdx = (currentIdx + i) % allImages.length;
        var prevIdx = (currentIdx - i + allImages.length) % allImages.length;
        indicesToPreload.push(nextIdx, prevIdx);
      }
      
      indicesToPreload.forEach(function(idx) {
        var url = allImages[idx];
        // Only preload if not already cached
        if (!preloadedImages[url]) {
          var img = new Image();
          preloadedImages[url] = img;
          img.src = url; // Start loading in background
        }
      });
    }
    
    function updateImage(index) {
      // Clear any pending update
      if (updateImageTimeout) {
        clearTimeout(updateImageTimeout);
        updateImageTimeout = null;
      }
      
      if (index < 0) index = allImages.length - 1;
      if (index >= allImages.length) index = 0;
      currentIndex = index;
      
      var imageUrl = allImages[currentIndex];
      
      // Cancel previous image load by setting src to empty (browser will cancel the request)
      if (currentImageObj) {
        currentImageObj.src = '';
        currentImageObj = null;
      }
      modalImage.attr('src', '');
      
      // Update counter
      $('#imageIndex').text(currentIndex + 1);
      $('#imageTotal').text(allImages.length);
      $('#imageCounter').text((currentIndex + 1) + ' / ' + allImages.length + ' ảnh');
      
      // Check if image is already preloaded
      var preloadedImg = preloadedImages[imageUrl];
      if (preloadedImg && preloadedImg.complete) {
        // Image already loaded from cache/preload - show immediately
        modalImage.attr('src', imageUrl);
        loadingSpinner.hide();
        modalImage.show();
        // Preload adjacent images in background
        preloadAdjacentImages(currentIndex);
        return;
      }
      
      // Show loading spinner
      loadingSpinner.show();
      modalImage.hide();
      
      // Use preloaded image if available, otherwise create new
      var img = preloadedImg || new Image();
      if (!preloadedImg) {
        preloadedImages[imageUrl] = img;
      }
      currentImageObj = img;
      
      img.onload = function() {
        // Only update if this is still the current image
        if (currentImageObj === img) {
          modalImage.attr('src', imageUrl);
          loadingSpinner.hide();
          modalImage.show();
          currentImageObj = null;
          // Preload adjacent images after current image loads
          preloadAdjacentImages(currentIndex);
        }
      };
      
      img.onerror = function() {
        // Only update if this is still the current image
        if (currentImageObj === img) {
          modalImage.attr('src', 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgZmlsbD0iI2RkZCIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LWZhbWlseT0iQXJpYWwiIGZvbnQtc2l6ZT0iMTQiIGZpbGw9IiM5OTkiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGR5PSIuM2VtIj5JbWFnZSBub3QgYXZhaWxhYmxlPC90ZXh0Pjwvc3ZnPg==');
          loadingSpinner.hide();
          modalImage.show();
          currentImageObj = null;
        }
      };
      
      // Start loading if not already loaded
      if (!img.complete) {
        img.src = imageUrl;
      } else {
        // Already complete, trigger onload manually
        img.onload();
      }
    }
    
    // Debounced update function to prevent rapid clicking
    function debouncedUpdateImage(index) {
      if (updateImageTimeout) {
        clearTimeout(updateImageTimeout);
      }
      updateImageTimeout = setTimeout(function() {
        updateImage(index);
        updateImageTimeout = null;
      }, 50); // Reduced to 50ms for faster response
    }
    
    updateImage(currentIndex);
    
    if (allImages.length <= 1) {
      $('#prevImage').prop('disabled', true);
      $('#nextImage').prop('disabled', true);
    } else {
      $('#prevImage').prop('disabled', false);
      $('#nextImage').prop('disabled', false);
    }
    
    // Keyboard navigation for faster switching
    $(document).on('keydown.imageModal', function(e) {
      if (modal.is(':visible')) {
        if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
          e.preventDefault();
          debouncedUpdateImage(currentIndex - 1);
        } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
          e.preventDefault();
          debouncedUpdateImage(currentIndex + 1);
        }
      }
    });
    
    $('#prevImage').on('click', function() {
      debouncedUpdateImage(currentIndex - 1);
    });
    
    $('#nextImage').on('click', function() {
      debouncedUpdateImage(currentIndex + 1);
    });
    
    modal.on('show.bs.modal', function() {
      // Prevent body scroll lock by keeping parent modal scrollable
      $('body').addClass('modal-open');
      $('#jobDetailModal').css('overflow-y', 'auto');
    });
    
    modal.modal('show');
    
    modal.on('hidden.bs.modal', function() {
      // Cleanup keyboard navigation
      $(document).off('keydown.imageModal');
      // Clear preload cache to free memory
      preloadedImages = {};
      preloadQueue = [];
      // Restore parent modal scroll
      $('#jobDetailModal').css('overflow-y', '');
      modal.remove();
      // Only remove backdrop if no other modal is open
      if (!$('.modal.show').length) {
        $('.modal-backdrop').remove();
        $('body').removeClass('modal-open');
        $('body').css('padding-right', '');
      }
    });
  }

  /**
   * Show error modal
   */
  function showErrorModal(errorMessages, title) {
    var errorList = errorMessages.map(function(msg, idx) {
      return '<li>' + escapeHtml(msg) + '</li>';
    }).join('');
    
    var modalHtml = '<div class="modal fade" id="errorModal" tabindex="-1" role="dialog" data-backdrop="static" style="z-index: 1060;">' +
      '<div class="modal-dialog" role="document">' +
      '<div class="modal-content">' +
      '<div class="modal-header bg-danger text-white">' +
      '<h5 class="modal-title">Lỗi - ' + escapeHtml(title) + '</h5>' +
      '<button type="button" class="close text-white" data-dismiss="modal"><span>&times;</span></button>' +
      '</div>' +
      '<div class="modal-body">' +
      '<ul class="list-unstyled">' + errorList + '</ul>' +
      '</div>' +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary" data-dismiss="modal">Đóng</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
    
    // Remove any existing error modal
    $('#errorModal').remove();
    
    $('body').append(modalHtml);
    var modal = $('#errorModal');
    modal.on('show.bs.modal', function() {
      // Prevent body scroll lock by keeping parent modal scrollable
      $('body').addClass('modal-open');
      $('#jobDetailModal').css('overflow-y', 'auto');
    });
    
    modal.modal('show');
    
    modal.on('hidden.bs.modal', function() {
      // Restore parent modal scroll
      $('#jobDetailModal').css('overflow-y', '');
      modal.remove();
      // Only remove backdrop if no other modal is open
      if (!$('.modal.show').length) {
        $('.modal-backdrop').remove();
        $('body').removeClass('modal-open');
        $('body').css('padding-right', '');
      }
    });
  }

  /**
   * Show chapter detail modal
   */
  function showChapterDetailModal(detail) {
    var detailHtml = '<div class="modal fade" id="chapterDetailModal" tabindex="-1" role="dialog" data-backdrop="static" style="z-index: 1060;">' +
      '<div class="modal-dialog modal-lg" role="document">' +
      '<div class="modal-content">' +
      '<div class="modal-header">' +
      '<h5 class="modal-title">Chi tiết Chapter ' + (detail.chapterIndex + 1) + '</h5>' +
      '<button type="button" class="close" data-dismiss="modal"><span>&times;</span></button>' +
      '</div>' +
      '<div class="modal-body">' +
      '<table class="table table-sm">' +
      '<tr><th>Chapter Index:</th><td>' + (detail.chapterIndex + 1) + '</td></tr>' +
      '<tr><th>Tên:</th><td>' + escapeHtml(detail.chapterName || '-') + '</td></tr>' +
      '<tr><th>URL:</th><td><a href="' + escapeHtml(detail.chapterUrl) + '" target="_blank">' + escapeHtml(detail.chapterUrl) + '</a></td></tr>' +
      '<tr><th>Trạng thái:</th><td>' + getStatusBadgeForChapter(detail.status, detail.retryCount) + '</td></tr>' +
      '<tr><th>Tiến độ:</th><td>' + detail.downloadedImages + ' / ' + detail.totalImages + '</td></tr>' +
      '<tr><th>Kích thước:</th><td>' + (detail.fileSizeFormatted || '-') + '</td></tr>' +
      '<tr><th>Thời gian tải:</th><td>' + (detail.downloadTimeFormatted || '-') + '</td></tr>' +
      '<tr><th>Tốc độ:</th><td>' + (detail.downloadSpeedFormatted || '-') + '</td></tr>' +
      '<tr><th>Requests:</th><td>' + (detail.requestCount || 0) + '</td></tr>' +
      '<tr><th>Errors:</th><td>' + (detail.errorCount || 0) + '</td></tr>' +
      '<tr><th>Retry:</th><td>' + (detail.retryCount || 0) + '</td></tr>' +
      '<tr><th>Bắt đầu:</th><td>' + (detail.startedAt ? formatDateTime(detail.startedAt) : '-') + '</td></tr>' +
      '<tr><th>Hoàn thành:</th><td>' + (detail.completedAt ? formatDateTime(detail.completedAt) : '-') + '</td></tr>' +
      '</table>' +
      '</div>' +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary" data-dismiss="modal">Đóng</button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
    
    // Remove any existing chapter detail modal
    $('#chapterDetailModal').remove();
    
    $('body').append(detailHtml);
    var modal = $('#chapterDetailModal');
    modal.modal('show');
    modal.on('show.bs.modal', function() {
      // Prevent body scroll lock by keeping parent modal scrollable
      $('body').addClass('modal-open');
      $('#jobDetailModal').css('overflow-y', 'auto');
    });
    
    modal.on('hidden.bs.modal', function() {
      // Restore parent modal scroll
      $('#jobDetailModal').css('overflow-y', '');
      modal.remove();
      // Only remove backdrop if no other modal is open
      if (!$('.modal.show').length) {
        $('.modal-backdrop').remove();
        $('body').removeClass('modal-open');
        $('body').css('padding-right', '');
      }
    });
  }

  // Cleanup when modal is closed
  $('#jobDetailModal').on('hidden.bs.modal', function () {
    cleanup();
  });

  return {
    show: show
  };
})();
