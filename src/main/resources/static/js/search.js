/**
 * Search module for TruyenGG
 * Handles search autocomplete and search functionality
 */

$(document).ready(function () {
    let autocomplete;
    const searchInput = $('#search_input');
    const searchResults = $('.show_result_search ul');
    const placeholderImage = '/img/placeholder.jpg';

    searchInput.on('input', function () {
        const query = $(this).val().trim();

        if (query.length < 2) {
            searchResults.empty();
            $('.show_result_search').removeClass('open');
            return;
        }

        clearTimeout(autocomplete);
        autocomplete = setTimeout(function () {
            $.ajax({
                url: '/api/comics/search',
                method: 'GET',
                data: {query: query, page: 0, size: 8},
                dataType: 'json',
                success: function (response) {
                    searchResults.empty();
                    if (!response.data || !response.data.content || response.data.content.length === 0) {
                        searchResults.append('<li><p style="padding: 10px;">Không tìm thấy kết quả</p></li>');
                        $('.show_result_search').addClass('open');
                        return;
                    }

                    const items = response.data.content.slice(0, 8);
                    items.forEach(function (item) {
                        if (!item.slug || !item.name) {
                            return;
                        }

                        const thumbUrl = item.thumbUrl || placeholderImage;
                        const otherName = item.originName || '';
                        const latestChapter = item.totalChapters ?
                            `${item.totalChapters} chương` : 'Chưa có chương';

                        const li = `
                            <li>
                                <a href="/truyen-tranh?slug=${item.slug}">
                                    <div class="search_avatar">
                                        <img src="${thumbUrl}" alt="${item.name}" class="lazy" onerror="this.src='${placeholderImage}'">
                                    </div>
                                    <div class="search_info">
                                        <p class="name">${item.name}</p>
                                        <p class="name_other">${otherName}</p>
                                        <p>${latestChapter}</p>
                                    </div>
                                </a>
                            </li>`;
                        searchResults.append(li);
                    });

                    // Initialize lazy loading
                    if (typeof $.fn.lazy !== 'undefined') {
                        $('.lazy').lazy({
                            effect: 'fadeIn',
                            effectTime: 300,
                            threshold: 0,
                            onError: function (element) {
                                element.attr('src', placeholderImage);
                            }
                        });
                    }

                    $('.show_result_search').addClass('open');
                },
                error: function (xhr, status, error) {
                    console.error('Search error:', {status: status, error: error});
                    searchResults.empty();
                    searchResults.append('<li><p style="padding: 10px;">Lỗi khi tải gợi ý</p></li>');
                    $('.show_result_search').addClass('open');
                }
            });
        }, 800);
    });

    // Close search results when clicking outside
    $(document).on('click', function (e) {
        if (!$(e.target).closest('.box_search_main, .show_result_search, .notification_span, .profile').length) {
            searchResults.empty();
            $('.show_result_search').removeClass('open');
            $('.notification').removeClass('active');
            $('.setting').removeClass('active');
        }
    });

    // Handle Enter key in search
    searchInput.on('keydown', function (e) {
        if (e.key === 'Enter' && $(this).val().trim().length > 0) {
            e.preventDefault();
            const keyword = $(this).val().trim();
            window.location.href = '/tim-kiem-nang-cao?keywords=' + encodeURIComponent(keyword);
        }
    });

    // Search button click
    $('.btn_search').on('click', function (e) {
        e.preventDefault();
        const keyword = $('#search_input').val().trim();
        if (keyword.length > 0) {
            window.location.href = '/tim-kiem-nang-cao?keywords=' + encodeURIComponent(keyword);
        } else {
            showToast('Vui lòng nhập từ khóa tìm kiếm.', false);
        }
    });

    // Notification and profile dropdowns
    $('.icon-notification').on('click', function (e) {
        e.preventDefault();
        $('.notification').toggleClass('active');
        $('.setting').removeClass('active');
    });

    $('.icon-profile').on('click', function (e) {
        e.preventDefault();
        $('.setting').toggleClass('active');
        $('.notification').removeClass('active');
    });
});
