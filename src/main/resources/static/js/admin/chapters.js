/**
 * Chapters management page initialization script
 */
(function () {
  'use strict';

  let chaptersTable;
  let currentPage = 0;
  const pageSize = 20;
  let selectedChapterIds = [];
  let currentFilters = {
    comicId: '',
    search: ''
  };

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }

    loadComics();
    initializeTable();
    setupEventHandlers();

    // Check if comicId is in URL params
    const urlParams = new URLSearchParams(window.location.search);
    const comicId = urlParams.get('comicId');
    if (comicId) {
      $('#comic-select').val(comicId);
      currentFilters.comicId = comicId;
      loadChapters();
    }
  });

  function initializeTable() {
    chaptersTable = $('#chaptersTable').DataTable({
      language: {
        url: '//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json'
      },
      pageLength: pageSize,
      responsive: true,
      processing: true,
      paging: true,
      searching: false,
      ordering: true,
      columnDefs: [
        {orderable: false, targets: [0, 7]},
        {searchable: false, targets: [0, 1, 5, 7]}
      ]
    });
  }

  function setupEventHandlers() {
    $('#comic-select').on('change', function () {
      currentFilters.comicId = $(this).val();
      loadChapters();
    });

    $('#applyFilters').on('click', function () {
      currentFilters.comicId = $('#comic-select').val();
      currentFilters.search = $('#filterSearch').val();
      currentPage = 0;
      loadChapters();
    });

    $('#resetFilters').on('click', function () {
      $('#filterForm')[0].reset();
      currentFilters = {comicId: '', search: ''};
      currentPage = 0;
      loadChapters();
    });

    // Select all checkbox
    $('#selectAll').on('change', function () {
      const isChecked = $(this).prop('checked');
      $('.chapter-checkbox').prop('checked', isChecked);
      updateSelectedChapters();
    });

    // Individual checkbox
    $(document).on('change', '.chapter-checkbox', function () {
      updateSelectedChapters();
    });

    // Bulk actions
    $('#bulkRestore').on('click', function () {
      if (selectedChapterIds.length === 0) {
        showToast('Vui lòng chọn ít nhất một chapter', false);
        return;
      }
      bulkRestore(selectedChapterIds);
    });

    $('#bulkSoftDelete').on('click', function () {
      if (selectedChapterIds.length === 0) {
        showToast('Vui lòng chọn ít nhất một chapter', false);
        return;
      }
      if (confirm('Bạn có chắc muốn soft delete ' + selectedChapterIds.length + ' chapters?')) {
        bulkSoftDelete(selectedChapterIds);
      }
    });

    $('#bulkHardDelete').on('click', function () {
      if (selectedChapterIds.length === 0) {
        showToast('Vui lòng chọn ít nhất một chapter', false);
        return;
      }
      if (confirm('Bạn có chắc muốn hard delete ' + selectedChapterIds.length + ' chapters? Hành động này không thể hoàn tác!')) {
        bulkHardDelete(selectedChapterIds);
      }
    });

    $('#bulkDeselectAll').on('click', function () {
      selectedChapterIds = [];
      $('.chapter-checkbox, #selectAll').prop('checked', false);
      updateBulkActionsToolbar();
    });

    // View detail
    $(document).on('click', '.view-detail-btn', function () {
      const chapterId = $(this).data('chapter-id');
      viewChapterDetail(chapterId);
    });

    // Restore
    $(document).on('click', '.restore-btn', function () {
      const chapterId = $(this).data('chapter-id');
      restoreChapter(chapterId);
    });

    // Soft delete
    $(document).on('click', '.soft-delete-btn', function () {
      const chapterId = $(this).data('chapter-id');
      if (confirm('Bạn có chắc muốn soft delete chapter này?')) {
        softDeleteChapter(chapterId);
      }
    });

    // Hard delete
    $(document).on('click', '.hard-delete-btn', function () {
      const chapterId = $(this).data('chapter-id');
      if (confirm('Bạn có chắc muốn hard delete chapter này? Hành động này không thể hoàn tác!')) {
        hardDeleteChapter(chapterId);
      }
    });
  }

  function loadComics() {
    ApiClient.get('/api/admin/comics', {page: 0, size: 1000}, true).done(function (response) {
      if (response.success && response.data) {
        const $select = $('#comic-select');
        response.data.content.forEach(function (comic) {
          $select.append('<option value="' + comic.id + '">' + escapeHtml(comic.name) + '</option>');
        });
      }
    });
  }

  function loadChapters() {
    const params = {
      page: currentPage,
      size: pageSize
    };

    if (currentFilters.comicId) {
      params.comicId = currentFilters.comicId;
    }
    if (currentFilters.search) {
      params.search = currentFilters.search;
    }

    ApiClient.get('/api/admin/chapters', params, true).done(function (response) {
      if (response.success && response.data) {
        const chapters = response.data.content || [];
        const $tbody = $('#chapters-tbody');
        $tbody.empty();

        if (chapters.length === 0) {
          $tbody.append('<tr><td colspan="8" class="text-center">Không tìm thấy chapter nào.</td></tr>');
        } else {
          chapters.forEach(function (chapter) {
            const row = buildChapterRow(chapter);
            $tbody.append(row);
          });
        }

        chaptersTable.clear().rows.add($tbody.find('tr')).draw();
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi tải danh sách chapters'), false);
    });
  }

  function buildChapterRow(chapter) {
    const sourceUrl = chapter.source ? '<a href="' + escapeHtml(chapter.source) + '" target="_blank" title="' + escapeHtml(chapter.source) + '"><i class="fas fa-external-link-alt"></i></a>' : '-';

    return `
      <tr>
        <td><input type="checkbox" class="chapter-checkbox" value="${chapter.id}"></td>
        <td>${chapter.id}</td>
        <td>${escapeHtml(chapter.chapterName)}</td>
        <td>${escapeHtml(chapter.chapterTitle || '-')}</td>
        <td>${sourceUrl}</td>
        <td>-</td>
        <td>${formatDate(chapter.createdAt)}</td>
        <td>
          <div class="btn-group btn-group-sm">
            <button class="btn btn-info view-detail-btn" data-chapter-id="${chapter.id}" title="Xem chi tiết">
              <i class="fas fa-eye"></i>
            </button>
            <button class="btn btn-success restore-btn" data-chapter-id="${chapter.id}" title="Restore">
              <i class="fas fa-undo"></i>
            </button>
            <button class="btn btn-warning soft-delete-btn" data-chapter-id="${chapter.id}" title="Soft Delete">
              <i class="fas fa-trash"></i>
            </button>
            <button class="btn btn-danger hard-delete-btn" data-chapter-id="${chapter.id}" title="Hard Delete">
              <i class="fas fa-trash-alt"></i>
            </button>
          </div>
        </td>
      </tr>
    `;
  }

  function updateSelectedChapters() {
    selectedChapterIds = [];
    $('.chapter-checkbox:checked').each(function () {
      selectedChapterIds.push(parseInt($(this).val()));
    });
    updateBulkActionsToolbar();
  }

  function updateBulkActionsToolbar() {
    if (selectedChapterIds.length > 0) {
      $('#bulkActionsToolbar').show();
      $('#selectedCount').text(selectedChapterIds.length);
    } else {
      $('#bulkActionsToolbar').hide();
    }
  }

  function bulkRestore(chapterIds) {
    ApiClient.post('/api/admin/chapters/bulk/restore', chapterIds, true).done(function (response) {
      if (response.success) {
        showToast(response.message || 'Restore chapters thành công', true);
        selectedChapterIds = [];
        $('.chapter-checkbox, #selectAll').prop('checked', false);
        updateBulkActionsToolbar();
        loadChapters();
      } else {
        showToast(response.message || 'Lỗi khi restore chapters', false);
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi restore chapters'), false);
    });
  }

  function bulkSoftDelete(chapterIds) {
    ApiClient.post('/api/admin/chapters/bulk/delete', chapterIds, true).done(function (response) {
      if (response.success) {
        showToast(response.message || 'Soft delete chapters thành công', true);
        selectedChapterIds = [];
        $('.chapter-checkbox, #selectAll').prop('checked', false);
        updateBulkActionsToolbar();
        loadChapters();
      } else {
        showToast(response.message || 'Lỗi khi soft delete chapters', false);
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi soft delete chapters'), false);
    });
  }

  function bulkHardDelete(chapterIds) {
    ApiClient.post('/api/admin/chapters/bulk/hard-delete', chapterIds, true).done(function (response) {
      if (response.success) {
        showToast(response.message || 'Hard delete chapters thành công', true);
        selectedChapterIds = [];
        $('.chapter-checkbox, #selectAll').prop('checked', false);
        updateBulkActionsToolbar();
        loadChapters();
      } else {
        showToast(response.message || 'Lỗi khi hard delete chapters', false);
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi hard delete chapters'), false);
    });
  }

  function viewChapterDetail(chapterId) {
    // Load chapter info and images in parallel
    Promise.all([
      ApiClient.get('/api/admin/chapters/' + chapterId, null, true),
      ApiClient.get('/api/admin/images/chapter/' + chapterId, null, true)
    ]).then(function (responses) {
      const chapterResponse = responses[0];
      const imagesResponse = responses[1];
      if (chapterResponse.success && chapterResponse.data && imagesResponse.success && imagesResponse.data) {
        const chapter = chapterResponse.data;
        const images = imagesResponse.data;
        displayChapterDetail(chapter, images);
      }
    }).catch(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi tải chi tiết chapter'), false);
    });
  }

  function displayChapterDetail(chapter, images) {
    const content = buildChapterDetailContent(chapter, images);
    $('#chapterDetailContent').html(content);
    $('#chapterDetailModal').data('chapter-id', chapter.id);
    const modal = new bootstrap.Modal(document.getElementById('chapterDetailModal'));
    modal.show();
    initializeImageDragDrop(chapter.id);
    // Re-initialize Feather icons
    if (typeof feather !== 'undefined') {
      feather.replace();
    }
  }

  function buildChapterDetailContent(chapter, images) {
    return `
      <div class="row mb-3">
        <div class="col-md-12">
          <h5>${escapeHtml(chapter.chapterName)}</h5>
          <p><strong>Title:</strong> ${escapeHtml(chapter.chapterTitle || '-')}</p>
          <p><strong>Source:</strong> <a href="${escapeHtml(chapter.source || '')}" target="_blank">${escapeHtml(chapter.source || '-')}</a></p>
          <p><strong>Total Images:</strong> ${images.length}</p>
        </div>
      </div>
      <div class="row">
        <div class="col-md-12">
          <div class="btn-group mb-3">
            <button type="button" class="btn btn-sm btn-primary" id="toggleVisibilityAll">
              <i class="fas fa-eye"></i> Toggle All Visibility
            </button>
            <button type="button" class="btn btn-sm btn-success" id="saveImageOrder">
              <i class="fas fa-save"></i> Lưu thứ tự
            </button>
          </div>
          <div id="imageGrid" class="row">
            ${images.map((img, index) => buildImageCard(img, index)).join('')}
          </div>
        </div>
      </div>
    `;
  }

  function buildImageCard(image, index) {
    const visibilityClass = image.isVisible ? 'fa-eye' : 'fa-eye-slash';
    const visibilityText = image.isVisible ? 'Hide' : 'Show';
    const downloadedBadge = image.isDownloaded ? '<span class="badge badge-success">Downloaded</span>' : '<span class="badge badge-warning">Not Downloaded</span>';

    return `
      <div class="col-md-3 mb-3 image-item" data-image-id="${image.id}">
        <div class="card">
          <img src="${escapeHtml(image.path || image.originalUrl || '')}" class="card-img-top" alt="Image ${index + 1}" style="max-height: 200px; object-fit: contain;">
          <div class="card-body">
            <p class="card-text">
              <small>Order: ${image.imageOrder}</small><br>
              ${downloadedBadge}
            </p>
            <div class="btn-group btn-group-sm btn-block">
              <button class="btn btn-info toggle-visibility-btn" data-image-id="${image.id}" data-visible="${image.isVisible}">
                <i class="fas ${visibilityClass}"></i> ${visibilityText}
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

  function initializeImageDragDrop(chapterId) {
    const imageGrid = document.getElementById('imageGrid');
    if (imageGrid && typeof Sortable !== 'undefined') {
      new Sortable(imageGrid, {
        animation: 150,
        onEnd: function (evt) {
          const imageItems = Array.from(imageGrid.querySelectorAll('.image-item'));
          const newOrder = [];
          imageItems.forEach(function (item, index) {
            const imageId = item.getAttribute('data-image-id');
            newOrder.push({imageId: imageId, order: index + 1});
          });
          saveImageOrder(chapterId, newOrder);
        }
      });
    }

    // Toggle visibility
    $(document).off('click', '.toggle-visibility-btn').on('click', '.toggle-visibility-btn', function () {
      const imageId = $(this).data('image-id');
      const isVisible = !$(this).data('visible');
      updateImageVisibility(imageId, isVisible);
    });

    // Delete image
    $(document).off('click', '.delete-image-btn').on('click', '.delete-image-btn', function () {
      const imageId = $(this).data('image-id');
      if (confirm('Bạn có chắc muốn xóa image này?')) {
        deleteImage(imageId);
      }
    });

    // Save order button
    $('#saveImageOrder').on('click', function () {
      const imageItems = $('#imageGrid .image-item');
      const newOrder = [];
      imageItems.each(function (index) {
        const imageId = $(this).data('image-id');
        newOrder.push({imageId: imageId, order: index + 1});
      });
      saveImageOrder(chapterId, newOrder);
    });
  }

  function saveImageOrder(chapterId, orderList) {
    // Save order for each image
    const promises = orderList.map(function (item) {
      const url = '/api/admin/images/' + item.imageId + '/order?manualOrder=' + item.order;
      return $.ajax({
        url: ApiClient.baseUrl + url,
        method: 'PUT',
        headers: ApiClient.getHeaders(true),
        dataType: 'json'
      });
    });

    Promise.all(promises).then(function () {
      showToast('Lưu thứ tự images thành công', true);
      // Reload images to reflect new order
      viewChapterDetail(chapterId);
    }).catch(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi lưu thứ tự images'), false);
    });
  }

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
        // Reload images - get chapterId from modal
        const chapterId = $('#chapterDetailModal').data('chapter-id');
        if (chapterId) {
          viewChapterDetail(chapterId);
        }
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi cập nhật visibility'), false);
    });
  }

  function deleteImage(imageId) {
    ApiClient.delete('/api/admin/images/' + imageId, true).done(function (response) {
      if (response.success) {
        showToast('Xóa image thành công', true);
        // Reload images - get chapterId from modal
        const chapterId = $('#chapterDetailModal').data('chapter-id');
        if (chapterId) {
          viewChapterDetail(chapterId);
        }
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi xóa image'), false);
    });
  }

  function restoreChapter(chapterId) {
    ApiClient.post('/api/admin/chapters/' + chapterId + '/restore', null, true).done(function (response) {
      if (response.success) {
        showToast('Restore chapter thành công', true);
        loadChapters();
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi restore chapter'), false);
    });
  }

  function softDeleteChapter(chapterId) {
    ApiClient.delete('/api/admin/chapters/' + chapterId, true).done(function (response) {
      if (response.success) {
        showToast('Soft delete chapter thành công', true);
        loadChapters();
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi soft delete chapter'), false);
    });
  }

  function hardDeleteChapter(chapterId) {
    ApiClient.delete('/api/admin/chapters/' + chapterId + '/hard', true).done(function (response) {
      if (response.success) {
        showToast('Hard delete chapter thành công', true);
        loadChapters();
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi hard delete chapter'), false);
    });
  }

  function formatDate(dateString) {
    if (!dateString) return 'Chưa cập nhật';
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN');
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
