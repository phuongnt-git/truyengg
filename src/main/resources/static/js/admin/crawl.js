/**
 * Crawl Module - Quản lý crawl truyện với WebSocket real-time updates
 */
(function ($) {
  'use strict';

  // Module state
  var CrawlModule = {
    // WebSocket connection variables
    stompClient: null,
    socket: null,
    wsSubscription: null,
    currentJobId: null,
    wsPendingSubscription: null, // jobId waiting to be subscribed

    // WebSocket connection state
    wsConnectionState: 'disconnected',
    reconnectAttempts: 0,
    maxReconnectAttempts: 10,
    reconnectDelay: 1000,
    reconnectTimer: null,
    heartbeatInterval: null,
    lastHeartbeatTime: null,

    // Realtime update intervals
    elapsedTimeInterval: null,
    jobStartTime: null,
    lastProgressUpdateTime: null,
    pausedElapsedTime: 0, // Accumulated elapsed time when paused
    isPaused: false,

    /**
     * Initialize module
     */
    init: function () {
      if (!ApiClient.getToken()) {
        window.location.href = '/';
        return;
      }

      this.bindEvents();
    },

    /**
     * Bind event handlers
     */
    bindEvents: function () {
      var self = this;

      // Toggle partial download options visibility based on downloadMode
      $('input[name="downloadMode"]').on('change', function () {
        var mode = $(this).val();
        if (mode === 'PARTIAL') {
          $('#partial_download_options').slideDown(300);
          updatePartRangeDisplay();
        } else {
          $('#partial_download_options').slideUp(300);
        }
      });

      // Toggle partial type options (range vs specific)
      $('input[name="partialType"]').on('change', function () {
        var type = $(this).val();
        if (type === 'range') {
          $('#partial_specific_options').slideUp(300);
        } else {
          $('#partial_specific_options').slideDown(300);
        }
      });

      // Update part range display when values change
      $('#part_start, #part_end').on('input change', function() {
        updatePartRangeDisplay();
      });

      // Function to update part range display
      function updatePartRangeDisplay() {
        var partStart = $('#part_start').val() || '1';
        var partEnd = $('#part_end').val() || '5';
        $('#partStartDisplay').text(partStart);
        $('#partEndDisplay').text(partEnd);
      }

      // Initialize display on page load
      updatePartRangeDisplay();

      // Initialize visibility on page load
      var selectedMode = $('input[name="downloadMode"]:checked').val();
      if (selectedMode === 'PARTIAL') {
        $('#partial_download_options').show();
        var selectedType = $('input[name="partialType"]:checked').val();
        if (selectedType === 'specific') {
          $('#partial_specific_options').show();
        } else {
          $('#partial_specific_options').hide();
        }
      }

      // Form submit handler
      $('#crawlForm').on('submit', function (e) {
        e.preventDefault();
        self.handleFormSubmit();
      });

      // Cleanup on page unload
      $(window).on('beforeunload', function () {
        self.cleanupWebSocket();
      });

      // Refresh button handler - reset state without reloading page
      $('#refreshButton').on('click', function(e) {
        e.preventDefault();
        self.resetCrawlState();
      });

      // Bind crawl action buttons using event delegation (buttons are created dynamically)
      // Use document-level delegation to handle buttons created after page load
      // Main crawl button handles all actions based on state
      $(document).on('click', '#crawlButton', function(e) {
        var button = $(this);
        var buttonText = button.html().toLowerCase();
        var buttonType = button.attr('type');
        
        // If button is type="submit" and text is "Bắt đầu tải", let form submit normally
        if (buttonType === 'submit' && buttonText.includes('bắt đầu tải')) {
          return; // Let form submit
        }
        
        // Prevent default for all other button states
        e.preventDefault();
        
        // Handle different button states
        if (buttonText.includes('tạm dừng') || buttonText.includes('pause')) {
          self.pauseCrawl();
        } else if (buttonText.includes('tiếp tục') || buttonText.includes('resume') || buttonText.includes('play')) {
          self.resumeCrawl();
        } else if (buttonText.includes('thử lại') || buttonText.includes('retry') || buttonText.includes('redo')) {
          self.retryCrawl();
        } else if (buttonText.includes('xóa') || buttonText.includes('delete') || buttonText.includes('trash')) {
          self.deleteCrawl();
        } else if (buttonText.includes('đóng') || buttonText.includes('close') || buttonText.includes('times')) {
          self.closeCrawlProgress();
        } else if (buttonText.includes('đang tải') || buttonText.includes('spinner')) {
          // During loading, if status is running, allow pause
          if (self.currentJobId) {
            var status = $('#statusBadge').text().toLowerCase();
            if (status.includes('đang chạy') || status.includes('running')) {
              self.pauseCrawl();
            }
          }
        }
      });
    },

    /**
     * Parse chapter indices from input string
     * Supports formats: "1,3,5", "10-15", "1,3,5,10-15", "1-5,10,15-20"
     * @param {string} input - Input string with chapter indices
     * @returns {Array<number>} Array of chapter indices (sorted, unique)
     */
    parseChapterIndices: function (input) {
      if (!input || !input.trim()) {
        return [];
      }

      var indices = [];
      var parts = input.split(',').map(function (part) {
        return part.trim();
      });

      for (var i = 0; i < parts.length; i++) {
        var part = parts[i];
        if (part.includes('-')) {
          // Range format: "10-15"
          var rangeParts = part.split('-').map(function (p) {
            return parseInt(p.trim());
          });
          if (rangeParts.length === 2 && !isNaN(rangeParts[0]) && !isNaN(rangeParts[1])) {
            var start = Math.min(rangeParts[0], rangeParts[1]);
            var end = Math.max(rangeParts[0], rangeParts[1]);
            for (var j = start; j <= end; j++) {
              if (j > 0 && indices.indexOf(j) === -1) {
                indices.push(j);
              }
            }
          }
        } else {
          // Single number
          var num = parseInt(part);
          if (!isNaN(num) && num > 0 && indices.indexOf(num) === -1) {
            indices.push(num);
          }
        }
      }

      // Sort and return
      return indices.sort(function (a, b) {
        return a - b;
      });
    },

    /**
     * Handle form submission
     */
    handleFormSubmit: function () {
      var self = this;
      var crawlButton = $('#crawlButton');
      // Keep button in loading state initially, will be updated to pause when WebSocket connects
      crawlButton.prop('disabled', false)
        .removeClass('btn-primary btn-warning btn-success btn-info btn-danger btn-secondary')
        .addClass('btn-warning')
        .html('<i class="fas fa-spinner fa-spin"></i> Đang tải...')
        .attr('type', 'button');

      // Show progress bar
      $('.progress').show();
      $('.progress-bar').css('width', '0%').text('0%');

      // Clear output and initialize progress card IMMEDIATELY
      $('#crawlOutput').empty();
      if (!this.initializeProgressCard()) {
        crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
        return;
      }

      var web = $('#web').val().trim();
      var downloadMode = $('input[name="downloadMode"]:checked').val();

      if (!web) {
        this.showMessage('Please enter the comic URL.', true);
        crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
        $('.progress').hide();
        return;
      }

      if (!downloadMode) {
        this.showMessage('Please select an image download mode.', true);
        crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
        $('.progress').hide();
        return;
      }

      // Build request data based on download mode
      var requestData = {
        url: web,
        downloadMode: downloadMode
      };

      // Add partial download options if mode is PARTIAL
      if (downloadMode === 'PARTIAL') {
        // Step 1: partStart and partEnd are ALWAYS required for PARTIAL mode (defines crawl range)
        var partStart = $('#part_start').val() ? parseInt($('#part_start').val()) : null;
        var partEnd = $('#part_end').val() ? parseInt($('#part_end').val()) : null;
        
        if (!partStart || !partEnd) {
          this.showMessage('Please enter both start and end chapter indices to define the crawl range.', true);
          crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
          $('.progress').hide();
          return;
        }
        
        if (partStart > partEnd) {
          this.showMessage('Start chapter index must be less than or equal to end chapter index.', true);
          crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
          $('.progress').hide();
          return;
        }
        
        // Always include partStart and partEnd (defines crawl range)
        requestData.partStart = partStart;
        requestData.partEnd = partEnd;
        
        // Step 2: Check if specific chapters are selected for image download
        var partialType = $('input[name="partialType"]:checked').val();
        
        if (partialType === 'specific') {
          var chaptersInput = $('#image_download_chapters').val().trim();
          
          if (!chaptersInput) {
            this.showMessage('Please enter chapter indices for specific chapters image download.', true);
            crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
            $('.progress').hide();
            return;
          }
          
          var chapters = this.parseChapterIndices(chaptersInput);
          if (chapters.length === 0) {
            this.showMessage('Invalid chapter indices format. Please use comma-separated numbers or ranges (e.g., 1,3,5 or 10-15).', true);
            crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
            $('.progress').hide();
            return;
          }
          
          // Validate that all chapters are within the crawl range
          var invalidChapters = chapters.filter(function(ch) {
            return ch < partStart || ch > partEnd;
          });
          if (invalidChapters.length > 0) {
            this.showMessage('Some chapter indices (' + invalidChapters.join(', ') + ') are outside the crawl range (' + partStart + '-' + partEnd + ').', true);
            crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Start Download');
            $('.progress').hide();
            return;
          }
          
          requestData.downloadChapters = chapters;
          // Note: downloadChapters will be null for 'range' type, meaning download all in range
        }
        // If partialType === 'range', downloadChapters will be null/undefined, 
        // which means download images for all chapters in partStart-partEnd range
      }

      // Close existing WebSocket connection
      this.cleanupWebSocket();

      // Prepare WebSocket connection EARLY (before API call)
      // This establishes connection immediately, subscription happens after we get jobId
      if (!this.prepareWebSocketConnection(crawlButton)) {
        // prepareWebSocketConnection handles error display, just return
        return;
      }

      // Start async crawl (parallel with WebSocket connection)
      ApiClient.post('/api/admin/comic-crawls/async', requestData, true)
        .done(function (response) {
            if (response.success && response.data) {
            self.currentJobId = response.data;
            
            // Check if job was queued (PENDING status)
            if (response.message && response.message.includes('queued')) {
              self.appendProgressLog('Job đã được tạo với ID: ' + self.currentJobId);
              self.appendProgressLog('Job đang chờ slot để bắt đầu (giới hạn: 5 jobs/admin, 25 jobs/server)');
              self.showMessage('Job đã được tạo và đang chờ. Sẽ tự động bắt đầu khi có slot.', false);
              
              // Don't subscribe to WebSocket for PENDING jobs
              self.cleanupWebSocket();
              self.updateMainCrawlButton('pending', crawlButton);
              $('.progress').hide();
            } else {
              self.appendProgressLog('Job đã được tạo với ID: ' + self.currentJobId);
              
              // Update button to show loading state with pause option
              self.updateMainCrawlButton('running', crawlButton);
              // Update secondary buttons
              if ($('#progressCard').length > 0) {
                self.updateSecondaryButtons('running');
              }
              
              // Subscribe to WebSocket (connection may already be established or still connecting)
              if (self.stompClient && self.stompClient.connected) {
                // Already connected, subscribe immediately
                self.subscribeToProgress(self.currentJobId, crawlButton);
              } else if (self.wsConnectionState === 'connecting') {
                // Still connecting, queue subscription
                self.wsPendingSubscription = self.currentJobId;
              } else {
                // Not connecting, start full connection (fallback)
                self.startWebSocketConnection(self.currentJobId, crawlButton, false);
              }
            }

            // Trigger jobs list refresh
            if (typeof JobsModule !== 'undefined') {
              if (typeof JobsModule.refresh === 'function') {
                JobsModule.refresh(self.currentJobId);
              }
              if (typeof JobsModule.broadcastRefresh === 'function') {
                JobsModule.broadcastRefresh();
              }
            }
          } else {
            // API call succeeded but no jobId - cleanup WebSocket
            self.cleanupWebSocket();
            self.showMessage('Lỗi: ' + (response.message || 'Không thể khởi tạo crawl'), true);
            crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
            $('.progress').hide();
          }
        })
        .fail(function (xhr) {
          // Cleanup WebSocket connection if API call fails
          self.cleanupWebSocket();
          $('.progress').hide();
          crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
          var errorMsg = xhr.responseJSON?.message || 'Lỗi hệ thống: ' + xhr.status;
          $('#crawlOutput').html('<div class="alert alert-danger">' + errorMsg + '</div>');
          $('.progress-bar').css('width', '0%').text('0%');
        });
    },

    /**
     * Initialize progress card structure in modal
     */
    initializeProgressCard: function () {
      try {
        if ($('#crawlProgressContent').length === 0) {
          return false;
        }

        $('#crawlProgressContent').empty();

        var initHtml = this.buildProgressCardHTML();
        $('#crawlProgressContent').html(initHtml);

        if ($('#progressCard').length === 0) {
          this.showMessage('Lỗi: Không thể tạo progress card', true);
          return false;
        }

        // Button will be updated to show "Xem tiến độ crawl"

        // Initialize Feather icons
        if (typeof feather !== 'undefined') {
          feather.replace();
        }

        // Append initial log message
        if (typeof this.appendProgressLog === 'function') {
          this.appendProgressLog('Job đã được tạo. Đang kết nối WebSocket...');
        } else {
          var timestamp = new Date();
          var timeStr = '[' +
            String(timestamp.getHours()).padStart(2, '0') + ':' +
            String(timestamp.getMinutes()).padStart(2, '0') + ':' +
            String(timestamp.getSeconds()).padStart(2, '0') + ']';
          $('#progressLog').html('<div class="log-entry mb-1" style="color: #e2e8f0;">' + timeStr + ' Job đã được tạo. Đang kết nối WebSocket...</div>');
        }

        // Auto-open modal when progress starts
        var modal = new bootstrap.Modal(document.getElementById('crawlProgressModal'));
        modal.show();

        return true;
      } catch (e) {
        this.showMessage('Lỗi khi khởi tạo progress card: ' + e.message, true);
        return false;
      }
    },

    /**
     * Update WebSocket connection status UI
     */
    updateConnectionStatus: function (state, message) {
      this.wsConnectionState = state;
      var statusBadge = $('#wsConnectionStatus');
      if (statusBadge.length === 0) {
        return;
      }

      statusBadge.removeClass('bg-success bg-info bg-warning bg-danger bg-secondary');
      switch (state) {
        case 'connected':
          statusBadge.addClass('bg-success').html('<i data-feather="circle"></i> Đã kết nối');
          break;
        case 'connecting':
          statusBadge.addClass('bg-info').html('<i data-feather="loader"></i> Đang kết nối...');
          break;
        case 'disconnected':
          statusBadge.addClass('bg-secondary').html('<i data-feather="circle"></i> Đã ngắt kết nối');
          break;
        case 'error':
          statusBadge.addClass('bg-danger').html('<i data-feather="alert-circle"></i> Lỗi kết nối');
          break;
      }
      
      // Re-initialize Feather icons
      if (typeof feather !== 'undefined') {
        feather.replace();
      }
    },

    /**
     * Start heartbeat monitoring
     */
    startHeartbeat: function () {
      this.stopHeartbeat();
      var self = this;
      this.lastHeartbeatTime = Date.now();

      this.heartbeatInterval = setInterval(function () {
        if (self.stompClient && self.stompClient.connected) {
          var now = Date.now();
          if (self.lastProgressUpdateTime && (now - self.lastProgressUpdateTime) > 30000) {
            console.warn('No progress update received in 30 seconds, connection may be stale');
            self.scheduleReconnect(self.currentJobId);
          }
        } else {
          self.stopHeartbeat();
        }
      }, 10000);
    },

    /**
     * Stop heartbeat monitoring
     */
    stopHeartbeat: function () {
      if (this.heartbeatInterval) {
        clearInterval(this.heartbeatInterval);
        this.heartbeatInterval = null;
      }
    },

    /**
     * Schedule reconnection with exponential backoff
     */
    scheduleReconnect: function (jobId, crawlButton) {
      var self = this;

      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
      }

      if (this.reconnectAttempts >= this.maxReconnectAttempts) {
        console.error('Max reconnection attempts reached');
        this.updateConnectionStatus('error', 'Không thể kết nối sau ' + this.maxReconnectAttempts + ' lần thử');
        this.appendProgressLog('Không thể kết nối WebSocket. Đang thử kết nối lại...');
        return;
      }

      this.reconnectAttempts++;
      var delay = Math.min(this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1), 30000);

      this.updateConnectionStatus('connecting', 'Đang thử kết nối lại... (' + this.reconnectAttempts + '/' + this.maxReconnectAttempts + ')');

      this.reconnectTimer = setTimeout(function () {
        if (self.currentJobId === jobId) {
          self.startWebSocketConnection(jobId, crawlButton, true);
        }
      }, delay);
    },

    /**
     * Build progress card HTML for modal
     */
    buildProgressCardHTML: function () {
      var html = '<div id="progressCard" class="progress-card">';
      
      // Header with status
      html += '<div class="mb-3">';
      html += '<div class="d-flex justify-content-between align-items-center mb-2">';
      html += '<h5 class="mb-0">Tiến độ Crawl</h5>';
      html += '<span id="wsConnectionStatus" class="badge bg-secondary"><i data-feather="circle"></i> Đã ngắt kết nối</span>';
      html += '</div>';
      html += '<div id="progressStatus" class="mb-2">';
      html += '<strong>Trạng thái:</strong> <span id="statusBadge" class="badge bg-info">Đang khởi tạo...</span>';
      html += '</div>';
      html += '</div>';
      
      // Progress bars
      html += '<div class="mb-3">';
      html += '<label>Tiến độ chương: <span id="progressChapterText">0 / 0 (0%)</span></label>';
      html += '<div class="progress" style="height: 24px;">';
      html += '<div id="progressChapterBar" class="progress-bar progress-bar-striped progress-bar-animated bg-primary" role="progressbar" style="width: 0%" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">0%</div>';
      html += '</div></div>';
      html += '<div class="mb-3">';
      html += '<label>Tiến độ hình ảnh: <span id="progressImageText">0 / 0 (0%)</span></label>';
      html += '<div class="progress" style="height: 24px;">';
      html += '<div id="progressImageBar" class="progress-bar progress-bar-striped progress-bar-animated bg-success" role="progressbar" style="width: 0%" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">0%</div>';
      html += '</div></div>';
      html += '<div id="progressTime" class="mb-3"></div>';
      
      // Current message
      html += '<div id="progressCurrentMessage" class="mb-3">';
      html += '<div class="alert alert-info mb-0"><i data-feather="loader"></i> Đang khởi tạo job...</div>';
      html += '</div>';
      
      // Two columns: Chapters and Log side by side
      html += '<div class="row g-3">';
      
      // Left column: Chapters
      html += '<div class="col-md-6">';
      html += '<h6 class="mb-2"><i data-feather="list"></i> Danh sách Chapters</h6>';
      html += '<div id="progressChapterList" style="height: 400px; overflow-y: auto; border: 1px solid #dee2e6; padding: 10px; background-color: #f8f9fa; border-radius: 4px;">';
      html += '<div class="text-muted text-center">Chưa có thông tin chapters...</div>';
      html += '</div></div>';
      
      // Right column: Log
      html += '<div class="col-md-6">';
      html += '<h6 class="mb-2"><i data-feather="file-text"></i> Nhật ký</h6>';
      html += '<div id="progressLog" style="height: 400px; overflow-y: auto; border: 1px solid #dee2e6; padding: 15px; background-color: #1a202c; border-radius: 4px; font-family: \'Courier New\', monospace; font-size: 13px; color: #e2e8f0;">';
      html += '<div class="text-muted text-center" style="color: #a0aec0 !important;">Đang chờ kết nối WebSocket...</div>';
      html += '</div></div>';
      
      html += '</div>'; // End row
      html += '</div>'; // End progress-card
      
      return html;
    },

    /**
     * Prepare WebSocket connection (CONNECT phase only, no subscription)
     * This is called BEFORE API call to establish connection early
     */
    prepareWebSocketConnection: function (crawlButton) {
      var self = this;

      // Cleanup existing connection
      this.cleanupWebSocket();
      this.wsPendingSubscription = null;
      this.pausedElapsedTime = 0;
      this.pauseStartTime = null;
      this.isPaused = false;

      // Ensure progress card exists
      if ($('#progressCard').length === 0) {
        if (!this.initializeProgressCard()) {
          this.showMessage('Lỗi: Không thể khởi tạo progress card', true);
          crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
          return false;
        }
      }

      var accessToken = ApiClient.getToken();
      if (!accessToken) {
        this.showMessage('Lỗi: Không tìm thấy token xác thực', true);
        crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
        this.updateConnectionStatus('error', 'Không có token xác thực');
        return false;
      }

      if (accessToken.startsWith('Bearer ')) {
        accessToken = accessToken.substring(7);
      }

      this.updateConnectionStatus('connecting', 'Đang kết nối...');
      this.appendProgressLog('Đang kết nối WebSocket...');

      this.socket = new SockJS('/ws');

      this.socket.onopen = function () {
        self.appendProgressLog('Đã mở kết nối SockJS');
      };

      this.socket.onclose = function (event) {
        if (event.code !== 1000) {
          self.appendProgressLog('Kết nối WebSocket đã đóng (code: ' + event.code + ')');
        }
        self.updateConnectionStatus('disconnected', 'Kết nối đã đóng');

        if (self.currentJobId && self.wsConnectionState !== 'disconnected') {
          self.scheduleReconnect(self.currentJobId, crawlButton);
        }
      };

      this.socket.onerror = function (error) {
        self.appendProgressLog('Lỗi SockJS: ' + (error.message || 'Kết nối thất bại'));
        self.updateConnectionStatus('error', 'Lỗi SockJS');
      };

      this.stompClient = Stomp.over(this.socket);
      this.stompClient.heartbeat.outgoing = 0;
      this.stompClient.heartbeat.incoming = 0;

      this.stompClient.debug = function (str) {
        // Only log errors, ignore all other STOMP debug messages
        if (str && str.toLowerCase().includes('error')) {
          console.error('STOMP Error:', str);
        }
      };

      var headers = {
        'Authorization': 'Bearer ' + accessToken,
        'token': accessToken
      };

      this.stompClient.connect(
        headers,
        function (frame) {
          self.reconnectAttempts = 0;
          self.reconnectDelay = 1000;
          self.updateConnectionStatus('connected', 'Kết nối thành công');
          self.appendProgressLog('Đã kết nối WebSocket thành công. Đang chờ jobId để đăng ký...');

          // Check if there's a pending subscription (jobId received while connecting)
          if (self.wsPendingSubscription) {
            self.subscribeToProgress(self.wsPendingSubscription, crawlButton);
            self.wsPendingSubscription = null;
          }
        },
        function (error) {
          var errorMsg = error.headers?.message || error.toString();
          self.appendProgressLog('Lỗi kết nối WebSocket: ' + errorMsg);
          self.updateConnectionStatus('error', 'Lỗi kết nối: ' + errorMsg);
          // If we have a currentJobId, try to reconnect
          // If we have a pending subscription, we'll retry when reconnect succeeds
          if (self.currentJobId) {
            self.scheduleReconnect(self.currentJobId, crawlButton);
          } else if (self.wsPendingSubscription) {
            // If API hasn't returned yet but WebSocket failed,
            // we'll handle subscription when API returns via startWebSocketConnection fallback
            console.warn('WebSocket connection failed while waiting for API response');
          }
        }
      );

      return true;
    },

    /**
     * Subscribe to progress updates for a specific job
     * This is called after WebSocket is connected
     */
    subscribeToProgress: function (jobIdStr, crawlButton) {
      var self = this;

      if (!this.stompClient || !this.stompClient.connected) {
        console.warn('Cannot subscribe: STOMP client not connected');
        return false;
      }

      // Unsubscribe existing subscription if any
      if (this.wsSubscription) {
        try {
          this.wsSubscription.unsubscribe();
        } catch (e) {
          console.warn('Error unsubscribing:', e);
        }
        this.wsSubscription = null;
      }

      this.startHeartbeat();

      // Ensure jobIdStr is clean (trim whitespace)
      jobIdStr = String(jobIdStr).trim();
      var destination = '/topic/crawl-progress/' + jobIdStr;

      try {
        this.wsSubscription = this.stompClient.subscribe(destination, function (message) {
          try {
            var progress = JSON.parse(message.body);

            self.lastProgressUpdateTime = Date.now();
            self.lastHeartbeatTime = Date.now();

            if (progress.startTime && !self.jobStartTime) {
              self.jobStartTime = progress.startTime;
              self.pausedElapsedTime = 0;
              self.startElapsedTimeTimer();
            }
            
            // Handle pause/resume timer
            if (progress.status === 'paused' && !self.isPaused) {
              // Just paused - stop timer but keep jobStartTime
              self.stopElapsedTimeTimer();
              // Store the time when paused to accumulate
              self.pauseStartTime = Date.now();
            } else if (progress.status === 'running' && self.isPaused) {
              // Just resumed - accumulate paused time and restart timer
              if (self.pauseStartTime) {
                var now = Date.now();
                self.pausedElapsedTime += (now - self.pauseStartTime);
                self.pauseStartTime = null;
              }
              self.startElapsedTimeTimer();
            }
            
            // Stop timer immediately when completed/failed/cancelled
            if (['completed', 'failed', 'cancelled'].includes(progress.status)) {
              self.stopElapsedTimeTimer();
              self.isPaused = true;
            }

            self.updateProgressUI(progress, crawlButton);

            if (['completed', 'failed', 'paused', 'cancelled'].includes(progress.status)) {
              self.stopElapsedTimeTimer();
              self.stopHeartbeat();
              
              // Update main button based on status
              self.updateMainCrawlButton(progress.status, crawlButton);
              
              // Cleanup WebSocket for completed, failed, cancelled, and paused
              if (['completed', 'failed', 'cancelled', 'paused'].includes(progress.status)) {
                self.cleanupWebSocket();
                // Keep currentJobId for paused status so user can resume
                // Also keep currentJobId for completed/failed so user can retry
                if (['cancelled'].includes(progress.status)) {
                  self.currentJobId = null;
                  self.jobStartTime = null;
                  self.lastProgressUpdateTime = null;
                }
              }

              if (typeof JobsModule !== 'undefined') {
                if (typeof JobsModule.refresh === 'function') {
                  JobsModule.refresh();
                }
                if (typeof JobsModule.broadcastRefresh === 'function') {
                  JobsModule.broadcastRefresh();
                }
              }
            }
          } catch (e) {
            console.error('Error parsing WebSocket message:', e, message.body);
            self.appendProgressLog('Lỗi khi xử lý thông điệp WebSocket: ' + e.message);
          }
        });

        if (!this.wsSubscription) {
          console.error('✗ WebSocket subscription failed - subscription is null');
          this.appendProgressLog('Cảnh báo: Đăng ký WebSocket không thành công. Đang thử kết nối lại...');
          this.updateConnectionStatus('error', 'Lỗi đăng ký');
          this.scheduleReconnect(jobIdStr, crawlButton);
          return false;
        } else {
          this.appendProgressLog('Đã đăng ký nhận cập nhật từ: ' + destination);
          $('#statusBadge').attr('class', 'badge bg-info').text('Đang chạy');
          // Update button to show pause option now that we're connected
          this.updateMainCrawlButton('running', crawlButton);
          if ($('#progressCard').length > 0) {
            this.updateSecondaryButtons('running');
          }
          return true;
        }
      } catch (subscribeError) {
        console.error('Error during subscription:', subscribeError);
        this.appendProgressLog('Lỗi khi đăng ký WebSocket: ' + subscribeError.message);
        this.updateConnectionStatus('error', 'Lỗi đăng ký');
        this.scheduleReconnect(jobIdStr, crawlButton);
        return false;
      }
    },

    /**
     * Start WebSocket connection (full flow: connect + subscribe)
     * This is for reconnect scenarios or when called with jobId directly
     * If WebSocket is already connected, just subscribes
     */
    startWebSocketConnection: function (jobId, crawlButton, isReconnect) {
      var self = this;
      var jobIdStr = String(jobId);
      this.currentJobId = jobIdStr;

      // If already connected, just subscribe
      if (this.stompClient && this.stompClient.connected) {
        this.subscribeToProgress(jobIdStr, crawlButton);
        return;
      }

      // If connecting (from prepareWebSocketConnection), queue subscription
      if (this.wsConnectionState === 'connecting') {
        this.wsPendingSubscription = jobIdStr;
        return;
      }

      // Otherwise, establish full connection (reconnect scenario)
      if (!isReconnect) {
        this.reconnectAttempts = 0;
        this.reconnectDelay = 1000;
      }

      // For reconnect scenario, establish full connection
      var accessToken = ApiClient.getToken();
      if (!accessToken) {
        this.showMessage('Lỗi: Không tìm thấy token xác thực', true);
        crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
        this.updateConnectionStatus('error', 'Không có token xác thực');
        return;
      }

      if (accessToken.startsWith('Bearer ')) {
        accessToken = accessToken.substring(7);
      }

      if (!isReconnect) {
        this.cleanupWebSocket();
      }

      // Ensure progress card exists
      if ($('#progressCard').length === 0) {
        if (!this.initializeProgressCard()) {
          this.showMessage('Lỗi: Không thể khởi tạo progress card', true);
          crawlButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu tải');
          return;
        }
      }

      this.updateConnectionStatus('connecting', 'Đang kết nối...');
      this.appendProgressLog('Đang kết nối WebSocket cho job: ' + jobIdStr + '...');

      try {
        this.socket = new SockJS('/ws');

        this.socket.onopen = function () {
          self.appendProgressLog('Đã mở kết nối SockJS');
        };

        this.socket.onclose = function (event) {
          if (event.code !== 1000) {
            self.appendProgressLog('Kết nối WebSocket đã đóng (code: ' + event.code + ')');
          }
          self.updateConnectionStatus('disconnected', 'Kết nối đã đóng');

          if (self.currentJobId === jobIdStr && self.wsConnectionState !== 'disconnected') {
            self.scheduleReconnect(jobIdStr, crawlButton);
          }
        };

        this.socket.onerror = function (error) {
          self.appendProgressLog('Lỗi SockJS: ' + (error.message || 'Kết nối thất bại'));
          self.updateConnectionStatus('error', 'Lỗi SockJS');
        };

        this.stompClient = Stomp.over(this.socket);
        this.stompClient.heartbeat.outgoing = 0;
        this.stompClient.heartbeat.incoming = 0;

        this.stompClient.debug = function (str) {
          // Only log errors, ignore all other STOMP debug messages
          if (str && str.toLowerCase().includes('error')) {
            console.error('STOMP Error:', str);
          }
        };

        var headers = {
          'Authorization': 'Bearer ' + accessToken,
          'token': accessToken
        };

        this.stompClient.connect(
          headers,
          function (frame) {
            self.reconnectAttempts = 0;
            self.reconnectDelay = 1000;
            self.updateConnectionStatus('connected', 'Kết nối thành công');
            self.appendProgressLog('Đã kết nối WebSocket thành công. Đang đăng ký nhận cập nhật...');

            // Subscribe immediately
            self.subscribeToProgress(jobIdStr, crawlButton);
          },
          function (error) {
            if ($('#progressCard').length === 0) {
              self.initializeProgressCard();
            }
            var errorMsg = error.headers?.message || error.toString();
            self.appendProgressLog('Lỗi kết nối WebSocket: ' + errorMsg);
            self.updateConnectionStatus('error', 'Lỗi kết nối: ' + errorMsg);
            if ($('#statusBadge').length > 0) {
              $('#statusBadge').attr('class', 'badge bg-warning').text('Đang thử kết nối lại...');
            }
            self.scheduleReconnect(jobIdStr, crawlButton);
          }
        );
      } catch (e) {
        this.appendProgressLog('Lỗi khi tạo kết nối WebSocket: ' + e.message);
        this.updateConnectionStatus('error', 'Lỗi: ' + e.message);
        if ($('#statusBadge').length > 0) {
          $('#statusBadge').attr('class', 'badge status-badge badge-warning').text('Đang thử kết nối lại...');
        }
        this.scheduleReconnect(jobId, crawlButton);
      }
    },

    /**
     * Cleanup WebSocket connection
     */
    cleanupWebSocket: function () {
      this.stopElapsedTimeTimer();
      this.stopHeartbeat();

      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }

      this.reconnectAttempts = 0;
      this.wsPendingSubscription = null;

      if (this.wsSubscription) {
        this.wsSubscription.unsubscribe();
        this.wsSubscription = null;
      }

      if (this.stompClient && this.stompClient.connected) {
        try {
          this.stompClient.disconnect();
        } catch (e) {
          console.error('Error disconnecting STOMP client:', e);
        }
      }

      if (this.socket) {
        try {
          this.socket.close();
        } catch (e) {
          console.error('Error closing socket:', e);
        }
      }

      this.stompClient = null;
      this.socket = null;
      this.updateConnectionStatus('disconnected', 'Đã ngắt kết nối');
      this.jobStartTime = null;
      this.lastProgressUpdateTime = null;
    },


    /**
     * Format message for display
     */
    formatMessage: function (message) {
      if (!message) return message;

      var msg = message.toLowerCase();

      // Image messages
      if (msg.includes('ảnh') || msg.includes('image') || msg.includes('hình ảnh')) {
        var imagePattern = /(\d+)\s*\/\s*(\d+)\s*(ảnh|hình ảnh|image)/i;
        var match = message.match(imagePattern);
        if (match) {
          var downloaded = match[1];
          var total = match[2];
          var percent = total > 0 ? Math.round((downloaded / total) * 100) : 0;
          return '<i data-feather="image" style="width: 14px; height: 14px; vertical-align: middle; color: #0ea5e9;"></i> <strong>' + downloaded + '</strong> / ' + total + ' hình ảnh <span class="badge bg-info">' + percent + '%</span>';
        }
        return '<i data-feather="image" style="width: 14px; height: 14px; vertical-align: middle; color: #0ea5e9;"></i> ' + message;
      }

      // Chapter messages
      if (msg.includes('chương') || msg.includes('chapter')) {
        return '<i data-feather="book" style="width: 14px; height: 14px; vertical-align: middle; color: #3b82f6;"></i> ' + message;
      }

      // Error messages
      if (msg.includes('lỗi') || msg.includes('error') || msg.includes('failed')) {
        return '<i data-feather="alert-triangle" style="width: 14px; height: 14px; vertical-align: middle; color: #ef4444;"></i> ' + message;
      }

      // Success messages
      if (msg.includes('hoàn thành') || msg.includes('completed') || msg.includes('thành công')) {
        return '<i data-feather="check-circle" style="width: 14px; height: 14px; vertical-align: middle; color: #10b981;"></i> ' + message;
      }

      return message;
    },

    /**
     * Append message to progress log
     */
    appendProgressLog: function (message) {
      var timestamp = new Date();
      var timeStr = '[' +
        String(timestamp.getHours()).padStart(2, '0') + ':' +
        String(timestamp.getMinutes()).padStart(2, '0') + ':' +
        String(timestamp.getSeconds()).padStart(2, '0') + ']';

      var logContainer = $('#progressLog');
      if (logContainer.length === 0) {
        // Log container doesn't exist yet, create it in modal
        this.initializeLogModal();
        logContainer = $('#progressLog');
      }

      if (logContainer.find('.text-muted').length > 0) {
        logContainer.empty();
      }

      var formattedMessage = this.formatMessage(message);
      var logEntry = $('<div class="log-entry mb-1" style="color: #e2e8f0;">').html(timeStr + ' ' + formattedMessage);
      logContainer.append(logEntry);

      // Initialize Feather icons for new log entry
      if (typeof feather !== 'undefined') {
        feather.replace();
      }

      // Don't auto-scroll - let user control scrolling
    },

    /**
     * Update chapter progress list
     */
    updateChapterProgressList: function (chapterProgress) {
      var container = $('#progressChapterList');
      if (container.length === 0) return;

      var chapters = Object.values(chapterProgress).sort(function (a, b) {
        return a.chapterIndex - b.chapterIndex;
      });

      container.empty();

      if (chapters.length === 0) {
        container.html('<div class="text-muted text-center">Chưa có thông tin chapters...</div>');
        return;
      }

      var self = this;
      chapters.forEach(function (chapter) {
        var statusClass = 'bg-secondary';
        var statusText = 'Chờ';
        // Fix: Check status correctly (should be lowercase from backend)
        var chapterStatus = (chapter.status || '').toLowerCase();
        if (chapterStatus === 'downloading') {
          statusClass = 'bg-info';
          statusText = 'Đang tải';
        } else if (chapterStatus === 'completed') {
          statusClass = 'bg-success';
          statusText = 'Hoàn thành';
        } else if (chapterStatus === 'failed') {
          statusClass = 'bg-danger';
          statusText = 'Thất bại';
        } else if (chapterStatus === 'pending') {
          statusClass = 'bg-secondary';
          statusText = 'Chờ';
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
        chapterHtml += '<div class="progress" style="height: 20px;">';
        chapterHtml += '<div class="progress-bar ' + (chapterStatus === 'downloading' ? 'progress-bar-striped progress-bar-animated bg-info' : (chapterStatus === 'completed' ? 'bg-success' : 'bg-secondary')) + '" ';
        chapterHtml += 'style="width: ' + progressPercent + '%" role="progressbar" aria-valuenow="' + progressPercent + '" aria-valuemin="0" aria-valuemax="100">' + progressPercent + '%</div>';
        chapterHtml += '</div>';
        chapterHtml += '<div class="mt-1">';
        chapterHtml += '<small class="text-muted">';
        chapterHtml += '<i data-feather="image" style="width: 12px; height: 12px; vertical-align: middle;"></i> ';
        chapterHtml += '<strong>' + chapter.downloadedImages + '</strong> / ' + chapter.totalImages + ' hình ảnh';
        if (chapter.totalImages > 0) {
          chapterHtml += ' <span class="badge bg-light text-dark">' + progressPercent + '%</span>';
        }
        chapterHtml += '</small></div></div>';

        container.append(chapterHtml);
      });

      // Re-initialize Feather icons
      if (typeof feather !== 'undefined') {
        feather.replace();
      }

      // Don't auto-scroll - let user control scrolling
    },


    /**
     * Start elapsed time timer
     */
    startElapsedTimeTimer: function () {
      this.stopElapsedTimeTimer();
      this.isPaused = false;
      this.updateElapsedTimeRealtime();
      var self = this;
      this.elapsedTimeInterval = setInterval(function () {
        if (!self.isPaused) {
          self.updateElapsedTimeRealtime();
        }
      }, 1000);
    },

    /**
     * Stop elapsed time timer
     */
    stopElapsedTimeTimer: function () {
      if (this.elapsedTimeInterval) {
        clearInterval(this.elapsedTimeInterval);
        this.elapsedTimeInterval = null;
      }
      this.isPaused = true;
    },

    /**
     * Update elapsed time realtime
     */
    updateElapsedTimeRealtime: function () {
      if (!this.jobStartTime) return;

      var now = Date.now();
      var start = new Date(this.jobStartTime).getTime();
      var elapsedMs = now - start - this.pausedElapsedTime;
      var elapsedSeconds = Math.floor(elapsedMs / 1000);

      var hours = Math.floor(elapsedSeconds / 3600);
      var minutes = Math.floor((elapsedSeconds % 3600) / 60);
      var seconds = elapsedSeconds % 60;
      var timeText = '<strong>Thời gian đã trôi qua:</strong> ';
      if (hours > 0) timeText += hours + ' giờ ';
      if (minutes > 0) timeText += minutes + ' phút ';
      timeText += seconds + ' giây';

      $('#progressTime').html(timeText);
    },

    /**
     * Update progress UI
     */
    updateProgressUI: function (progress, crawlButton) {
      var percentage = 0;
      if (progress.totalChapters > 0) {
        percentage = Math.round((progress.currentChapter / progress.totalChapters) * 100);
      } else if (progress.status === 'completed') {
        percentage = 100;
      } else if (progress.status === 'failed') {
        percentage = 0;
      }

      $('.progress').show();
      $('.progress-bar').css('width', percentage + '%').text(percentage + '%');

      // Initialize progress card if not exists
      if ($('#progressCard').length === 0) {
        $('#crawlProgressContent').html(this.buildProgressCardHTML());
        // Initialize Feather icons
        if (typeof feather !== 'undefined') {
          feather.replace();
        }
      }

      // Update status badge FIRST - this is critical for UI
      var statusBadgeClass = 'bg-info';
      var statusText = 'Đang chạy';
      var progressStatus = (progress.status || '').toLowerCase();
      
      if (progressStatus === 'completed') {
        statusBadgeClass = 'bg-success';
        statusText = 'Hoàn thành';
      } else if (progressStatus === 'failed') {
        statusBadgeClass = 'bg-danger';
        statusText = 'Thất bại';
      } else if (progressStatus === 'paused') {
        statusBadgeClass = 'bg-warning';
        statusText = 'Tạm dừng';
      } else if (progressStatus === 'cancelled') {
        statusBadgeClass = 'bg-secondary';
        statusText = 'Đã hủy';
      } else if (progressStatus === 'running') {
        statusBadgeClass = 'bg-info';
        statusText = 'Đang chạy';
      }

      var statusBadge = $('#statusBadge');
      if (statusBadge.length > 0) {
        statusBadge.attr('class', 'badge ' + statusBadgeClass).text(statusText);
      }

      // Update main crawl button based on status
      this.updateMainCrawlButton(progress.status, crawlButton);
      
      // Update secondary buttons (delete, close) when progress card is visible
      if ($('#progressCard').length > 0) {
        this.updateSecondaryButtons(progress.status);
      }
      
      // Re-initialize Feather icons after updates
      if (typeof feather !== 'undefined') {
        feather.replace();
      }

      // Update chapter progress bar
      var chapterPercent = 0;
      if (progress.totalChapters > 0) {
        chapterPercent = Math.round((progress.currentChapter / progress.totalChapters) * 100);
      }
      var chapterBar = $('#progressChapterBar');
      chapterBar.css('width', chapterPercent + '%').attr('aria-valuenow', chapterPercent).text(chapterPercent + '%');
      $('#progressChapterText').text(
        progress.currentChapter + ' / ' + progress.totalChapters + ' (' + chapterPercent + '%)'
      );

      // Update image progress bar
      var imagePercent = 0;
      var totalImages = progress.totalImages || 0;
      var downloadedImages = progress.downloadedImages || 0;
      
      if (totalImages > 0) {
        imagePercent = Math.round((downloadedImages / totalImages) * 100);
      } else if (progress.status === 'completed') {
        imagePercent = 100;
      }
      
      var imageBar = $('#progressImageBar');
      if (imageBar.length > 0) {
        imageBar.css('width', imagePercent + '%')
          .attr('aria-valuenow', imagePercent)
          .text(imagePercent + '%');
      }
      
      var imageText = $('#progressImageText');
      if (imageText.length > 0) {
        imageText.text(
          downloadedImages + ' / ' + totalImages + ' (' + imagePercent + '%)'
        );
      }

      // Update chapter progress list
      if (progress.chapterProgress && Object.keys(progress.chapterProgress).length > 0) {
        this.updateChapterProgressList(progress.chapterProgress);
      }

      // Update elapsed time
      // Stop timer if completed, failed, or cancelled
      if (['completed', 'failed', 'cancelled'].includes(progress.status)) {
        this.stopElapsedTimeTimer();
        this.isPaused = true; // Prevent timer from restarting
        // Show final elapsed time from progress data
        if (progress.elapsedSeconds && progress.elapsedSeconds > 0) {
          var hours = Math.floor(progress.elapsedSeconds / 3600);
          var minutes = Math.floor((progress.elapsedSeconds % 3600) / 60);
          var seconds = progress.elapsedSeconds % 60;
          var timeText = '<strong>Thời gian đã trôi qua:</strong> ';
          if (hours > 0) timeText += hours + ' giờ ';
          if (minutes > 0) timeText += minutes + ' phút ';
          timeText += seconds + ' giây';
          $('#progressTime').html(timeText);
        } else if (this.jobStartTime) {
          // Calculate from jobStartTime if elapsedSeconds not provided
          var now = Date.now();
          var start = new Date(this.jobStartTime).getTime();
          var elapsedMs = now - start - this.pausedElapsedTime;
          var elapsedSeconds = Math.floor(elapsedMs / 1000);
          var hours = Math.floor(elapsedSeconds / 3600);
          var minutes = Math.floor((elapsedSeconds % 3600) / 60);
          var seconds = elapsedSeconds % 60;
          var timeText = '<strong>Thời gian đã trôi qua:</strong> ';
          if (hours > 0) timeText += hours + ' giờ ';
          if (minutes > 0) timeText += minutes + ' phút ';
          timeText += seconds + ' giây';
          $('#progressTime').html(timeText);
        }
      } else if (this.jobStartTime) {
        if (!this.elapsedTimeInterval && !this.isPaused) {
          this.startElapsedTimeTimer();
        }
      } else {
        if (progress.elapsedSeconds && progress.elapsedSeconds > 0) {
          var hours = Math.floor(progress.elapsedSeconds / 3600);
          var minutes = Math.floor((progress.elapsedSeconds % 3600) / 60);
          var seconds = progress.elapsedSeconds % 60;
          var timeText = '<strong>Thời gian đã trôi qua:</strong> ';
          if (hours > 0) timeText += hours + ' giờ ';
          if (minutes > 0) timeText += minutes + ' phút ';
          timeText += seconds + ' giây';
          $('#progressTime').html(timeText);
        } else {
          $('#progressTime').empty();
        }
      }

      // Update current message
      if (progress.currentMessage) {
        var alertClass = 'alert-info';
        if (progress.status === 'completed') {
          alertClass = 'alert-success';
        } else if (progress.status === 'failed') {
          alertClass = 'alert-danger';
        } else if (progress.status === 'paused') {
          alertClass = 'alert-warning';
        }
        $('#progressCurrentMessage').html('<div class="alert ' + alertClass + ' mb-3">' +
          '<strong><i class="fas fa-info-circle"></i> Thông báo:</strong> ' + progress.currentMessage +
          '</div>');
      } else {
        $('#progressCurrentMessage').empty();
      }

      // Append new messages to progress log
      if (progress.messages && progress.messages.length > 0) {
        var existingMessages = $('#progressLog').data('messages') || [];
        var newMessages = progress.messages.slice(existingMessages.length);
        var self = this;
        newMessages.forEach(function (msg) {
          self.appendProgressLog(msg);
        });
        $('#progressLog').data('messages', progress.messages);
      }

      // Update progress bar animation
      if (['completed', 'failed', 'paused', 'cancelled'].includes(progress.status)) {
        $('#progressChapterBar').removeClass('progress-bar-striped progress-bar-animated');
        $('#progressImageBar').removeClass('progress-bar-striped progress-bar-animated');

        if (progress.status === 'completed') {
          $('#progressChapterBar').css('width', '100%').attr('aria-valuenow', 100).text('100%');
          if (progress.totalImages > 0) {
            var finalImagePercent = Math.round((progress.downloadedImages / progress.totalImages) * 100);
            $('#progressImageBar').css('width', finalImagePercent + '%').attr('aria-valuenow', finalImagePercent).text(finalImagePercent + '%');
          } else {
            $('#progressImageBar').css('width', '100%').attr('aria-valuenow', 100).text('100%');
          }
        }
      } else {
        $('#progressChapterBar').addClass('progress-bar-striped progress-bar-animated');
        $('#progressImageBar').addClass('progress-bar-striped progress-bar-animated');
      }
    },

    /**
     * Update main crawl button based on status
     */
    updateMainCrawlButton: function (status, crawlButton) {
      var button = crawlButton || $('#crawlButton');
      if (button.length === 0) return;

      var statusLower = (status || '').toLowerCase();
      
      // If there's an active job, show "Xem tiến độ crawl" button
      if (this.currentJobId && ['running', 'paused', 'completed', 'failed', 'cancelled'].includes(statusLower)) {
        button.prop('disabled', false)
          .removeClass('btn-primary btn-warning btn-success btn-info btn-danger btn-secondary')
          .addClass('btn-info')
          .html('<i data-feather="activity"></i> Xem tiến độ crawl')
          .attr('type', 'button')
          .off('click')
          .on('click', function(e) {
            e.preventDefault();
            var modalElement = document.getElementById('crawlProgressModal');
            if (modalElement) {
              var modal = bootstrap.Modal.getInstance(modalElement) || new bootstrap.Modal(modalElement);
              modal.show();
            }
          });
      } else {
        // No active job, show start button
        button.prop('disabled', false)
          .removeClass('btn-primary btn-warning btn-success btn-info btn-danger btn-secondary')
          .addClass('btn-primary')
          .html('<i data-feather="download"></i> Bắt đầu tải')
          .attr('type', 'submit')
          .off('click');
      }
      
      // Re-initialize Feather icons
      if (typeof feather !== 'undefined') {
        feather.replace();
      }
    },

    /**
     * Add action buttons in modal footer
     */
    updateSecondaryButtons: function (status) {
      // Create buttons container in modal footer if not exists
      var container = $('#crawlProgressActions');
      if (container.length === 0) {
        return;
      }
      
      container.empty();
      
      var statusLower = (status || '').toLowerCase();
      var self = this;
      
      // Pause/Resume button
      if (statusLower === 'running') {
        var pauseBtn = $('<button class="btn btn-warning" id="btnPauseCrawlModal">')
          .html('<i data-feather="pause"></i> Tạm dừng')
          .on('click', function(e) {
            e.preventDefault();
            self.pauseCrawl();
          });
        container.append(pauseBtn);
      } else if (statusLower === 'paused') {
        var resumeBtn = $('<button class="btn btn-success" id="btnResumeCrawlModal">')
          .html('<i data-feather="play"></i> Tiếp tục')
          .on('click', function(e) {
            e.preventDefault();
            self.resumeCrawl();
          });
        container.append(resumeBtn);
      }
      
      // Retry button for completed/failed
      if (['completed', 'failed'].includes(statusLower)) {
        var retryBtn = $('<button class="btn btn-info" id="btnRetryCrawlModal">')
          .html('<i data-feather="refresh-cw"></i> Thử lại')
          .on('click', function(e) {
            e.preventDefault();
            self.retryCrawl();
          });
        container.append(retryBtn);
      }
      
      // Cancel button for running/paused
      if (['running', 'paused'].includes(statusLower)) {
        var cancelBtn = $('<button class="btn btn-danger" id="btnCancelCrawlModal">')
          .html('<i data-feather="x-circle"></i> Hủy')
          .on('click', function(e) {
            e.preventDefault();
            self.deleteCrawl();
          });
        container.append(cancelBtn);
      }
      
      // Delete button for completed/failed/cancelled
      if (['completed', 'failed', 'cancelled'].includes(statusLower)) {
        var deleteBtn = $('<button class="btn btn-danger" id="btnDeleteCrawlModal">')
          .html('<i data-feather="trash-2"></i> Xóa')
          .on('click', function(e) {
            e.preventDefault();
            self.deleteCrawl();
          });
        container.append(deleteBtn);
      }
      
      // Initialize Feather icons for new buttons
      if (typeof feather !== 'undefined') {
        feather.replace();
      }
    },

    /**
     * Show message
     */
    showMessage: function (message, isError) {
      var alertClass = isError ? 'alert-danger' : 'alert-success';
      $('#crawlOutput').html('<div class="alert ' + alertClass + '">' + message + '</div>');
    },

    /**
     * Update crawl action buttons based on status (deprecated - now using main button)
     * Kept for backward compatibility but no longer used
     */
    updateCrawlActionButtons: function (status) {
      // This method is no longer used - buttons are now integrated into main crawlButton
      // Keeping for backward compatibility
    },

    /**
     * Pause crawl
     */
    pauseCrawl: function () {
      if (!this.currentJobId) {
        this.showMessage('Không tìm thấy job ID', true);
        return;
      }
      if (!confirm('Bạn có chắc muốn tạm dừng crawl này?')) {
        return;
      }
      var self = this;
      var jobId = this.currentJobId;
      
      // Stop timer immediately when pause is triggered
      if (this.jobStartTime && !this.isPaused) {
        this.pauseStartTime = Date.now();
        this.stopElapsedTimeTimer();
      }
      
      ApiClient.post('/api/admin/comic-crawls/' + jobId + '/pause?reason=' + encodeURIComponent('Paused by user'), {}, true)
        .done(function (response) {
          if (response.success) {
            self.appendProgressLog('Crawl đã được tạm dừng');
            self.updateMainCrawlButton('paused', $('#crawlButton'));
            // Update secondary buttons
            self.updateSecondaryButtons('paused');
            // Update status badge
            $('#statusBadge').attr('class', 'badge bg-warning').text('Tạm dừng');
          } else {
            self.showMessage('Lỗi khi tạm dừng crawl: ' + (response.message || 'Unknown error'), true);
            // Resume timer if pause failed
            if (self.pauseStartTime) {
              self.pauseStartTime = null;
              self.startElapsedTimeTimer();
            }
          }
        })
        .fail(function (xhr) {
          var errorMsg = xhr.responseJSON?.message || 'Lỗi khi tạm dừng crawl: ' + xhr.status;
          self.showMessage(errorMsg, true);
          // Resume timer if pause failed
          if (self.pauseStartTime) {
            self.pauseStartTime = null;
            self.startElapsedTimeTimer();
          }
        });
    },

    /**
     * Resume crawl
     */
    resumeCrawl: function () {
      if (!this.currentJobId) {
        this.showMessage('Không tìm thấy job ID', true);
        return;
      }
      var self = this;
      var jobId = this.currentJobId;
      
      // Accumulate paused time before resuming
      if (this.pauseStartTime) {
        this.pausedElapsedTime += (Date.now() - this.pauseStartTime);
        this.pauseStartTime = null;
      }
      
      ApiClient.post('/api/admin/comic-crawls/' + jobId + '/resume', {}, true)
        .done(function (response) {
          if (response.success) {
            self.appendProgressLog('Crawl đã được tiếp tục');
            self.updateMainCrawlButton('running', $('#crawlButton'));
            // Update status badge
            $('#statusBadge').attr('class', 'badge bg-info').text('Đang chạy');
            // Restart timer
            self.startElapsedTimeTimer();
            // Reconnect WebSocket and resubscribe (was disconnected when paused)
            self.startWebSocketConnection(jobId, $('#crawlButton'), false);
            // Update secondary buttons
            self.updateSecondaryButtons('running');
          } else {
            self.showMessage('Lỗi khi tiếp tục crawl: ' + (response.message || 'Unknown error'), true);
          }
        })
        .fail(function (xhr) {
          var errorMsg = xhr.responseJSON?.message || 'Lỗi khi tiếp tục crawl: ' + xhr.status;
          self.showMessage(errorMsg, true);
        });
    },

    /**
     * Retry crawl
     */
    retryCrawl: function () {
      if (!this.currentJobId) {
        this.showMessage('Không tìm thấy job ID', true);
        return;
      }
      if (!confirm('Bạn có chắc muốn thử lại crawl này? Tất cả dữ liệu đã crawl sẽ bị xóa và crawl sẽ bắt đầu lại từ đầu.')) {
        return;
      }
      var self = this;
      var jobId = this.currentJobId;
      ApiClient.post('/api/admin/comic-crawls/' + jobId + '/retry', {}, true)
        .done(function (response) {
          if (response.success) {
            self.appendProgressLog('Crawl đã được thử lại');
            // Update currentJobId if new one is returned
            if (response.data) {
              self.currentJobId = response.data;
            }
            // Reset state
            self.jobStartTime = null;
            self.pausedElapsedTime = 0;
            self.pauseStartTime = null;
            self.isPaused = false;
            
            self.updateMainCrawlButton('running', $('#crawlButton'));
            // Update secondary buttons
            self.updateSecondaryButtons('running');
            // Update status badge
            $('#statusBadge').attr('class', 'badge bg-info').text('Đang chạy');
            // Reset progress bars
            $('#progressChapterBar').css('width', '0%').attr('aria-valuenow', 0).text('0%').addClass('progress-bar-striped progress-bar-animated');
            $('#progressImageBar').css('width', '0%').attr('aria-valuenow', 0).text('0%').addClass('progress-bar-striped progress-bar-animated');
            $('#progressChapterText').text('0 / 0 (0%)');
            $('#progressImageText').text('0 / 0 (0%)');
            $('#progressChapterList').html('<div class="text-muted text-center">Chưa có thông tin chapters...</div>');
            $('#progressTime').empty();
            // Reconnect WebSocket (was disconnected when completed/failed)
            self.startWebSocketConnection(self.currentJobId, $('#crawlButton'), false);
          } else {
            self.showMessage('Lỗi khi thử lại crawl: ' + (response.message || 'Unknown error'), true);
          }
        })
        .fail(function (xhr) {
          var errorMsg = xhr.responseJSON?.message || 'Lỗi khi thử lại crawl: ' + xhr.status;
          self.showMessage(errorMsg, true);
        });
    },

    /**
     * Delete crawl
     */
    deleteCrawl: function () {
      if (!this.currentJobId) {
        this.showMessage('Không tìm thấy job ID', true);
        return;
      }
      if (!confirm('Bạn có chắc muốn xóa crawl này? Hành động này không thể hoàn tác.')) {
        return;
      }
      var self = this;
      var jobId = this.currentJobId;
      ApiClient.post('/api/admin/comic-crawls/' + jobId + '/cancel?reason=' + encodeURIComponent('Deleted by user'), {}, true)
        .done(function (response) {
          if (response.success) {
            self.showMessage('Crawl đã được hủy/xóa thành công', false);
            self.cleanupWebSocket();
            
            // Close modal
            var modalElement = document.getElementById('crawlProgressModal');
            if (modalElement) {
              var modal = bootstrap.Modal.getInstance(modalElement);
              if (modal) {
                modal.hide();
              }
            }
            
            $('#crawlProgressContent').empty();
            self.currentJobId = null;
            self.jobStartTime = null;
            self.pausedElapsedTime = 0;
            self.pauseStartTime = null;
            self.isPaused = false;
            self.updateMainCrawlButton(null, $('#crawlButton'));
          } else {
            self.showMessage('Lỗi khi xóa crawl: ' + (response.message || 'Unknown error'), true);
          }
        })
        .fail(function (xhr) {
          var errorMsg = xhr.responseJSON?.message || 'Lỗi khi xóa crawl: ' + xhr.status;
          self.showMessage(errorMsg, true);
        });
    },

    /**
     * Close crawl progress
     */
    /**
     * Reset crawl state without reloading page
     */
    resetCrawlState: function () {
      // Cleanup WebSocket
      this.cleanupWebSocket();
      
      // Close modal if open
      var modalElement = document.getElementById('crawlProgressModal');
      if (modalElement) {
        var modal = bootstrap.Modal.getInstance(modalElement);
        if (modal) {
          modal.hide();
        }
      }
      
      // Clear progress content
      $('#crawlProgressContent').empty();
      $('#crawlProgressButtonContainer').hide();
      
      // Reset state variables
      this.currentJobId = null;
      this.jobStartTime = null;
      this.pausedElapsedTime = 0;
      this.pauseStartTime = null;
      this.isPaused = false;
      this.lastProgressUpdateTime = null;
      
      // Reset button to show "Bắt đầu tải"
      this.updateMainCrawlButton(null, $('#crawlButton'));
      
      // Keep form configuration intact - user doesn't need to re-enter
    },

    closeCrawlProgress: function () {
      // Close modal
      var modalElement = document.getElementById('crawlProgressModal');
      if (modalElement) {
        var modal = bootstrap.Modal.getInstance(modalElement);
        if (modal) {
          modal.hide();
        }
      }
      
      // Cleanup WebSocket but keep progress data for reopening
      this.cleanupWebSocket();
            // Button will be updated to show "Xem tiến độ crawl"
      this.updateMainCrawlButton('initial', $('#crawlButton'));
    }
  };

  // Initialize on document ready
  $(document).ready(function () {
    CrawlModule.init();
  });

  // Expose module globally
  window.CrawlModule = CrawlModule;

})(jQuery);

