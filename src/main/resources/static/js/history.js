// /**
//  * History module for TruyenGG
//  * Handles reading history management
//  */
//
// const HistoryModule = {
//   /**
//    * Load reading history
//    */
//   loadHistory: function (containerSelector, options = {}) {
//     // Show loading state
//     if (typeof showLoading === x27functionx27 && !opts.append) {
//       showLoading(containerSelector, x27Đang
//       tải
//       lịch
//       sử
//       đọc
//     ...
//       x27
//     )
//       ;
//     }
//
//     const defaults = {
//       page: 0,
//       size: 20
//     };
//     const opts = Object.assign({}, defaults, options);
//
//     ApiClient.get('/history', {
//       page: opts.page,
//       size: opts.size
//     }, true).done(function (response) {
//       if (response.success && response.data) {
//         const historyItems = response.data.content || [];
//         const $container = $(containerSelector);
//
//         if (!opts.append) {
//           $container.empty();
//         }
//
//         historyItems.forEach(function (item) {
//           const historyHtml = HistoryModule.renderHistoryItem(item);
//           $container.append(historyHtml);
//         });
//         const errorMsg = handleApiError(xhr, 'Không thể tải lịch sử đọc');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load history:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//         const errorMsg = handleApiError(xhr, 'Không thể tải lịch sử đọc');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load history:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//         const errorMsg = handleApiError(xhr, 'Không thể tải lịch sử đọc');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load history:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//       }
//     }
//   }
// ).
// fail(function (xhr) {
//   console.error('Failed to load history:', xhr);
// });
// },
//
// /**
//  * Render history item HTML
//  */
// renderHistoryItem: function (item) {
//   const thumbUrl = item.comicThumbUrl || item.comic_thumb_url || '/img/placeholder.jpg';
//   const comicName = item.comicName || item.comic_name || 'Chưa có tên';
//   const comicSlug = item.comicSlug || item.comic_slug || '';
//   const chapterName = item.chapterName || item.chapter_name || '';
//   const readAt = item.readAt || item.read_at || '';
//   const timeAgoText = timeAgo(readAt);
//
//   return `
//             <div class="history-item" data-comic-slug="${comicSlug}">
//                 <a href="/chapter?slug=${comicSlug}&chapter=${chapterName}">
//                     <div class="history-thumb">
//                         <img src="${thumbUrl}" alt="${comicName}" class="lazy">
//                     </div>
//                     <div class="history-info">
//                         <h4 class="history-comic-name">${comicName}</h4>
//                         <p class="history-chapter">Chương ${chapterName}</p>
//                         <p class="history-time">${timeAgoText}</p>
//                     </div>
//                 </a>
//                 <button class="btn-remove-history" onclick="HistoryModule.removeHistory('${comicSlug}')">
//                     <i class="bi bi-x"></i>
//                 </button>
//             </div>
//         `;
// }
// ,
//
// /**
//  * Remove history item
//  */
// removeHistory: function (comicSlug) {
//   ApiClient.delete(`/history/${comicSlug}`, true).done(function (response) {
//     if (response.success) {
//       $(`.history-item[data-comic-slug="${comicSlug}"]`).remove();
//       showToast('Đã xóa khỏi lịch sử', true);
//     }
//   }).fail(function (xhr) {
//     const errorMsg = xhr.responseJSON?.message || 'Không thể xóa lịch sử';
//     showToast(errorMsg, false);
//   });
// }
// ,
//
// /**
//  * Clear all history
//  */
// clearAllHistory: function () {
//   if (!confirm('Bạn có chắc muốn xóa toàn bộ lịch sử đọc truyện?')) {
//     return;
//   }
//
//   ApiClient.delete('/history', true).done(function (response) {
//     if (response.success) {
//       $('.history-item').remove();
//       showToast('Đã xóa toàn bộ lịch sử', true);
//     }
//   }).fail(function (xhr) {
//     const errorMsg = xhr.responseJSON?.message || 'Không thể xóa lịch sử';
//     showToast(errorMsg, false);
//   });
// }
// }
// ;
