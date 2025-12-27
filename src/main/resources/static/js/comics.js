// /**
//  * Comics module for TruyenGG
//  * Handles comic listing, detail, pagination
//  */
//
// const ComicsModule = {
//   /**
//    * Load comics from API
//    */
//   loadComics: function (url, containerSelector, options = {}) {
//     // Show loading state
//     if (typeof showLoading === x27functionx27 && !opts.append) {
//       showLoading(containerSelector, x27Đang
//       tải
//       danh
//       sách
//       truyện
//     ...
//       x27
//     )
//       ;
//     }
//
//     const defaults = {
//       page: 0,
//       size: 24,
//       append: false
//     };
//     const opts = Object.assign({}, defaults, options);
//
//     ApiClient.get(url, {
//       page: opts.page,
//       size: opts.size
//     }, false).done(function (response) {
//         if (response.success && response.data) {
//           const comics = response.data.content || response.data.items || [];
//           const $container = $(containerSelector);
//
//           if (!opts.append) {
//             $container.empty();
//           }
//
//           comics.forEach(function (comic) {
//             const card = ComicsModule.renderComicCard(comic);
//             $container.append(card);
//           });
//
//           // Initialize lazy loading
//           if (typeof $.fn.lazy !== 'undefined') {
//             $container.find('.lazy').lazy({
//               effect: 'fadeIn',
//               effectTime: 300,
//               threshold: 0
//             });
//           }
//
//           const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện');
//           if (typeof showError === 'function') {
//             showError(containerSelector, errorMsg, xhr);
//           } else {
//             console.error('Failed to load comics:', xhr);
//           }
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//           const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện');
//           if (typeof showError === 'function') {
//             showError(containerSelector, errorMsg, xhr);
//           } else {
//             console.error('Failed to load comics:', xhr);
//           }
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//           const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện');
//           if (typeof showError === 'function') {
//             showError(containerSelector, errorMsg, xhr);
//           } else {
//             console.error('Failed to load comics:', xhr);
//           }
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//           const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện');
//           if (typeof showError === 'function') {
//             showError(containerSelector, errorMsg, xhr);
//           } else {
//             console.error('Failed to load comics:', xhr);
//           }
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//           const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện');
//           if (typeof showError === 'function') {
//             showError(containerSelector, errorMsg, xhr);
//           } else {
//             console.error('Failed to load comics:', xhr);
//           }
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//           const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện');
//           if (typeof showError === 'function') {
//             showError(containerSelector, errorMsg, xhr);
//           } else {
//             console.error('Failed to load comics:', xhr);
//           }
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//           console.error('Failed to load comics:', xhr);
//           if (opts.onError) {
//             opts.onError(xhr);
//           }
//         }
//       )
//         ;
//       },
//
//       /**
//        * Render comic card HTML
//        */
//       renderComicCard
//   :
//
//     function (comic) {
//       const thumbUrl = comic.thumbUrl || comic.thumb_url || '/img/placeholder.jpg';
//       const name = comic.name || 'Chưa có tên';
//       const slug = comic.slug || '';
//       const status = comic.status || 'Đang cập nhật';
//       const viewCount = comic.viewCount || comic.view_count || 0;
//       const updateTime = comic.updateTime || comic.update_time || null;
//       const latestChapter = comic.latestChapter || comic.latest_chapter || null;
//
//       const statusClass = status === 'Hoàn thành' ? 'status-completed' : 'status-ongoing';
//       const timeAgoText = updateTime ? timeAgo(updateTime) : 'Chưa cập nhật';
//       const chapterText = latestChapter ? `Chương ${latestChapter}` : 'Chưa có chương';
//
//       return `
//             <div class="comic-card">
//                 <a href="/truyen-tranh?slug=${slug}">
//                     <div class="comic-thumb">
//                         <img src="${thumbUrl}" alt="${name}" class="lazy" onerror="this.src='/img/placeholder.jpg'">
//                         <span class="comic-status ${statusClass}">${status}</span>
//                     </div>
//                     <div class="comic-info">
//                         <h3 class="comic-name">${name}</h3>
//                         <p class="comic-chapter">${chapterText}</p>
//                         <p class="comic-meta">
//                             <span class="comic-views">${formatNumber(viewCount)} lượt xem</span>
//                             <span class="comic-time">${timeAgoText}</span>
//                         </p>
//                     </div>
//                 </a>
//             </div>
//         `;
//     }
//
//   ,
//
//     /**
//      * Load comic detail
//      */
//     loadComicDetail: function (slug, containerSelector) {
//       ApiClient.get('/comics/' + slug, null, false).done(function (response) {
//         if (response.success && response.data) {
//           const comic = response.data;
//           // Render comic detail
//           if (containerSelector) {
//             $(containerSelector).html(ComicsModule.renderComicDetail(comic));
//           }
//         }
//       }).fail(function (xhr) {
//         console.error('Failed to load comic detail:', xhr);
//         showToast('Không thể tải thông tin truyện', false);
//       });
//     }
//   ,
//
//     /**
//      * Render comic detail HTML
//      */
//     renderComicDetail: function (comic) {
//       // This will be implemented in comic-detail.html
//       return '';
//     }
//   };
//
// // Helper function for numeric check
//   function isNumeric(str)
// {
//   return !isNaN(str) && !isNaN(parseFloat(str));
// }
