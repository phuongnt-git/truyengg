/**
 * Index page initialization script
 */
(function () {
  'use strict';

  $(document).ready(function () {
    // Load new updates comics
    ComicsModule.loadComics('/comics', '#new-updates-section', {
      page: 0,
      size: 24,
      onLoad: function (comics, data) {
        console.log('Loaded new updates:', comics.length);
      },
      onError: function (xhr) {
        $('#new-updates-section').html('<p class="text-center">Không thể tải danh sách truyện mới cập nhật</p>');
      }
    });

    // Load hot comics (voted)
    ApiClient.get('/comics/list/hot', {page: 0, size: 10}, false).done(function (response) {
      if (response.success && response.data && response.data.content) {
        const comics = response.data.content;
        const $container = $('#hot-comics-section');
        $container.empty();

        comics.forEach(function (comic) {
          const thumbUrl = comic.thumbUrl || '/img/placeholder.jpg';
          const name = comic.name || 'Chưa có tên';
          const slug = comic.slug || '';
          const latestChapter = comic.latestChapter || null;
          const viewCount = comic.viewCount || 0;
          const followCount = comic.followCount || 0;
          const updateTime = comic.updateTime || null;
          const timeAgoText = updateTime ? timeAgo(updateTime) : 'Chưa cập nhật';

          const item = `
                            <div class="col-md-12 col-lg-6 item_home">
                                <div class="d-flex">
                                    <div class="mr-3">
                                        <a href="/truyen-tranh?slug=${slug}" class="thumbblock thumb70x85">
                                            <img data-src="${thumbUrl}" alt="${name}" class="lazy-image" 
                                                 src="https://st.truyengg.net/template/frontend/img/loading.jpg"
                                                 onerror="this.src='https://st.truyengg.net/template/frontend/img/placeholder.jpg';"/>
                                        </a>
                                    </div>
                                    <div class="flex-one wc70">
                                        <a href="/truyen-tranh?slug=${slug}" class="fs14 txt_oneline fw600" title="${name}">
                                            ${name}
                                        </a>
                                        <div>
                                            ${latestChapter ?
            `<a href="/chapter?slug=${slug}&chapter=${latestChapter}" class="fs13 cl99" title="Chương ${latestChapter}">Chương ${latestChapter}</a>` :
            '<span class="fs13 cl99">Chưa có chapter</span>'
          }
                                        </div>
                                        <div class="fs13 cl99"><i class="bi bi-eye-fill"></i> <em>${formatNumber(viewCount)}</em></div>
                                        <div class="fs13 cl99"><i class="bi bi-bookmark-plus-fill"></i> <em>${formatNumber(followCount)}</em></div>
                                    </div>
                                </div>
                            </div>
                        `;
          $container.append(item);
        });

        if (typeof $.fn.lazy !== 'undefined') {
          $container.find('.lazy-image').lazy({
            effect: 'fadeIn',
            effectTime: 300,
            threshold: 0
          });
        }
      } else {
        $('#hot-comics-section').html('<p>Không tìm thấy truyện hot nào!</p>');
      }
    }).fail(function (xhr) {
      $('#hot-comics-section').html('<p>Lỗi khi tải danh sách truyện hot!</p>');
    });

    // Load top viewed comics
    ApiClient.get('/rankings/monthly', {page: 0, size: 10}, false).done(function (response) {
      if (response.success && response.data && response.data.content) {
        const comics = response.data.content;
        const $container = $('#top-viewed-section');
        $container.empty();

        comics.forEach(function (comic) {
          const thumbUrl = comic.thumbUrl || '/img/placeholder.jpg';
          const name = comic.name || 'Chưa có tên';
          const slug = comic.slug || '';
          const latestChapter = comic.latestChapter || null;
          const viewCount = comic.viewCount || 0;
          const followCount = comic.followCount || 0;

          const item = `
                            <div class="col-md-12 col-lg-6 item_home">
                                <div class="d-flex">
                                    <div class="mr-3">
                                        <a href="/truyen-tranh?slug=${slug}" class="thumbblock thumb70x85">
                                            <img data-src="${thumbUrl}" alt="${name}" class="lazy-image" 
                                                 src="https://st.truyengg.net/template/frontend/img/loading.jpg"
                                                 onerror="this.src='https://st.truyengg.net/template/frontend/img/placeholder.jpg';"/>
                                        </a>
                                    </div>
                                    <div class="flex-one wc70">
                                        <a href="/truyen-tranh?slug=${slug}" class="fs14 txt_oneline fw600" title="${name}">
                                            ${name}
                                        </a>
                                        <div>
                                            ${latestChapter ?
            `<a href="/chapter?slug=${slug}&chapter=${latestChapter}" class="fs13 cl99" title="Chương ${latestChapter}">Chương ${latestChapter}</a>` :
            '<span class="fs13 cl99">Chưa có chapter</span>'
          }
                                        </div>
                                        <div class="fs13 cl99"><i class="bi bi-eye-fill"></i> <em>${formatNumber(viewCount)}</em></div>
                                        <div class="fs13 cl99"><i class="bi bi-bookmark-plus-fill"></i> <em>${formatNumber(followCount)}</em></div>
                                    </div>
                                </div>
                            </div>
                        `;
          $container.append(item);
        });

        if (typeof $.fn.lazy !== 'undefined') {
          $container.find('.lazy-image').lazy({
            effect: 'fadeIn',
            effectTime: 300,
            threshold: 0
          });
        }
      } else {
        $('#top-viewed-section').html('<p>Không tìm thấy truyện nào!</p>');
      }
    }).fail(function (xhr) {
      $('#top-viewed-section').html('<p>Lỗi khi tải danh sách truyện xem nhiều!</p>');
    });

    // Back to top button
    $(window).scroll(function () {
      if ($(this).scrollTop() > 100) {
        $('#back-to-top').fadeIn();
      } else {
        $('#back-to-top').fadeOut();
      }
    });

    $('#back-to-top').click(function () {
      $('html, body').animate({scrollTop: 0}, 600);
      return false;
    });
  });

  // Popup functions
  function checkPopup() {
    const popupShown = localStorage.getItem('popupShown');
    const now = new Date().getTime();
    const sixtyMinutes = 60 * 60 * 1000;
    if (!popupShown || now - popupShown > sixtyMinutes) {
      const popup = document.getElementById('custom-popup');
      if (popup) {
        popup.style.display = 'flex';
        popup.classList.add('show');
        setTimeout(function () {
          if (popup.style.display !== 'none') {
            closePopup();
          }
        }, 10000);
      }
    }
  }

  function closePopup() {
    const popup = document.getElementById('custom-popup');
    if (popup) {
      popup.style.display = 'none';
      localStorage.setItem('popupShown', new Date().getTime());
    }
  }

  window.onload = checkPopup;
  window.closePopup = closePopup;
})();

