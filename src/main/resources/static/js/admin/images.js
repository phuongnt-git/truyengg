/**
 * Images management page initialization script
 */
(function () {
    'use strict';

    $(document).ready(function () {
        if (!ApiClient.getToken()) {
            var currentPath = window.location.pathname;
            window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
            return;
        }

        loadImageStats();
        setupEventHandlers();
    });

    function setupEventHandlers() {
        $('#searchImagesBtn').on('click', function () {
            $('#searchComicId').val();
            const chapterId = $('#searchChapterId').val();
            if (!chapterId) {
                showToast('Vui lòng nhập Chapter ID', false);
                return;
            }
            searchImages(chapterId);
        });
    }

    function loadImageStats() {
        ApiClient.get('/api/admin/images/stats', null, true).done(function (response) {
            if (response.success && response.data) {
                const stats = response.data;
                $('#totalImages').text(stats.totalImages || 0);
                $('#downloadedImages').text(stats.downloadedImages || 0);
                $('#visibleImages').text(stats.visibleImages || 0);
                $('#notDownloadedImages').text(stats.notDownloadedImages || 0);
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi tải statistics'), false);
        });
    }

    function searchImages(chapterId) {
        ApiClient.get('/api/admin/images/chapter/' + chapterId, null, true).done(function (response) {
            if (response.success && response.data) {
                const images = response.data;
                displayImages(images);
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi tải images'), false);
        });
    }

    function displayImages(images) {
        const $container = $('#imagesList');
        $container.empty();

        if (images.length === 0) {
            $container.html('<p class="text-center text-muted">Không tìm thấy images nào.</p>');
            return;
        }

        let html = '<div class="row">';
        images.forEach(function (image) {
            html += buildImageCard(image);
        });
        html += '</div>';
        $container.html(html);
    }

    function buildImageCard(image) {
        const visibilityClass = image.isVisible ? 'fa-eye text-success' : 'fa-eye-slash text-muted';
        const downloadedBadge = image.isDownloaded ? '<span class="badge badge-success">Downloaded</span>' : '<span class="badge badge-warning">Not Downloaded</span>';
        const deletedBadge = image.deletedAt ? '<span class="badge badge-danger">Deleted</span>' : '';

        return `
      <div class="col-md-3 mb-3">
        <div class="card">
          <img src="${escapeHtml(image.path || image.originalUrl || '')}" class="card-img-top" alt="Image" style="max-height: 200px; object-fit: contain;">
          <div class="card-body">
            <p class="card-text">
              <small>ID: ${image.id}</small><br>
              <small>Order: ${image.imageOrder}</small><br>
              ${downloadedBadge} ${deletedBadge}
            </p>
            <div class="btn-group btn-group-sm btn-block">
              <button class="btn btn-info toggle-visibility-btn" data-image-id="${image.id}" data-visible="${image.isVisible}">
                <i class="fas ${visibilityClass}"></i>
              </button>
              <button class="btn btn-success restore-image-btn" data-image-id="${image.id}" style="${image.deletedAt ? '' : 'display:none;'}">
                <i class="fas fa-undo"></i>
              </button>
              <button class="btn btn-danger delete-image-btn" data-image-id="${image.id}">
                <i class="fas fa-trash"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    `;
    }

    $(document).on('click', '.toggle-visibility-btn', function () {
        const imageId = $(this).data('image-id');
        const isVisible = !$(this).data('visible');
        updateImageVisibility(imageId, isVisible);
    });

    $(document).on('click', '.restore-image-btn', function () {
        const imageId = $(this).data('image-id');
        restoreImage(imageId);
    });

    $(document).on('click', '.delete-image-btn', function () {
        const imageId = $(this).data('image-id');
        if (confirm('Bạn có chắc muốn xóa image này?')) {
            deleteImage(imageId);
        }
    });

    function updateImageVisibility(imageId, isVisible) {
        const url = '/api/admin/images/' + imageId + '/visibility?isVisible=' + isVisible;
        $.ajax({
            url: ApiClient.baseUrl + url,
            method: 'PUT',
            headers: ApiClient.getHeaders(true),
            dataType: 'json'
        }).done(function (response) {
            if (response.success) {
                showToast('Cập nhật visibility thành công', true);
                const chapterId = $('#searchChapterId').val();
                if (chapterId) {
                    searchImages(chapterId);
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi cập nhật visibility'), false);
        });
    }

    function restoreImage(imageId) {
        ApiClient.post('/api/admin/images/' + imageId + '/restore', null, true).done(function (response) {
            if (response.success) {
                showToast('Restore image thành công', true);
                const chapterId = $('#searchChapterId').val();
                if (chapterId) {
                    searchImages(chapterId);
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi restore image'), false);
        });
    }

    function deleteImage(imageId) {
        ApiClient.delete('/api/admin/images/' + imageId, true).done(function (response) {
            if (response.success) {
                showToast('Xóa image thành công', true);
                const chapterId = $('#searchChapterId').val();
                if (chapterId) {
                    searchImages(chapterId);
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi xóa image'), false);
        });
    }

    function escapeHtml(text) {
        if (!text) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.toString().replace(/[&<>"']/g, m => map[m]);
    }
})();

