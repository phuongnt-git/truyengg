/**
 * Duplicates management page initialization script
 */
(function () {
  'use strict';

  let pendingDuplicatesTable;
  let candidatesTable;
  let currentComicId = null;
  let mergePrimaryId = null;
  let mergeDuplicateId = null;

  $(document).ready(function () {
    if (!ApiClient.getToken()) {
      window.location.href = '/';
      return;
    }

    initializeTables();
    loadPendingDuplicates();
    setupEventHandlers();
  });

  function initializeTables() {
    pendingDuplicatesTable = $('#pendingDuplicatesTable').DataTable({
      language: {
        url: '//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json'
      },
      pageLength: 20,
      responsive: true,
      processing: true,
      paging: true,
      searching: true,
      ordering: true
    });

    candidatesTable = $('#candidatesTable').DataTable({
      language: {
        url: '//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json'
      },
      pageLength: 10,
      responsive: true,
      paging: true,
      searching: false,
      ordering: true
    });
  }

  function setupEventHandlers() {
    $('#detectDuplicatesBtn').on('click', function () {
      const comicId = $('#comicIdInput').val();
      if (!comicId) {
        showToast('Vui lòng nhập Comic ID', false);
        return;
      }
      detectDuplicates(comicId);
    });

    $(document).on('click', '.approve-duplicate-btn', function () {
      const comicId = $(this).data('comic-id');
      approveDuplicate(comicId);
    });

    $(document).on('click', '.reject-duplicate-btn', function () {
      const comicId = $(this).data('comic-id');
      rejectDuplicate(comicId);
    });

    $(document).on('click', '.merge-btn', function () {
      const primaryId = currentComicId;
      const duplicateId = $(this).data('comic-id');
      showMergeConfirmation(primaryId, duplicateId);
    });

    $('#confirmMerge').on('click', function () {
      if (mergePrimaryId && mergeDuplicateId) {
        mergeComics(mergePrimaryId, mergeDuplicateId);
      }
    });
  }

  function loadPendingDuplicates() {
    ApiClient.get('/api/admin/duplicates/pending', {page: 0, size: 100}, true).done(function (response) {
      if (response.success && response.data) {
        const comics = response.data.content || [];
        const $tbody = $('#pendingDuplicates-tbody');
        $tbody.empty();

        if (comics.length === 0) {
          $tbody.append('<tr><td colspan="6" class="text-center">Không có pending duplicates.</td></tr>');
        } else {
          comics.forEach(function (comic) {
            const row = buildPendingDuplicateRow(comic);
            $tbody.append(row);
          });
        }

        pendingDuplicatesTable.clear().rows.add($tbody.find('tr')).draw();
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi tải pending duplicates'), false);
    });
  }

  function buildPendingDuplicateRow(comic) {
    const statusHtml = getStatusBadge(comic.status);
    const sourceUrl = comic.source ? '<a href="' + escapeHtml(comic.source) + '" target="_blank"><i class="fas fa-external-link-alt"></i></a>' : '-';

    return `
      <tr>
        <td>${comic.id}</td>
        <td><a href="/truyen-tranh/${escapeHtml(comic.slug)}" target="_blank">${escapeHtml(comic.name)}</a></td>
        <td>${sourceUrl}</td>
        <td>${statusHtml}</td>
        <td>${formatDate(comic.createdAt)}</td>
        <td>
          <div class="btn-group btn-group-sm">
            <button class="btn btn-success approve-duplicate-btn" data-comic-id="${comic.id}" title="Approve">
              <i class="fas fa-check"></i>
            </button>
            <button class="btn btn-warning reject-duplicate-btn" data-comic-id="${comic.id}" title="Reject">
              <i class="fas fa-times"></i>
            </button>
            <button class="btn btn-info detect-candidates-btn" data-comic-id="${comic.id}" title="Xem candidates">
              <i class="fas fa-search"></i>
            </button>
          </div>
        </td>
      </tr>
    `;
  }

  function detectDuplicates(comicId) {
    currentComicId = comicId;
    ApiClient.get('/api/admin/duplicates/' + comicId + '/candidates', null, true).done(function (response) {
      if (response.success && response.data) {
        const candidates = response.data;
        const $tbody = $('#candidates-tbody');
        $tbody.empty();

        if (candidates.length === 0) {
          $tbody.append('<tr><td colspan="6" class="text-center">Không tìm thấy duplicate nào.</td></tr>');
        } else {
          candidates.forEach(function (candidate) {
            const row = buildCandidateRow(candidate);
            $tbody.append(row);
          });
        }

        $('#duplicateCandidates').show();
        candidatesTable.clear().rows.add($tbody.find('tr')).draw();
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi tìm duplicates'), false);
    });
  }

  function buildCandidateRow(candidate) {
    const statusHtml = getStatusBadge(candidate.status);
    const similarityPercent = (candidate.similarity * 100).toFixed(2);
    const similarityBadge = candidate.similarity >= 0.9 ? 'badge-danger' : candidate.similarity >= 0.7 ? 'badge-warning' : 'badge-info';
    const sourceUrl = candidate.source ? '<a href="' + escapeHtml(candidate.source) + '" target="_blank"><i class="fas fa-external-link-alt"></i></a>' : '-';

    return `
      <tr>
        <td>${candidate.id}</td>
        <td><a href="/truyen-tranh/${escapeHtml(candidate.slug)}" target="_blank">${escapeHtml(candidate.name)}</a></td>
        <td>${sourceUrl}</td>
        <td>${statusHtml}</td>
        <td><span class="badge ${similarityBadge}">${similarityPercent}%</span></td>
        <td>
          <button class="btn btn-sm btn-primary merge-btn" data-comic-id="${candidate.id}">
            <i class="fas fa-code-branch"></i> Merge
          </button>
        </td>
      </tr>
    `;
  }

  function showMergeConfirmation(primaryId, duplicateId) {
    mergePrimaryId = primaryId;
    mergeDuplicateId = duplicateId;

    // Load both comics to show comparison
    Promise.all([
      ApiClient.get('/api/admin/comics/' + primaryId, null, true),
      ApiClient.get('/api/admin/comics/' + duplicateId, null, true)
    ]).then(function (responses) {
      const primary = responses[0].success ? responses[0].data : null;
      const duplicate = responses[1].success ? responses[1].data : null;

      if (primary && duplicate) {
        const content = buildMergeComparison(primary, duplicate);
        $('#mergeModalContent').html(content);
        const modal = new bootstrap.Modal(document.getElementById('mergeModal'));
        modal.show();
        // Re-initialize Feather icons
        if (typeof feather !== 'undefined') {
          feather.replace();
        }
      }
    }).catch(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi tải thông tin comics'), false);
    });
  }

  function buildMergeComparison(primary, duplicate) {
    return `
      <div class="row">
        <div class="col-md-6">
          <h5>Primary Comic (Giữ lại)</h5>
          <p><strong>ID:</strong> ${primary.id}</p>
          <p><strong>Name:</strong> ${escapeHtml(primary.name)}</p>
          <p><strong>Chapters:</strong> ${primary.chapterCount || 0}</p>
          <p><strong>Likes:</strong> ${primary.likes || 0}</p>
          <p><strong>Follows:</strong> ${primary.follows || 0}</p>
        </div>
        <div class="col-md-6">
          <h5>Duplicate Comic (Sẽ merge vào)</h5>
          <p><strong>ID:</strong> ${duplicate.id}</p>
          <p><strong>Name:</strong> ${escapeHtml(duplicate.name)}</p>
          <p><strong>Chapters:</strong> ${duplicate.chapterCount || 0}</p>
          <p><strong>Likes:</strong> ${duplicate.likes || 0}</p>
          <p><strong>Follows:</strong> ${duplicate.follows || 0}</p>
        </div>
      </div>
      <div class="alert alert-warning mt-3">
        <i class="fas fa-exclamation-triangle"></i> Sau khi merge, duplicate comic sẽ được đánh dấu là MERGED và tất cả chapters sẽ được chuyển sang primary comic.
      </div>
    `;
  }

  function mergeComics(primaryId, duplicateId) {
    const url = '/api/admin/duplicates/merge?primaryId=' + primaryId + '&duplicateId=' + duplicateId;
    ApiClient.post(url, null, true).done(function (response) {
      if (response.success) {
        showToast('Merge comics thành công', true);
        const modal = bootstrap.Modal.getInstance(document.getElementById('mergeModal'));
        if (modal) modal.hide();
        loadPendingDuplicates();
        $('#duplicateCandidates').hide();
      } else {
        showToast(response.message || 'Lỗi khi merge comics', false);
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi merge comics'), false);
    });
  }

  function approveDuplicate(comicId) {
    ApiClient.post('/api/admin/duplicates/' + comicId + '/approve', null, true).done(function (response) {
      if (response.success) {
        showToast('Approve duplicate thành công', true);
        loadPendingDuplicates();
      } else {
        showToast(response.message || 'Lỗi khi approve duplicate', false);
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi approve duplicate'), false);
    });
  }

  function rejectDuplicate(comicId) {
    ApiClient.post('/api/admin/duplicates/' + comicId + '/reject', null, true).done(function (response) {
      if (response.success) {
        showToast('Reject duplicate thành công', true);
        loadPendingDuplicates();
      } else {
        showToast(response.message || 'Lỗi khi reject duplicate', false);
      }
    }).fail(function (xhr) {
      showToast(handleApiError(xhr, 'Lỗi khi reject duplicate'), false);
    });
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

