/**
 * Comics management page initialization script
 */
(function () {
    'use strict';

    let comicsTable;
    let currentPage = 0;
    const pageSize = 20;
    let selectedComicIds = [];
    let currentFilters = {
        status: '',
        progressStatus: '',
        search: ''
    };

    $(document).ready(function () {
        if (!ApiClient.getToken()) {
            var currentPath = window.location.pathname;
            window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
            return;
        }

        initializeTable();
        loadComics();
        setupEventHandlers();
    });

    function initializeTable() {
        comicsTable = $('#comicsTable').DataTable({
            language: {
                url: '//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json'
            },
            pageLength: pageSize,
            responsive: true,
            processing: true,
            serverSide: false,
            paging: true,
            searching: false,
            ordering: true,
            columnDefs: [
                {orderable: false, targets: [0, 12]},
                {searchable: false, targets: [0, 1, 4, 5, 7, 8, 9, 10, 12]}
            ]
        });
    }

    function setupEventHandlers() {
        // Filter handlers
        $('#applyFilters').on('click', function () {
            currentFilters.status = $('#filterStatus').val();
            currentFilters.progressStatus = $('#filterProgressStatus').val();
            currentFilters.search = $('#filterSearch').val();
            currentPage = 0;
            loadComics();
        });

        $('#resetFilters').on('click', function () {
            $('#filterForm')[0].reset();
            currentFilters = {status: '', progressStatus: '', search: ''};
            currentPage = 0;
            loadComics();
        });

        // Select all checkbox
        $('#selectAll').on('change', function () {
            const isChecked = $(this).prop('checked');
            $('.comic-checkbox').prop('checked', isChecked);
            updateSelectedComics();
        });

        // Individual checkbox
        $(document).on('change', '.comic-checkbox', function () {
            updateSelectedComics();
        });

        // Bulk actions
        $('#bulkUpdateStatus').on('click', function () {
            if (selectedComicIds.length === 0) {
                showToast('Vui lòng chọn ít nhất một comic', false);
                return;
            }
            const modal = new bootstrap.Modal(document.getElementById('bulkStatusModal'));
            modal.show();
        });

        $('#confirmBulkStatus').on('click', function () {
            const newStatus = $('#bulkStatusSelect').val();
            bulkUpdateStatus(selectedComicIds, newStatus);
        });

        $('#bulkArchive').on('click', function () {
            if (selectedComicIds.length === 0) {
                showToast('Vui lòng chọn ít nhất một comic', false);
                return;
            }
            if (confirm('Bạn có chắc muốn archive ' + selectedComicIds.length + ' comics?')) {
                bulkDelete(selectedComicIds);
            }
        });

        $('#bulkDelete').on('click', function () {
            if (selectedComicIds.length === 0) {
                showToast('Vui lòng chọn ít nhất một comic', false);
                return;
            }
            if (confirm('Bạn có chắc muốn xóa ' + selectedComicIds.length + ' comics? Hành động này không thể hoàn tác!')) {
                bulkDelete(selectedComicIds);
            }
        });

        $('#bulkDeselectAll').on('click', function () {
            selectedComicIds = [];
            $('.comic-checkbox, #selectAll').prop('checked', false);
            updateBulkActionsToolbar();
        });

        // View detail
        $(document).on('click', '.view-detail-btn', function () {
            const comicId = $(this).data('comic-id');
            viewComicDetail(comicId);
        });

        // Detect duplicates
        $(document).on('click', '.detect-duplicates-btn', function () {
            const comicId = $(this).data('comic-id');
            detectDuplicates(comicId);
        });

        // Update status
        $(document).on('click', '.update-status-btn', function () {
            const comicId = $(this).data('comic-id');
            const currentStatus = $(this).data('status');
            updateComicStatus(comicId, currentStatus);
        });
    }

    function loadComics() {
        const params = {
            page: currentPage,
            size: pageSize
        };

        if (currentFilters.status) {
            params.status = currentFilters.status;
        }
        if (currentFilters.progressStatus) {
            params.progressStatus = currentFilters.progressStatus;
        }
        if (currentFilters.search) {
            params.search = currentFilters.search;
        }

        ApiClient.get('/api/admin/comics', params, true).done(function (response) {
            if (response.success && response.data) {
                const comics = response.data.content || [];
                const $tbody = $('#comics-tbody');
                $tbody.empty();

                if (comics.length === 0) {
                    $tbody.append('<tr><td colspan="13" class="text-center">Không tìm thấy comic nào.</td></tr>');
                } else {
                    comics.forEach(function (comic) {
                        const row = buildComicRow(comic);
                        $tbody.append(row);
                    });
                }

                comicsTable.clear().rows.add($tbody.find('tr')).draw();

                // Re-initialize Feather icons
                if (typeof feather !== 'undefined') {
                    feather.replace();
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi tải danh sách comics'), false);
        });
    }

    function buildComicRow(comic) {
        const statusHtml = getStatusBadge(comic.status);
        const progressStatusHtml = getProgressStatusBadge(comic.progressStatus);
        const thumbHtml = comic.thumbUrl ?
            '<img src="' + escapeHtml(comic.thumbUrl) + '" class="thumb-img" alt="Thumbnail" style="max-width: 50px; max-height: 50px;">' :
            'Không có ảnh';
        const sourceUrl = comic.source ? '<a href="' + escapeHtml(comic.source) + '" target="_blank" title="' + escapeHtml(comic.source) + '"><i class="fas fa-external-link-alt"></i></a>' : '-';
        const alternativeNames = comic.alternativeNames && comic.alternativeNames.length > 0 ?
            comic.alternativeNames.join(', ') : '-';

        return `
      <tr>
        <td><input type="checkbox" class="comic-checkbox" value="${comic.id}"></td>
        <td>${comic.id}</td>
        <td>
          <a href="/truyen-tranh/${escapeHtml(comic.slug)}" target="_blank">${escapeHtml(comic.name)}</a>
          ${alternativeNames !== '-' ? '<br><small class="text-muted">' + escapeHtml(alternativeNames) + '</small>' : ''}
        </td>
        <td>${escapeHtml(comic.author || '-')}</td>
        <td>${statusHtml}</td>
        <td>${progressStatusHtml}</td>
        <td>${sourceUrl}</td>
        <td>${comic.likes || 0}</td>
        <td>${comic.follows || 0}</td>
        <td>${comic.chapterCount || 0}</td>
        <td>${thumbHtml}</td>
        <td>${formatDate(comic.updatedAt)}</td>
        <td>
          <div class="btn-group btn-group-sm">
            <button class="btn btn-info view-detail-btn" data-comic-id="${comic.id}" title="Xem chi tiết">
              <i data-feather="eye"></i>
            </button>
            <button class="btn btn-warning detect-duplicates-btn" data-comic-id="${comic.id}" title="Tìm duplicates">
              <i data-feather="copy"></i>
            </button>
            <button class="btn btn-primary update-status-btn" data-comic-id="${comic.id}" data-status="${comic.status}" title="Cập nhật status">
              <i data-feather="edit"></i>
            </button>
            <a href="/admin/chapters?comicId=${comic.id}" class="btn btn-secondary" title="Quản lý chapters">
              <i data-feather="list"></i>
            </a>
          </div>
        </td>
      </tr>
    `;
    }

    function getStatusBadge(status) {
        const statusMap = {
            'pending': {text: 'Pending', class: 'badge-secondary'},
            'active': {text: 'Active', class: 'badge-success'},
            'duplicate_detected': {text: 'Duplicate', class: 'badge-warning'},
            'merged': {text: 'Merged', class: 'badge-info'},
            'archived': {text: 'Archived', class: 'badge-dark'}
        };
        const statusInfo = statusMap[status] || {text: status, class: 'badge-secondary'};
        return '<span class="badge ' + statusInfo.class + '">' + statusInfo.text + '</span>';
    }

    function getProgressStatusBadge(progressStatus) {
        const statusMap = {
            'ongoing': {text: 'Ongoing', class: 'badge-primary'},
            'completed': {text: 'Completed', class: 'badge-success'},
            'onhold': {text: 'On Hold', class: 'badge-warning'},
            'dropped': {text: 'Dropped', class: 'badge-danger'},
            'coming_soon': {text: 'Coming Soon', class: 'badge-info'}
        };
        const statusInfo = statusMap[progressStatus] || {text: progressStatus, class: 'badge-secondary'};
        return '<span class="badge ' + statusInfo.class + '">' + statusInfo.text + '</span>';
    }

    function updateSelectedComics() {
        selectedComicIds = [];
        $('.comic-checkbox:checked').each(function () {
            selectedComicIds.push($(this).val());
        });
        updateBulkActionsToolbar();
    }

    function updateBulkActionsToolbar() {
        if (selectedComicIds.length > 0) {
            $('#bulkActionsToolbar').show();
            $('#selectedCount').text(selectedComicIds.length);
        } else {
            $('#bulkActionsToolbar').hide();
        }
    }

    function bulkUpdateStatus(comicIds, status) {
        const url = '/api/admin/comics/bulk/status?status=' + encodeURIComponent(status);
        $.ajax({
            url: ApiClient.baseUrl + url,
            method: 'POST',
            headers: ApiClient.getHeaders(true),
            data: JSON.stringify(comicIds),
            contentType: 'application/json',
            dataType: 'json'
        }).done(function (response) {
            if (response.success) {
                showToast(response.message || 'Cập nhật status thành công', true);
                const modal = bootstrap.Modal.getInstance(document.getElementById('bulkStatusModal'));
                if (modal) modal.hide();
                selectedComicIds = [];
                $('.comic-checkbox, #selectAll').prop('checked', false);
                updateBulkActionsToolbar();
                loadComics();
            } else {
                showToast(response.message || 'Lỗi khi cập nhật status', false);
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi cập nhật status'), false);
        });
    }

    function bulkDelete(comicIds) {
        ApiClient.post('/api/admin/comics/bulk/delete', comicIds, true).done(function (response) {
            if (response.success) {
                showToast(response.message || 'Archive comics thành công', true);
                selectedComicIds = [];
                $('.comic-checkbox, #selectAll').prop('checked', false);
                updateBulkActionsToolbar();
                loadComics();
            } else {
                showToast(response.message || 'Lỗi khi archive comics', false);
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi archive comics'), false);
        });
    }

    function viewComicDetail(comicId) {
        ApiClient.get('/api/admin/comics/' + comicId, null, true).done(function (response) {
            if (response.success && response.data) {
                const comic = response.data;
                const content = buildComicDetailContent(comic);
                $('#comicDetailContent').html(content);
                const modal = new bootstrap.Modal(document.getElementById('comicDetailModal'));
                modal.show();
                // Re-initialize Feather icons
                if (typeof feather !== 'undefined') {
                    feather.replace();
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi tải chi tiết comic'), false);
        });
    }

    function buildComicDetailContent(comic) {
        return `
      <div class="row">
        <div class="col-md-4">
          <img src="${escapeHtml(comic.thumbUrl || '')}" class="img-fluid" alt="Thumbnail">
        </div>
        <div class="col-md-8">
          <h4>${escapeHtml(comic.name)}</h4>
          <p><strong>Origin Name:</strong> ${escapeHtml(comic.originName || '-')}</p>
          <p><strong>Author:</strong> ${escapeHtml(comic.author || '-')}</p>
          <p><strong>Status:</strong> ${getStatusBadge(comic.status)}</p>
          <p><strong>Progress Status:</strong> ${getProgressStatusBadge(comic.progressStatus)}</p>
          <p><strong>Source:</strong> <a href="${escapeHtml(comic.source || '')}" target="_blank">${escapeHtml(comic.source || '-')}</a></p>
          <p><strong>Likes:</strong> ${comic.likes || 0}</p>
          <p><strong>Follows:</strong> ${comic.follows || 0}</p>
          <p><strong>Chapters:</strong> ${comic.chapterCount || 0}</p>
          <p><strong>Alternative Names:</strong> ${comic.alternativeNames && comic.alternativeNames.length > 0 ? comic.alternativeNames.join(', ') : '-'}</p>
          <p><strong>Content:</strong> ${escapeHtml(comic.content || '-')}</p>
        </div>
      </div>
    `;
    }

    function detectDuplicates(comicId) {
        ApiClient.get('/api/admin/comics/' + comicId + '/duplicates', null, true).done(function (response) {
            if (response.success && response.data) {
                const duplicates = response.data;
                if (duplicates.length === 0) {
                    showToast('Không tìm thấy duplicate nào', true);
                } else {
                    let message = 'Tìm thấy ' + duplicates.length + ' potential duplicates:\n';
                    duplicates.forEach(function (dup) {
                        message += '- ' + dup.name + ' (similarity: ' + (dup.similarity * 100).toFixed(2) + '%)\n';
                    });
                    alert(message);
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi tìm duplicates'), false);
        });
    }

    function updateComicStatus(comicId, currentStatus) {
        const statusOptions = ['pending', 'active', 'duplicate_detected', 'merged', 'archived'];
        const currentIndex = statusOptions.indexOf(currentStatus);
        const nextIndex = (currentIndex + 1) % statusOptions.length;
        const newStatus = statusOptions[nextIndex];

        const url = '/api/admin/comics/' + comicId + '/status?status=' + encodeURIComponent(newStatus);
        $.ajax({
            url: ApiClient.baseUrl + url,
            method: 'PUT',
            headers: ApiClient.getHeaders(true),
            dataType: 'json'
        }).done(function (response) {
            if (response.success) {
                showToast('Cập nhật status thành công', true);
                loadComics();
            } else {
                showToast(response.message || 'Lỗi khi cập nhật status', false);
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi cập nhật status'), false);
        });
    }

    function formatDate(dateString) {
        if (!dateString) return 'Chưa cập nhật';
        const date = new Date(dateString);
        return date.toLocaleDateString('vi-VN') + ' ' + date.toLocaleTimeString('vi-VN', {
            hour: '2-digit',
            minute: '2-digit'
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

