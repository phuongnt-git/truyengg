/**
 * Crawl Job Detail Module
 * Handles real-time progress updates via WebSocket for crawl job detail page
 */
console.log('crawl-detail.js: File loaded, starting module...');

(function () {
  console.log('crawl-detail.js: IIFE started');
  
  var jobId = null;
  var stompClient = null;
  var socket = null;
  var wsSubscription = null;
  
  try {

  // WebSocket connection state
  var wsConnectionState = 'disconnected';
  var reconnectAttempts = 0;
  var maxReconnectAttempts = 10;
  var reconnectDelay = 1000;
  var reconnectTimer = null;
  var heartbeatInterval = null;
  var lastHeartbeatTime = null;

  // Statistics tracking
  var progressHistory = [];
  var lastUpdateTime = null;
  var lastDownloadedImages = 0;
  var lastElapsedSeconds = 0;

  // Charts
  var progressChart = null;
  var speedChart = null;

  /**
   * Initialize page
   */
  function init() {
    console.log('=== crawl-detail.js init() called ===');
    console.log('Current URL:', window.location.pathname);
    console.log('Current href:', window.location.href);
    
    // Get jobId from URL
    var pathParts = window.location.pathname.split('/');
    jobId = pathParts[pathParts.length - 1];
    
    console.log('Extracted jobId:', jobId);

    if (!jobId) {
      console.error('Job ID not found in URL');
      showError('Không tìm thấy Job ID');
      return;
    }

    console.log('Initializing charts...');
    // Initialize charts
    initCharts();

    console.log('Loading job detail for jobId:', jobId);
    // Load job detail
    loadJobDetail();

    // Bind action buttons
    $('#btnPause').on('click', function () {
      pauseJob();
    });

    $('#btnResume').on('click', function () {
      resumeJob();
    });

    $('#btnCancel').on('click', function () {
      cancelJob();
    });

    $('#btnRetry').on('click', function () {
      retryJob();
    });

    // Cleanup on page unload
    $(window).on('beforeunload', function () {
      cleanupWebSocket();
    });
  }

  /**
   * Initialize Chart.js charts
   */
  function initCharts() {
    // Progress Chart
    var progressCtx = document.getElementById('progressChart');
    if (progressCtx) {
      progressChart = new Chart(progressCtx, {
        type: 'line',
        data: {
          labels: [],
          datasets: [
            {
              label: 'Tiến độ chương (%)',
              data: [],
              borderColor: 'rgb(0, 123, 255)',
              backgroundColor: 'rgba(0, 123, 255, 0.1)',
              tension: 0.4
            },
            {
              label: 'Tiến độ hình ảnh (%)',
              data: [],
              borderColor: 'rgb(40, 167, 69)',
              backgroundColor: 'rgba(40, 167, 69, 0.1)',
              tension: 0.4
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: {
              beginAtZero: true,
              max: 100
            }
          },
          plugins: {
            legend: {
              display: true
            }
          }
        }
      });
    }

    // Speed Chart
    var speedCtx = document.getElementById('speedChart');
    if (speedCtx) {
      speedChart = new Chart(speedCtx, {
        type: 'line',
        data: {
          labels: [],
          datasets: [{
            label: 'Tốc độ tải (images/s)',
            data: [],
            borderColor: 'rgb(255, 193, 7)',
            backgroundColor: 'rgba(255, 193, 7, 0.1)',
            tension: 0.4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            y: {
              beginAtZero: true
            }
          },
          plugins: {
            legend: {
              display: true
            }
          }
        }
      });
    }
  }

  /**
   * Load job detail from API
   */
  function loadJobDetail() {
      ApiClient.get('/api/admin/comic-crawls/' + jobId, null, true)
      .done(function (response) {
        console.log('API Response:', response);
        if (response.success && response.data) {
          var jobDetail = response.data;
          console.log('Job Detail:', jobDetail);
          console.log('Crawl:', jobDetail.crawl);
          console.log('Details:', jobDetail.details);
          console.log('Metrics:', jobDetail.metrics);
          console.log('Current Progress:', jobDetail.currentProgress);
          
          renderJobInfo(jobDetail);

          // If crawl is RUNNING or PAUSED, start WebSocket connection
          if (jobDetail.crawl && (jobDetail.crawl.status === 'RUNNING' || jobDetail.crawl.status === 'PAUSED')) {
            startWebSocketConnection(jobId);
          } else if (jobDetail.currentProgress) {
            // Show initial progress if available
            updateProgressUI(jobDetail.currentProgress);
          }

          // Show content
          $('#loadingIndicator').hide();
          $('#jobDetailContent').show();
        } else {
          showError('Không thể tải thông tin job: ' + (response.message || 'Unknown error'));
        }
      })
      .fail(function (xhr) {
        showError('Lỗi khi tải thông tin job: ' + xhr.status);
      });
  }

  /**
   * Update WebSocket connection status UI
   */
  function updateConnectionStatus(state, message) {
    wsConnectionState = state;
    var statusBadge = $('#wsConnectionStatus');
    if (statusBadge.length === 0) {
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
        if (lastUpdateTime && (now - lastUpdateTime) > 30000) {
          console.warn('No progress update received in 30 seconds, connection may be stale');
          scheduleReconnect(jobId);
        }
      } else {
        stopHeartbeat();
      }
    }, 10000);
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
    var delay = Math.min(reconnectDelay * Math.pow(2, reconnectAttempts - 1), 30000);

    console.log('Scheduling reconnection attempt ' + reconnectAttempts + ' in ' + delay + 'ms');
    updateConnectionStatus('connecting', 'Đang thử kết nối lại... (' + reconnectAttempts + '/' + maxReconnectAttempts + ')');

    var jobIdToReconnect = jobId;
    reconnectTimer = setTimeout(function () {
      if (jobId === jobIdToReconnect) {
        startWebSocketConnection(jobIdToReconnect, true);
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

    console.log('Starting WebSocket connection for job:', jobIdStr, isReconnect ? '(reconnect attempt ' + reconnectAttempts + ')' : '');

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
      cleanupWebSocket();
    } else if (stompClient && stompClient.connected) {
      return; // Already connected
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

        if (jobId === jobIdStr && wsConnectionState !== 'disconnected') {
          scheduleReconnect(jobIdStr);
        }
      };

      socket.onerror = function (error) {
        console.error('SockJS error:', error);
        updateConnectionStatus('error', 'Lỗi SockJS');
      };

      stompClient = Stomp.over(socket);
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

      // Connect with JWT token
      var headers = {
        'Authorization': 'Bearer ' + accessToken,
        'token': accessToken
      };

      console.log('Attempting STOMP connection with headers:', Object.keys(headers));
      stompClient.connect(
        headers,
        function (frame) {
          console.log('STOMP connection successful, frame:', frame);
          reconnectAttempts = 0;
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

          startHeartbeat();

          // Subscribe to progress updates
          var destination = '/topic/crawl-progress/' + jobIdStr;
          console.log('Subscribing to:', destination);
          console.log('STOMP client connected state:', stompClient.connected);

          try {
            wsSubscription = stompClient.subscribe(destination, function (message) {
              console.log('✓ WebSocket message received for job:', jobIdStr);
              console.log('Message body:', message.body);
              lastHeartbeatTime = Date.now();

              try {
                var progress = JSON.parse(message.body);
                console.log('Progress update received:', progress.status, 'chapter:', progress.currentChapter + '/' + progress.totalChapters);
                updateProgressUI(progress);

                // Handle job completion/failure/pause
                if (progress.status === 'completed') {
                  addMessage('info', 'Job đã hoàn thành!');
                  stopHeartbeat();
                  cleanupWebSocket();
                  updateActionButtons('COMPLETED');
                  loadJobDetail();
                } else if (progress.status === 'failed') {
                  addMessage('error', 'Job đã thất bại!');
                  stopHeartbeat();
                  cleanupWebSocket();
                  updateActionButtons('FAILED');
                  loadJobDetail();
                } else if (progress.status === 'paused') {
                  addMessage('warning', 'Job đã được tạm dừng');
                  cleanupWebSocket();
                  updateActionButtons('PAUSED');
                  loadJobDetail();
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
   * Cleanup WebSocket connection
   */
  function cleanupWebSocket() {
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
    }

    stompClient = null;
    socket = null;
    updateConnectionStatus('disconnected', 'Đã ngắt kết nối');
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
    var chapterPercent = progress.totalChapters > 0 ?
      (progress.currentChapter / progress.totalChapters * 100) : 0;
    var imagePercent = progress.totalImages > 0 ?
      (progress.downloadedImages / progress.totalImages * 100) : 0;

    progressHistory.push({
      timestamp: now,
      chapterPercent: chapterPercent,
      imagePercent: imagePercent,
      speed: stats.speed
    });

    // Keep only last 200 points
    if (progressHistory.length > 200) {
      progressHistory.shift();
    }

    lastUpdateTime = now;
    lastDownloadedImages = progress.downloadedImages;
    lastElapsedSeconds = progress.elapsedSeconds || 0;

    return stats;
  }

  /**
   * Update statistics UI
   */
  function updateStatisticsUI(stats) {
    // Download Speed
    var speedText = stats.speed > 0 ? stats.speed.toFixed(2) + ' images/s' : '0 images/s';
    $('#downloadSpeed').text(speedText);

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
    $('#eta').text(etaText);

    // File Size
    var fileSizeText = formatFileSize(stats.estimatedFileSize);
    if (stats.totalEstimatedFileSize > 0) {
      fileSizeText += ' / ' + formatFileSize(stats.totalEstimatedFileSize);
    }
    $('#fileSize').text(fileSizeText);

    // Network Stats
    var networkText = stats.networkStats.requests + ' / ' + stats.networkStats.errors;
    if (stats.networkStats.retries > 0) {
      networkText += ' (' + stats.networkStats.retries + ' retries)';
    }
    $('#networkStats').text(networkText);
  }

  /**
   * Render metrics from backend
   */
  function renderMetrics(metrics) {
    if (!metrics) return;

    // Update statistics cards with backend metrics
    if (metrics.averageDownloadSpeedFormatted) {
      $('#downloadSpeed').text(metrics.averageDownloadSpeedFormatted);
    }
    if (metrics.totalDownloadTimeFormatted) {
      $('#totalDownloadTime').text(metrics.totalDownloadTimeFormatted);
    }
    if (metrics.totalFileSizeFormatted) {
      $('#fileSize').text(metrics.totalFileSizeFormatted);
    }
    if (metrics.totalRequestCount !== null && metrics.totalErrorCount !== null) {
      $('#networkStats').text(metrics.totalRequestCount + ' / ' + metrics.totalErrorCount);
    }

    // Render retry statistics
    if (metrics.maxRetryCount > 0 || metrics.chaptersWithRetry > 0 || metrics.failedChaptersCount > 0) {
      $('#retryStatsCard').show();
      $('#maxRetryCount').text(metrics.maxRetryCount || 0);
      $('#avgRetryCount').text(metrics.avgRetryCount ? metrics.avgRetryCount.toFixed(2) : '0.00');
      $('#chaptersWithRetry').text(metrics.chaptersWithRetry || 0);
      $('#failedChaptersCount').text(metrics.failedChaptersCount || 0);
    } else {
      $('#retryStatsCard').hide();
    }
  }

  /**
   * Render chapter details table
   */
  function renderChapterDetailsTable(details) {
    try {
      console.log('renderChapterDetailsTable called with:', details);
      var tbody = $('#chaptersTableBody');
      
      if (tbody.length === 0) {
        console.error('Table body #chaptersTableBody not found!');
        return;
      }
      
      tbody.empty();

      if (!details || details.length === 0) {
        console.log('No details to render');
        tbody.html('<tr><td colspan="11" class="text-center text-muted">Chưa có dữ liệu chapters</td></tr>');
        return;
      }

      console.log('Rendering ' + details.length + ' chapter details');

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
        var imagePathsArray = detail.imagePaths; // Store reference for closure
        for (var i = 0; i < previewCount; i++) {
          (function(index) {
            var imgPath = imagePathsArray[index];
            var imgUrl = convertToProxyUrl ? convertToProxyUrl(imgPath) : imgPath;
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
              .on('click', function() {
                var proxyUrls = imagePathsArray.map(function(path) {
                  return convertToProxyUrl ? convertToProxyUrl(path) : path;
                });
                showImageModal(imgUrl, proxyUrls);
              });
            previewContainer.append(img);
          }(i));
        }
        imagePreview.append(previewContainer);
        
        // Show total count with view all button
        var proxyUrls = detail.imagePaths.map(function(path) {
          return convertToProxyUrl ? convertToProxyUrl(path) : path;
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
    
    console.log('Successfully rendered ' + details.length + ' chapters');
    } catch (error) {
      console.error('Error rendering chapter details table:', error);
      console.error('Error stack:', error.stack);
      var tbody = $('#chaptersTableBody');
      if (tbody.length > 0) {
        tbody.html('<tr><td colspan="11" class="text-center text-danger">Lỗi khi hiển thị chapters: ' + escapeHtml(error.message) + '</td></tr>');
      }
    }
  }
  
  /**
   * Show image modal
   */
  function showImageModal(currentImageUrl, allImages, title) {
    if (!allImages || allImages.length === 0) return;
    
    var currentIndex = currentImageUrl ? allImages.indexOf(currentImageUrl) : 0;
    if (currentIndex === -1) currentIndex = 0;
    
    var modalHtml = '<div class="modal fade" id="imageViewerModal" tabindex="-1" role="dialog">' +
      '<div class="modal-dialog modal-xl" role="document">' +
      '<div class="modal-content">' +
      '<div class="modal-header">' +
      '<h5 class="modal-title">' + (title || 'Hình ảnh') + ' (' + allImages.length + ' ảnh)</h5>' +
      '<button type="button" class="close" data-dismiss="modal"><span>&times;</span></button>' +
      '</div>' +
      '<div class="modal-body text-center" style="position: relative;">' +
      '<div id="imageLoadingSpinner" class="spinner-border text-primary" role="status" style="display: none; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 10;">' +
      '<span class="sr-only">Loading...</span>' +
      '</div>' +
      '<img id="modalImage" src="" class="img-fluid" style="max-height: 70vh;">' +
      '</div>' +
      '<div class="modal-footer">' +
      '<button type="button" class="btn btn-secondary" id="prevImage"><i class="fas fa-chevron-left"></i> Trước</button>' +
      '<span id="imageCounter">' + (currentIndex + 1) + ' / ' + allImages.length + '</span>' +
      '<button type="button" class="btn btn-secondary" id="nextImage">Sau <i class="fas fa-chevron-right"></i></button>' +
      '</div>' +
      '</div>' +
      '</div>' +
      '</div>';
    
    $('#imageViewerModal').remove();
    $('body').append(modalHtml);
    
    var modal = $('#imageViewerModal');
    var modalImage = $('#modalImage');
    var imageCounter = $('#imageCounter');
    var loadingSpinner = $('#imageLoadingSpinner');
    var currentImgIndex = currentIndex;
    var currentImageObj = null; // Track current Image object to cancel if needed
    var updateImageTimeout = null; // For debouncing rapid clicks
    var preloadedImages = {}; // Cache for preloaded images: {url: Image object}
    
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
      currentImgIndex = index;
      
      var imageUrl = allImages[currentImgIndex];
      
      // Cancel previous image load by setting src to empty (browser will cancel the request)
      if (currentImageObj) {
        currentImageObj.src = '';
        currentImageObj = null;
      }
      modalImage.attr('src', '');
      
      // Update counter
      imageCounter.text((currentImgIndex + 1) + ' / ' + allImages.length);
      $('#prevImage').prop('disabled', allImages.length <= 1);
      $('#nextImage').prop('disabled', allImages.length <= 1);
      
      // Check if image is already preloaded
      var preloadedImg = preloadedImages[imageUrl];
      if (preloadedImg && preloadedImg.complete) {
        // Image already loaded from cache/preload - show immediately
        modalImage.attr('src', imageUrl);
        loadingSpinner.hide();
        modalImage.show();
        // Preload adjacent images in background
        preloadAdjacentImages(currentImgIndex);
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
          preloadAdjacentImages(currentImgIndex);
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
    
    // Keyboard navigation for faster switching
    $(document).on('keydown.imageModal', function(e) {
      if (modal.is(':visible')) {
        if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
          e.preventDefault();
          debouncedUpdateImage(currentImgIndex - 1);
        } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
          e.preventDefault();
          debouncedUpdateImage(currentImgIndex + 1);
        }
      }
    });
    
    $('#prevImage').on('click', function() {
      debouncedUpdateImage(currentImgIndex - 1);
    });
    
    $('#nextImage').on('click', function() {
      debouncedUpdateImage(currentImgIndex + 1);
    });
    
    modal.modal('show');
    
    modal.on('hidden.bs.modal', function() {
      // Cleanup keyboard navigation
      $(document).off('keydown.imageModal');
      // Clear preload cache to free memory
      preloadedImages = {};
      modal.remove();
    });
  }
  
  /**
   * Show error modal
   */
  function showErrorModal(errorMessages, title) {
    var errorList = errorMessages.map(function(msg, idx) {
      return '<li>' + escapeHtml(msg) + '</li>';
    }).join('');
    
    var modalHtml = '<div class="modal fade" id="errorModal" tabindex="-1" role="dialog">' +
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
    
    $('#errorModal').remove();
    $('body').append(modalHtml);
    $('#errorModal').modal('show');
    $('#errorModal').on('hidden.bs.modal', function() {
      $('#errorModal').remove();
    });
  }
  
  /**
   * Show chapter detail modal
   */
  function showChapterDetailModal(detail) {
    var detailHtml = '<div class="modal fade" id="chapterDetailModal" tabindex="-1" role="dialog">' +
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
    
    $('#chapterDetailModal').remove();
    $('body').append(detailHtml);
    $('#chapterDetailModal').modal('show');
    $('#chapterDetailModal').on('hidden.bs.modal', function() {
      $('#chapterDetailModal').remove();
    });
  }
  
  /**
   * Escape HTML to prevent XSS
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
    return String(text).replace(/[&<>"']/g, function(m) { return map[m]; });
  }

  /**
   * Get status badge for chapter
   */
  function getStatusBadgeForChapter(status, retryCount) {
    var statusClass = 'badge-';
    var statusText = '';
    
    switch (status) {
      case 'pending':
        statusClass += 'secondary';
        statusText = 'Chờ';
        break;
      case 'downloading':
        statusClass += 'primary';
        statusText = 'Đang tải';
        break;
      case 'completed':
        statusClass += 'success';
        statusText = 'Hoàn thành';
        break;
      case 'failed':
        statusClass += 'danger';
        statusText = 'Thất bại';
        break;
      default:
        statusClass += 'secondary';
        statusText = status;
    }
    
    var badge = '<span class="badge ' + statusClass + '">' + statusText + '</span>';
    if (retryCount > 0) {
      badge += ' <span class="badge badge-warning" title="Retried ' + retryCount + ' time(s)">R' + retryCount + '</span>';
    }
    
    return badge;
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
   * Format duration from seconds
   */
  function formatDuration(seconds) {
    if (!seconds || seconds === 0) {
      return '0s';
    }
    var hours = Math.floor(seconds / 3600);
    var minutes = Math.floor((seconds % 3600) / 60);
    var secs = seconds % 60;
    
    if (hours > 0) {
      return hours + 'h ' + minutes + 'm ' + secs + 's';
    } else if (minutes > 0) {
      return minutes + 'm ' + secs + 's';
    } else {
      return secs + 's';
    }
  }

  /**
   * Update charts with new data
   */
  function updateCharts() {
    if (progressHistory.length === 0) return;

    // Update Progress Chart
    if (progressChart) {
      var labels = progressHistory.map(function (item, index) {
        var minutes = Math.floor((Date.now() - item.timestamp) / 60000);
        return minutes + 'm ago';
      });
      progressChart.data.labels = labels;
      progressChart.data.datasets[0].data = progressHistory.map(function (item) {
        return item.chapterPercent;
      });
      progressChart.data.datasets[1].data = progressHistory.map(function (item) {
        return item.imagePercent;
      });
      progressChart.update('none'); // 'none' mode for smooth updates
    }

    // Update Speed Chart
    if (speedChart) {
      var speedLabels = progressHistory.map(function (item, index) {
        var minutes = Math.floor((Date.now() - item.timestamp) / 60000);
        return minutes + 'm ago';
      });
      speedChart.data.labels = speedLabels;
      speedChart.data.datasets[0].data = progressHistory.map(function (item) {
        return item.speed;
      });
      speedChart.update('none');
    }
  }

  /**
   * Render job information
   */
  function renderJobInfo(jobDetail) {
    console.log('renderJobInfo called with:', jobDetail);
    
    if (!jobDetail) {
      console.error('jobDetail is null or undefined');
      return;
    }
    
    var crawl = jobDetail.crawl;
    if (!crawl) {
      console.error('crawl is null or undefined in jobDetail:', jobDetail);
      return;
    }

    console.log('Rendering crawl info:', crawl);

    // Crawl Info
    $('#jobId').text(crawl.id || '-');
    $('#jobStatus').html(getStatusBadge(crawl.status));
    $('#jobSource').text('-'); // Source field removed from ComicCrawlResponse
    $('#jobUrl').attr('href', crawl.url || '#').text(crawl.url || '-');
    $('#jobCreatedBy').text(crawl.createdByUsername || '-');
    $('#jobStartTime').text(crawl.startTime ? formatDateTime(crawl.startTime) : '-');
    $('#jobEndTime').text(crawl.endTime ? formatDateTime(crawl.endTime) : '-');
    $('#jobTotalChapters').text(crawl.totalChapters || 0);

    // Checkpoint Info
    if (jobDetail.checkpoint) {
      var checkpoint = jobDetail.checkpoint;
      $('#checkpointChapterIndex').text((checkpoint.currentChapterIndex || 0) + 1);
      if (checkpoint.pausedAt) {
        $('#checkpointPausedAt').text(formatDateTime(checkpoint.pausedAt));
      } else {
        $('#checkpointPausedAt').text('-');
      }
      $('#checkpointReason').text(checkpoint.pauseReason || '-');
      $('#checkpointInfo').show();
    } else {
      $('#checkpointInfo').hide();
    }

    // Render metrics if available
    if (jobDetail.metrics) {
      console.log('Rendering metrics:', jobDetail.metrics);
      renderMetrics(jobDetail.metrics);
    } else {
      console.log('No metrics found in jobDetail');
    }

    // Render chapter details if available
    console.log('Checking details:', jobDetail.details);
    console.log('Details type:', typeof jobDetail.details, 'isArray:', Array.isArray(jobDetail.details));
    console.log('Details length:', jobDetail.details ? jobDetail.details.length : 'null/undefined');
    
    var tableBody = $('#chaptersTableBody');
    console.log('Table body element found:', tableBody.length > 0);
    
    if (jobDetail.details && Array.isArray(jobDetail.details) && jobDetail.details.length > 0) {
      console.log('Calling renderChapterDetailsTable with ' + jobDetail.details.length + ' chapters');
      try {
        renderChapterDetailsTable(jobDetail.details);
      } catch (error) {
        console.error('Error calling renderChapterDetailsTable:', error);
        if (tableBody.length > 0) {
          tableBody.html('<tr><td colspan="11" class="text-center text-danger">Lỗi: ' + escapeHtml(error.message) + '</td></tr>');
        }
      }
    } else {
      console.log('No details found or empty array, showing empty message');
      if (tableBody.length > 0) {
        tableBody.html('<tr><td colspan="11" class="text-center text-muted">Chưa có dữ liệu chapters</td></tr>');
      } else {
        console.error('Table body #chaptersTableBody not found in DOM!');
      }
    }
    
    // Render messages/logs if available from currentProgress
    if (jobDetail.currentProgress && jobDetail.currentProgress.messages && jobDetail.currentProgress.messages.length > 0) {
      console.log('Rendering messages from currentProgress:', jobDetail.currentProgress.messages.length);
      jobDetail.currentProgress.messages.forEach(function(msg) {
        var messageType = getMessageType(msg);
        addMessage(messageType, msg);
      });
    } else {
      console.log('No messages found in currentProgress');
    }
    
    // Render messages/logs if available from currentProgress
    if (jobDetail.currentProgress && jobDetail.currentProgress.messages && jobDetail.currentProgress.messages.length > 0) {
      console.log('Rendering messages from currentProgress:', jobDetail.currentProgress.messages.length);
      jobDetail.currentProgress.messages.forEach(function(msg) {
        var messageType = getMessageType(msg);
        addMessage(messageType, msg);
      });
    } else {
      console.log('No messages found in currentProgress');
    }

    // Action Buttons
    updateActionButtons(crawl.status);
  }

  /**
   * Get status badge HTML
   */
  function getStatusBadge(status) {
    var statusClass = 'status-' + status.toLowerCase();
    var statusText = getStatusText(status);
    return '<span class="' + statusClass + '">' + statusText + '</span>';
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
   * Update action buttons based on job status
   */
  function updateActionButtons(status) {
    $('#btnPause').hide();
    $('#btnResume').hide();
    $('#btnCancel').hide();
    $('#btnRetry').hide();

    if (status === 'PENDING') {
      // PENDING jobs can only be cancelled
      $('#btnCancel').show();
    } else if (status === 'RUNNING') {
      $('#btnPause').show();
      $('#btnCancel').show();
    } else if (status === 'PAUSED') {
      $('#btnResume').show();
      $('#btnCancel').show();
    } else if (status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED') {
      // Show retry button for jobs that are not running
      $('#btnRetry').show();
    }
  }

  /**
   * Update progress UI
   */
  function updateProgressUI(progress) {
    if (!progress) return;

    // Calculate statistics
    var stats = calculateStatistics(progress);
    updateStatisticsUI(stats);
    updateCharts();

    // Chapter Progress
    var chapterPercent = 0;
    if (progress.totalChapters > 0) {
      chapterPercent = Math.round((progress.currentChapter / progress.totalChapters) * 100);
    }
    $('#chapterProgressBar').css('width', chapterPercent + '%').text(chapterPercent + '%');
    $('#chapterProgressText').text(
      progress.currentChapter + ' / ' + progress.totalChapters + ' (' + chapterPercent + '%)'
    );

    // Image Progress
    var imagePercent = 0;
    if (progress.totalImages > 0) {
      imagePercent = Math.round((progress.downloadedImages / progress.totalImages) * 100);
    }
    $('#imageProgressBar').css('width', imagePercent + '%').text(imagePercent + '%');
    $('#imageProgressText').text(
      progress.downloadedImages + ' / ' + progress.totalImages + ' (' + imagePercent + '%)'
    );

    // Elapsed Time
    var elapsedSeconds = progress.elapsedSeconds || 0;
    var hours = Math.floor(elapsedSeconds / 3600);
    var minutes = Math.floor((elapsedSeconds % 3600) / 60);
    var seconds = elapsedSeconds % 60;
    var timeText = '';
    if (hours > 0) timeText += hours + ' giờ ';
    if (minutes > 0) timeText += minutes + ' phút ';
    timeText += seconds + ' giây';
    $('#elapsedTime').text(timeText);

    // Current Message
    if (progress.currentMessage) {
      $('#currentMessage').text(progress.currentMessage);
      // Update alert class based on status
      $('#currentMessage').removeClass('alert-info alert-success alert-danger alert-warning');
      if (progress.status === 'completed') {
        $('#currentMessage').addClass('alert-success');
      } else if (progress.status === 'failed') {
        $('#currentMessage').addClass('alert-danger');
      } else if (progress.status === 'paused') {
        $('#currentMessage').addClass('alert-warning');
      } else {
        $('#currentMessage').addClass('alert-info');
      }
    }

    // Messages
    if (progress.messages && progress.messages.length > 0) {
      // Only add new messages (compare with existing)
      var existingMessages = $('#messagesScrollBox .message-item').length;
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
    var messagesBox = $('#messagesScrollBox');

    // Remove "no messages" placeholder
    if (messagesBox.find('.text-muted').length > 0) {
      messagesBox.empty();
    }

    var timestamp = new Date().toLocaleTimeString('vi-VN');
    var messageClass = 'message-' + type;
    var messageItem = $('<div class="message-item ' + messageClass + '">')
      .html('<span class="message-timestamp">[' + timestamp + ']</span>' + escapeHtml(message));

    messagesBox.append(messageItem);

    // Auto-scroll to bottom
    messagesBox.scrollTop(messagesBox[0].scrollHeight);
  }

  /**
   * Escape HTML (duplicate removed - using the one defined above)
   */
  // Function removed - using escapeHtml defined at line 918

  /**
   * Pause job
   */
  function pauseJob() {
    if (!confirm('Bạn có chắc muốn tạm dừng job này?')) {
      return;
    }

    ApiClient.post('/api/admin/comic-crawls/' + jobId + '/pause?reason=' + encodeURIComponent('Paused by user'), {}, true)
      .done(function (response) {
        if (response.success) {
          addMessage('warning', 'Job đã được tạm dừng');
          cleanupWebSocket();
          loadJobDetail(); // Reload to update status
        } else {
          showError('Lỗi khi tạm dừng job: ' + (response.message || 'Unknown error'));
        }
      })
      .fail(function (xhr) {
        showError('Lỗi khi tạm dừng job: ' + xhr.status);
      });
  }

  /**
   * Resume job
   */
  function resumeJob() {
    ApiClient.post('/api/admin/comic-crawls/' + jobId + '/resume', {}, true)
      .done(function (response) {
        if (response.success) {
          addMessage('info', 'Job đã được tiếp tục');
          loadJobDetail(); // Reload to update status and start WebSocket
        } else {
          showError('Lỗi khi tiếp tục job: ' + (response.message || 'Unknown error'));
        }
      })
      .fail(function (xhr) {
        showError('Lỗi khi tiếp tục job: ' + xhr.status);
      });
  }

  /**
   * Cancel job
   */
  function cancelJob() {
    if (!confirm('Bạn có chắc muốn hủy job này? Hành động này không thể hoàn tác.')) {
      return;
    }

    var reason = prompt('Nhập lý do hủy (tùy chọn):');
    if (reason === null) {
      return; // User cancelled
    }

    var reasonParam = reason ? '?reason=' + encodeURIComponent(reason) : '';
    ApiClient.post('/api/admin/comic-crawls/' + jobId + '/cancel' + reasonParam, {}, true)
      .done(function (response) {
        if (response.success) {
          addMessage('error', 'Job đã được hủy');
          cleanupWebSocket();
          loadJobDetail(); // Reload to update status
        } else {
          showError('Lỗi khi hủy job: ' + (response.message || 'Unknown error'));
        }
      })
      .fail(function (xhr) {
        showError('Lỗi khi hủy job: ' + xhr.status);
      });
  }

  /**
   * Retry job
   */
  function retryJob() {
    if (!confirm('Bạn có chắc muốn retry job này? Tất cả dữ liệu đã crawl (checkpoint, progress, files) sẽ bị xóa và job sẽ bắt đầu lại từ đầu.')) {
      return;
    }

    ApiClient.post('/api/admin/comic-crawls/' + jobId + '/retry', {}, true)
      .done(function (response) {
        if (response.success) {
          addMessage('info', 'Job đã được retry');
          cleanupWebSocket();
          loadJobDetail(); // Reload to update status and start WebSocket
        } else {
          showError('Lỗi khi retry job: ' + (response.message || 'Unknown error'));
        }
      })
      .fail(function (xhr) {
        var errorMsg = xhr.responseJSON?.message || 'Lỗi khi retry job: ' + xhr.status;
        showError(errorMsg);
      });
  }

  /**
   * Format date time
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
    $('#loadingIndicator').html(
      '<div class="alert alert-danger">' + escapeHtml(message) + '</div>'
    );
  }

  // Initialize when DOM is ready
  console.log('crawl-detail.js: Setting up DOM ready handler');
  
  // Check if jQuery is loaded
  if (typeof $ === 'undefined') {
    console.error('crawl-detail.js: jQuery is not loaded!');
    return;
  }
  
  console.log('crawl-detail.js: jQuery version:', $.fn.jquery);
  
  $(document).ready(function () {
    console.log('crawl-detail.js: DOM ready - initializing crawl-detail module');
    try {
      init();
    } catch (error) {
      console.error('crawl-detail.js: Error in init():', error);
      console.error('crawl-detail.js: Error stack:', error.stack);
    }
  });
  
  // Also try to initialize immediately if DOM is already ready
  if (document.readyState === 'complete' || document.readyState === 'interactive') {
    console.log('crawl-detail.js: DOM already ready - initializing crawl-detail module immediately');
    setTimeout(function() {
      try {
        init();
      } catch (error) {
        console.error('crawl-detail.js: Error in init() (immediate):', error);
        console.error('crawl-detail.js: Error stack:', error.stack);
      }
    }, 0);
  }
  
  console.log('crawl-detail.js: Module setup complete');
  
  } catch (error) {
    console.error('crawl-detail.js: Error in module setup:', error);
    console.error('crawl-detail.js: Error stack:', error.stack);
  }
})();

console.log('crawl-detail.js: Module file execution complete');
