/**
 * Category Crawl Module - Quản lý crawl theo thể loại với WebSocket real-time updates
 */
(function ($) {
  'use strict';

  var CategoryCrawlModule = {
    stompClient: null,
    socket: null,
    wsSubscription: null,
    currentJobId: null,

    init: function () {
      if (!ApiClient.getToken()) {
        window.location.href = '/';
        return;
      }

      this.bindEvents();
      this.loadJobs();
    },

    bindEvents: function () {
      var self = this;

      $('#categoryCrawlForm').on('submit', function (e) {
        e.preventDefault();
        self.handleFormSubmit();
      });

      $(window).on('beforeunload', function () {
        self.cleanupWebSocket();
      });
    },

    handleFormSubmit: function () {
      var self = this;
      var startButton = $('#startButton');
      startButton.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Đang khởi tạo...');

      var source = $('#source').val();
      var categoryUrl = $('#categoryUrl').val().trim();

      if (!categoryUrl) {
        this.showMessage('Vui lòng nhập đường link thể loại.', true);
        startButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu crawl');
        return;
      }

      var requestData = {
        categoryUrl: categoryUrl,
        source: source
      };

      ApiClient.post('/admin/category-crawl/start', requestData, true)
        .done(function (response) {
          if (response.success && response.data) {
            self.currentJobId = response.data.id;
            
            // Check if job was queued (PENDING status)
            if (response.message && response.message.includes('queued')) {
              self.showMessage('Category crawl job đã được tạo với ID: ' + self.currentJobId + '. Job đang chờ slot để bắt đầu.', false);
              self.loadJobs();
              // Don't connect WebSocket for PENDING jobs
            } else {
              self.showMessage('Category crawl job đã được tạo với ID: ' + self.currentJobId, false);
              self.loadJobs();
              self.connectWebSocket(self.currentJobId);
            }
          } else {
            self.showMessage('Lỗi: ' + (response.message || 'Không thể tạo job'), true);
            startButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu crawl');
          }
        })
        .fail(function (xhr) {
          var errorMsg = handleApiError(xhr, 'Không thể tạo category crawl job');
          self.showMessage(errorMsg, true);
          startButton.prop('disabled', false).html('<i class="fas fa-download"></i> Bắt đầu crawl');
        });
    },

    loadJobs: function () {
      var self = this;
      ApiClient.get('/admin/category-crawl/jobs', {size: 20}, false)
        .done(function (response) {
          if (response.success && response.data) {
            self.renderJobs(response.data.content || []);
          }
        })
        .fail(function (xhr) {
          console.error('Error loading jobs:', xhr);
        });
    },

    renderJobs: function (jobs) {
      var $container = $('#jobsList');
      $container.empty();

      if (jobs.length === 0) {
        $container.html('<p class="text-muted">Chưa có job nào.</p>');
        return;
      }

      jobs.forEach(function (job) {
        var jobCard = this.createJobCard(job);
        $container.append(jobCard);
      }.bind(this));
    },

    createJobCard: function (job) {
      var statusClass = this.getStatusClass(job.status);
      var statusText = this.getStatusText(job.status);
      var progressPercent = job.totalStories > 0
        ? Math.round((job.crawledStories / job.totalStories) * 100)
        : 0;

      var progressBarClass = 'progress-bar';
      var progressBarStyle = 'width: ' + progressPercent + '%';
      var progressText = progressPercent + '%';
      
      if (job.status === 'PENDING') {
        progressBarClass += ' progress-bar-striped progress-bar-animated bg-secondary';
        progressText = 'Đang chờ...';
      } else if (job.status === 'RUNNING') {
        progressBarClass += ' bg-primary';
      } else if (job.status === 'COMPLETED') {
        progressBarClass += ' bg-success';
      } else if (job.status === 'FAILED') {
        progressBarClass += ' bg-danger';
      }

      var card = $('<div>').addClass('job-card')
        .html(`
          <div class="d-flex justify-content-between align-items-center mb-2">
            <h5>Job: ${job.id.substring(0, 8)}...</h5>
            <span class="badge badge-${statusClass}">${statusText}</span>
          </div>
          ${job.status === 'PENDING' ? '<div class="alert alert-info mb-2"><i class="fas fa-info-circle"></i> Job đang chờ slot để bắt đầu (giới hạn: 5 jobs/admin, 25 jobs/server)</div>' : ''}
          <div class="mb-2">
            <small class="text-muted">URL: </small>
            <a href="${job.categoryUrl}" target="_blank">${job.categoryUrl.substring(0, 60)}...</a>
          </div>
          <div class="mb-2">
            <small class="text-muted">Trang: </small>
            <span>${job.crawledPages} / ${job.totalPages}</span>
          </div>
          <div class="mb-2">
            <small class="text-muted">Truyện: </small>
            <span>${job.crawledStories} / ${job.totalStories}</span>
          </div>
          <div class="progress mb-2">
            <div class="${progressBarClass}" role="progressbar" style="${progressBarStyle}">
              ${progressText}
            </div>
          </div>
          <div class="story-details-container" data-job-id="${job.id}">
            <button class="btn btn-sm btn-info toggle-details" data-job-id="${job.id}">
              <i class="fas fa-chevron-down"></i> Xem chi tiết
            </button>
            <div class="story-details-content" style="display: none; margin-top: 10px;"></div>
          </div>
        `);

      // Bind toggle event
      card.find('.toggle-details').on('click', function () {
        var $btn = $(this);
        var jobId = $btn.data('job-id');
        var $content = card.find('.story-details-content');
        
        if ($content.is(':visible')) {
          $content.slideUp();
          $btn.html('<i class="fas fa-chevron-down"></i> Xem chi tiết');
        } else {
          $content.slideDown();
          $btn.html('<i class="fas fa-chevron-up"></i> Ẩn chi tiết');
          this.loadStoryDetails(jobId, $content);
        }
      }.bind(this));

      return card;
    },

    loadStoryDetails: function (jobId, $container) {
      ApiClient.get('/admin/category-crawl/jobs/' + jobId + '/details', null, false)
        .done(function (response) {
          if (response.success && response.data) {
            var details = response.data;
            var html = '<div class="list-group">';
            details.forEach(function (detail) {
              var statusClass = this.getStatusClass(detail.status);
              html += `
                <div class="list-group-item">
                  <div class="d-flex justify-content-between">
                    <div>
                      <strong>${detail.storyTitle || detail.storyUrl.substring(0, 50)}</strong><br>
                      <small>Chapters: ${detail.crawledChapters} / ${detail.totalChapters}</small>
                    </div>
                    <span class="badge badge-${statusClass}">${detail.status}</span>
                  </div>
                </div>
              `;
            }.bind(this));
            html += '</div>';
            $container.html(html);
          }
        }.bind(this))
        .fail(function (xhr) {
          $container.html('<p class="text-danger">Không thể tải chi tiết.</p>');
        });
    },

    getStatusClass: function (status) {
      var statusLower = (status || '').toLowerCase();
      var statusMap = {
        'pending': 'secondary',
        'running': 'primary',
        'completed': 'success',
        'failed': 'danger',
        'cancelled': 'warning',
        'paused': 'info',
        'partial_failed': 'warning'
      };
      return statusMap[statusLower] || 'secondary';
    },

    getStatusText: function (status) {
      var statusMap = {
        'PENDING': 'Đang chờ',
        'RUNNING': 'Đang chạy',
        'PAUSED': 'Tạm dừng',
        'COMPLETED': 'Hoàn thành',
        'FAILED': 'Thất bại',
        'CANCELLED': 'Đã hủy'
      };
      return statusMap[status] || status;
    },

    connectWebSocket: function (jobId) {
      var self = this;
      var jobIdStr = String(jobId);

      // Cleanup existing connection
      this.cleanupWebSocket();

      if (!ApiClient.getToken()) {
        console.warn('No authentication token, cannot connect WebSocket');
        return;
      }

      try {
        var socket = new SockJS('/ws');
        this.socket = socket;
        var stompClient = Stomp.over(socket);

        // Disable debug logging
        stompClient.debug = function () {};

        var token = ApiClient.getToken();
        var headers = {
          'Authorization': 'Bearer ' + token
        };

        stompClient.connect(headers, function () {
          console.log('WebSocket connected for category crawl job:', jobIdStr);
          self.stompClient = stompClient;

          var destination = '/topic/category-crawl-progress/' + jobIdStr;
          self.wsSubscription = stompClient.subscribe(destination, function (message) {
            try {
              var progress = JSON.parse(message.body);
              console.log('Category crawl progress update:', progress);
              self.updateProgressUI(progress, jobIdStr);
            } catch (e) {
              console.error('Error parsing WebSocket message:', e, message.body);
            }
          });

          console.log('Subscribed to category crawl progress:', destination);
        }, function (error) {
          console.error('WebSocket connection error:', error);
          self.cleanupWebSocket();
        });

        this.stompClient = stompClient;
      } catch (e) {
        console.error('Error setting up WebSocket:', e);
        this.cleanupWebSocket();
      }
    },

    updateProgressUI: function (progress, jobId) {
      // Find job card and update it
      var $jobCard = $('.job-card').filter(function () {
        return $(this).find('.toggle-details').data('job-id') === jobId;
      });

      if ($jobCard.length === 0) {
        // Reload jobs list if card not found
        this.loadJobs();
        return;
      }

      // Update progress bar
      var progressPercent = progress.totalStories > 0
        ? Math.round((progress.crawledStories / progress.totalStories) * 100)
        : 0;
      $jobCard.find('.progress-bar')
        .css('width', progressPercent + '%')
        .text(progressPercent + '%');

      // Update story count
      $jobCard.find('.mb-2').eq(2).html(
        '<small class="text-muted">Truyện: </small>' +
        '<span>' + progress.crawledStories + ' / ' + progress.totalStories + '</span>'
      );

      // Update chapter count if available
      if (progress.totalChapters > 0) {
        var chapterInfo = $('<div>').addClass('mb-2').html(
          '<small class="text-muted">Chapters: </small>' +
          '<span>' + progress.crawledChapters + ' / ' + progress.totalChapters + '</span>'
        );
        if ($jobCard.find('.mb-2').length < 4) {
          $jobCard.find('.progress').before(chapterInfo);
        } else {
          $jobCard.find('.mb-2').eq(3).replaceWith(chapterInfo);
        }
      }
    },

    cleanupWebSocket: function () {
      if (this.wsSubscription) {
        this.wsSubscription.unsubscribe();
        this.wsSubscription = null;
      }
      if (this.stompClient) {
        this.stompClient.disconnect();
        this.stompClient = null;
      }
      if (this.socket) {
        this.socket.close();
        this.socket = null;
      }
    },

    showMessage: function (message, isError) {
      var alertClass = isError ? 'alert-danger' : 'alert-success';
      var alert = $('<div>').addClass('alert ' + alertClass).text(message);
      $('#categoryCrawlForm').after(alert);
      setTimeout(function () {
        alert.fadeOut(function () {
          $(this).remove();
        });
      }, 5000);
    }
  };

  // Initialize on document ready
  $(document).ready(function () {
    CategoryCrawlModule.init();
  });

  // Expose module globally
  window.CategoryCrawlModule = CategoryCrawlModule;

})(jQuery);

