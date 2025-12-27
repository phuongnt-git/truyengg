/**
 * Completed comics page initialization script
 */
(function () {
    'use strict';

    let currentPage = 0;
    const pageSize = 24;

    $(document).ready(function () {
        const urlParams = new URLSearchParams(window.location.search);
        currentPage = parseInt(urlParams.get('page')) || 0;

        loadComics();
    });

    function loadComics() {
        ComicsModule.loadComics('/lists/completed', '#comics-list', {
            page: currentPage,
            size: pageSize,
            onLoad: function (comics, data) {
                renderPagination(data);

                if (typeof $.fn.lazy !== 'undefined') {
                    $('#comics-list .lazy-image').lazy({
                        effect: 'fadeIn',
                        effectTime: 300,
                        threshold: 0
                    });
                }
            },
            onError: function (xhr) {
                $('#comics-list').html('<p class="text-center">Không thể tải danh sách truyện</p>');
            }
        });
    }

    function renderPagination(data) {
        const totalPages = data.totalPages || 0;
        const $container = $('#pagination-container');
        $container.empty();

        if (totalPages <= 1) return;

        let paginationHtml = '<div class="pagination">';

        if (currentPage > 0) {
            paginationHtml += `<a href="?page=${currentPage - 1}" class="page-item">‹</a>`;
        }

        const maxPages = 5;
        let startPage = Math.max(0, currentPage - Math.floor(maxPages / 2));
        let endPage = Math.min(totalPages - 1, startPage + maxPages - 1);

        if (endPage - startPage < maxPages - 1) {
            startPage = Math.max(0, endPage - maxPages + 1);
        }

        for (let i = startPage; i <= endPage; i++) {
            if (i === currentPage) {
                paginationHtml += `<a href="javascript:void(0)" class="page-item active">${i + 1}</a>`;
            } else {
                paginationHtml += `<a href="?page=${i}" class="page-item">${i + 1}</a>`;
            }
        }

        if (currentPage < totalPages - 1) {
            paginationHtml += `<a href="?page=${currentPage + 1}" class="page-item">›</a>`;
        }

        paginationHtml += '</div>';
        $container.html(paginationHtml);
    }
})();

