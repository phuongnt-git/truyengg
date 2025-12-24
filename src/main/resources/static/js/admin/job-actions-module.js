/**
 * JobActionsModule - Xử lý các actions: pause, resume, cancel, retry, delete
 */
var JobActionsModule = (function () {

  /**
   * Pause job
   */
  function pauseJob(jobId, reason, onSuccess) {
    ConfirmationModal.show({
      title: 'Xác nhận tạm dừng',
      message: 'Bạn có chắc muốn tạm dừng job này?',
      confirmText: 'Tạm dừng',
      cancelText: 'Hủy',
      showReasonInput: true,
      onConfirm: function (data) {
        var url = '/api/admin/comic-crawls/' + jobId + '/pause';
        if (data.reason) {
          url += '?reason=' + encodeURIComponent(data.reason);
        }

        ApiClient.post(url, {}, true)
          .done(function (response) {
            if (response.success) {
              showSuccess('Job đã được tạm dừng');
              if (onSuccess) onSuccess();
              JobsModule.refresh();
            } else {
              showError('Lỗi: ' + (response.message || 'Không thể tạm dừng job'));
            }
          })
          .fail(function (xhr) {
            var errorMsg = xhr.responseJSON?.message || 'Lỗi khi tạm dừng job';
            showError(errorMsg);
          });
      }
    });
  }

  /**
   * Resume job
   */
  function resumeJob(jobId, onSuccess) {
    ConfirmationModal.show({
      title: 'Xác nhận tiếp tục',
      message: 'Bạn có chắc muốn tiếp tục job này?',
      confirmText: 'Tiếp tục',
      cancelText: 'Hủy',
      showReasonInput: false,
      onConfirm: function (data) {
        ApiClient.post('/api/admin/comic-crawls/' + jobId + '/resume', {}, true)
          .done(function (response) {
            if (response.success) {
              showSuccess('Job đã được tiếp tục');
              if (onSuccess) onSuccess();
              JobsModule.refresh();
            } else {
              showError('Lỗi: ' + (response.message || 'Không thể tiếp tục job'));
            }
          })
          .fail(function (xhr) {
            var errorMsg = xhr.responseJSON?.message || 'Lỗi khi tiếp tục job';
            showError(errorMsg);
          });
      }
    });
  }

  /**
   * Cancel job
   */
  function cancelJob(jobId, reason, onSuccess) {
    ConfirmationModal.show({
      title: 'Xác nhận hủy',
      message: 'Bạn có chắc muốn hủy job này? Hành động này không thể hoàn tác.',
      confirmText: 'Hủy job',
      cancelText: 'Đóng',
      showReasonInput: true,
      onConfirm: function (data) {
        var url = '/api/admin/comic-crawls/' + jobId + '/cancel';
        if (data.reason) {
          url += '?reason=' + encodeURIComponent(data.reason);
        }

        ApiClient.post(url, {}, true)
          .done(function (response) {
            if (response.success) {
              showSuccess('Job đã được hủy');
              if (onSuccess) onSuccess();
              JobsModule.refresh();
            } else {
              showError('Lỗi: ' + (response.message || 'Không thể hủy job'));
            }
          })
          .fail(function (xhr) {
            var errorMsg = xhr.responseJSON?.message || 'Lỗi khi hủy job';
            showError(errorMsg);
          });
      }
    });
  }

  /**
   * Retry job
   */
  function retryJob(jobId, onSuccess) {
    ConfirmationModal.show({
      title: 'Xác nhận retry',
      message: 'Bạn có chắc muốn retry job này? Tất cả dữ liệu đã crawl (checkpoint, progress, files) sẽ bị xóa và job sẽ bắt đầu lại từ đầu.',
      confirmText: 'Retry',
      cancelText: 'Hủy',
      showReasonInput: false,
      onConfirm: function (data) {
        ApiClient.post('/api/admin/comic-crawls/' + jobId + '/retry', {}, true)
          .done(function (response) {
            if (response.success) {
              showSuccess('Job đã được retry');
              if (onSuccess) onSuccess();
              JobsModule.refresh();
            } else {
              showError('Lỗi: ' + (response.message || 'Không thể retry job'));
            }
          })
          .fail(function (xhr) {
            var errorMsg = xhr.responseJSON?.message || 'Lỗi khi retry job';
            showError(errorMsg);
          });
      }
    });
  }

  /**
   * Delete job
   */
  function deleteJob(jobId, hardDelete, onSuccess) {
    var message = 'Bạn có chắc muốn xóa job này?';

    ConfirmationModal.show({
      title: 'Xác nhận xóa',
      message: message,
      confirmText: 'Xóa',
      cancelText: 'Hủy',
      showReasonInput: false,
      showHardDeleteCheckbox: true,
      onConfirm: function (data) {
        var url = '/api/admin/comic-crawls/' + jobId;
        if (data.hardDelete) {
          url += '?hardDelete=true';
        }

        ApiClient.delete(url, true)
          .done(function (response) {
            if (response.success) {
              showSuccess(response.message || 'Job đã được xóa');
              if (onSuccess) onSuccess();
              JobsModule.refresh();
            } else {
              showError('Lỗi: ' + (response.message || 'Không thể xóa job'));
            }
          })
          .fail(function (xhr) {
            var errorMsg = xhr.responseJSON?.message || 'Lỗi khi xóa job';
            showError(errorMsg);
          });
      }
    });
  }

  /**
   * Restore job
   */
  function restoreJob(jobId) {
    ApiClient.post('/api/admin/comic-crawls/' + jobId + '/restore', {}, true)
      .done(function (response) {
        if (response.success) {
          showSuccess('Job đã được khôi phục');
          JobsModule.refresh();
        } else {
          showError('Lỗi: ' + (response.message || 'Không thể khôi phục job'));
        }
      })
      .fail(function (xhr) {
        var errorMsg = xhr.responseJSON?.message || 'Lỗi khi khôi phục job';
        showError(errorMsg);
      });
  }

  /**
   * Show success message
   */
  function showSuccess(message) {
    // You can use Toastify or similar library here
    if (typeof Toastify !== 'undefined') {
      Toastify({
        text: message,
        duration: 3000,
        gravity: "top",
        position: "right",
        backgroundColor: "#28a745"
      }).showToast();
    } else {
      alert(message);
    }
  }

  /**
   * Show error message
   */
  function showError(message) {
    if (typeof Toastify !== 'undefined') {
      Toastify({
        text: message,
        duration: 5000,
        gravity: "top",
        position: "right",
        backgroundColor: "#dc3545"
      }).showToast();
    } else {
      alert(message);
    }
  }

  /**
   * Initialize event handlers
   */
  function init() {
    // Use event delegation for dynamic buttons
    $(document).on('click', '.btn-pause-job', function () {
      var jobId = $(this).data('job-id');
      pauseJob(jobId);
    });

    $(document).on('click', '.btn-resume-job', function () {
      var jobId = $(this).data('job-id');
      resumeJob(jobId);
    });

    $(document).on('click', '.btn-cancel-job', function () {
      var jobId = $(this).data('job-id');
      cancelJob(jobId);
    });

    $(document).on('click', '.btn-delete-job', function () {
      var jobId = $(this).data('job-id');
      deleteJob(jobId, false);
    });

    $(document).on('click', '.btn-retry-job', function () {
      var jobId = $(this).data('job-id');
      retryJob(jobId);
    });
  }

  return {
    init: init,
    pauseJob: pauseJob,
    resumeJob: resumeJob,
    cancelJob: cancelJob,
    retryJob: retryJob,
    deleteJob: deleteJob,
    restoreJob: restoreJob
  };
})();

// Initialize on document ready
$(document).ready(function () {
  JobActionsModule.init();
});

