/**
 * Stories management page initialization script
 */
(function () {
    'use strict';

    let comicsTable;
    let currentPage = 0;
    const pageSize = 20;

    $(document).ready(function () {
        if (!ApiClient.getToken()) {
            var currentPath = window.location.pathname;
            window.location.href = '/auth/login?redirect=' + encodeURIComponent(currentPath);
            return;
        }

        loadComics();

        // Initialize DataTable
        comicsTable = $('#comicsTable').DataTable({
            "language": {
                "url": "//cdn.datatables.net/plug-ins/1.13.7/i18n/vi.json"
            },
            "pageLength": pageSize,
            "responsive": true,
            "columnDefs": [
                {"orderable": false, "targets": 6}
            ],
            "paging": false,
            "info": false
        });

        // Search functionality
        $('#searchInput').on('keyup', function () {
            comicsTable.search(this.value).draw();
        });

        // Sync comic
        $(document).on('click', '.sync-comic-btn', function () {
            const comicId = $(this).data('comic-id');
            const $btn = $(this);
            const originalHtml = $btn.html();

            $btn.prop('disabled', true).html('<i data-feather="loader"></i> Đang đồng bộ...');

            ApiClient.post('/admin/comics/' + comicId + '/sync', null, true).done(function (response) {
                if (response.success) {
                    showToast(response.message || 'Đồng bộ truyện thành công', true);
                    loadComics();
                } else {
                    showToast(response.message || 'Lỗi khi đồng bộ truyện', false);
                }
                $btn.prop('disabled', false).html(originalHtml);
            }).fail(function (xhr) {
                showToast(handleApiError(xhr, 'Lỗi khi đồng bộ truyện'), false);
                $btn.prop('disabled', false).html(originalHtml);
            });
        });
    });

    function loadComics() {
        ApiClient.get('/admin/comics', {page: currentPage, size: pageSize}, true).done(function (response) {
            if (response.success && response.data) {
                const comics = response.data.content || [];
                const $tbody = $('#comics-tbody');
                $tbody.empty();

                if (comics.length === 0) {
                    $tbody.append('<tr><td colspan="7" class="text-center">Chưa có truyện nào.</td></tr>');
                } else {
                    comics.forEach(function (comic) {
                        const statusHtml = getStatusHtml(comic.status);
                        const thumbHtml = comic.thumbUrl ?
                            '<img src="' + escapeHtml(comic.thumbUrl) + '" class="thumb-img" alt="Thumbnail">' :
                            'Không có ảnh';
                        const authors = comic.authors && comic.authors.length > 0 ?
                            comic.authors.join(', ') : 'Chưa có tác giả';

                        const row = `
                        <tr>
                            <td>${comic.id}</td>
                            <td><a th:href="@{/truyen-tranh(slug=${comic.slug})}" target="_blank">${escapeHtml(comic.name)}</a></td>
                            <td>${escapeHtml(authors)}</td>
                            <td>${statusHtml}</td>
                            <td>${thumbHtml}</td>
                            <td>${formatDate(comic.updatedAt)}</td>
                            <td>
                                <button class="btn btn-sm btn-success sync-comic-btn" data-comic-id="${comic.id}">
                                    <i data-feather="refresh-cw"></i> Đồng bộ
                                </button>
                                <a th:href="@{/admin/chapters?comicId=${comic.id}}" class="btn btn-sm btn-info">
                                    <i data-feather="list"></i> Quản lý Chapter
                                </a>
                            </td>
                        </tr>
                    `;
                        $tbody.append(row);
                    });
                }

                // Update DataTable
                comicsTable.clear().rows.add($tbody.find('tr')).draw();

                // Re-initialize Feather icons
                if (typeof feather !== 'undefined') {
                    feather.replace();
                }
            }
        }).fail(function (xhr) {
            showToast(handleApiError(xhr, 'Lỗi khi tải danh sách truyện'), false);
        });
    }

    function getStatusHtml(status) {
        const statusMap = {
            'ongoing': {text: 'Đang tiến hành', class: 'status-ongoing'},
            'completed': {text: 'Hoàn thành', class: 'status-completed'},
            'onhold': {text: 'Tạm hoãn', class: 'status-onhold'},
            'dropped': {text: 'Đã hủy', class: 'status-dropped'},
            'coming_soon': {text: 'Sắp ra mắt', class: 'status-coming_soon'}
        };

        const statusInfo = statusMap[status] || {text: status, class: ''};
        return '<span class="' + statusInfo.class + '">' + statusInfo.text + '</span>';
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

