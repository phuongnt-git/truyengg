// /**
//  * Follow module for TruyenGG
//  * Handles follow/unfollow functionality
//  */
//
// const FollowModule = {
//   /**
//    * Toggle follow status for a comic
//    */
//   toggleFollow: function (comicId, comicSlug, buttonSelector) {
//     if (!ApiClient.getToken()) {
//       showToast('Vui lòng đăng nhập để theo dõi truyện', false);
//       popup('login');
//       return;
//     }
//
//     const $button = $(buttonSelector);
//     const isFollowing = $button.hasClass('following');
//
//     if (isFollowing) {
//       FollowModule.unfollow(comicId, $button);
//     } else {
//       FollowModule.follow(comicId, comicSlug, $button);
//     }
//   },
//
//   /**
//    * Follow a comic
//    */
//   follow: function (comicId, comicSlug, $button) {
//     ApiClient.post('/follow', {
//       comicId: comicId,
//       comicSlug: comicSlug
//     }, true).done(function (response) {
//       if (response.success) {
//         $button.addClass('following').text('Đang theo dõi');
//         showToast('Đã theo dõi truyện', true);
//       }
//     }).fail(function (xhr) {
//       const errorMsg = xhr.responseJSON?.message || 'Không thể theo dõi truyện';
//       showToast(errorMsg, false);
//     });
//   },
//
//   /**
//    * Unfollow a comic
//    */
//   unfollow: function (comicId, $button) {
//     ApiClient.delete(`/follow/${comicId}`, true).done(function (response) {
//       if (response.success) {
//         $button.removeClass('following').text('Theo dõi');
//         showToast('Đã bỏ theo dõi truyện', true);
//       }
//     }).fail(function (xhr) {
//       const errorMsg = xhr.responseJSON?.message || 'Không thể bỏ theo dõi truyện';
//       showToast(errorMsg, false);
//     });
//   },
//
//   /**
//    * Load followed comics
//    */
//   loadFollowedComics: function (containerSelector, options = {}) {
//     // Show loading state
//     if (typeof showLoading === x27functionx27 && !opts.append) {
//       showLoading(containerSelector, x27Đang
//       tải
//       danh
//       sách
//       truyện
//       theo
//       dõi
//     ...
//       x27
//     )
//       ;
//     }
//
//     const defaults = {
//       page: 0,
//       size: 24
//     };
//     const opts = Object.assign({}, defaults, options);
//
//     ApiClient.get('/follow/comics', {
//       page: opts.page,
//       size: opts.size
//     }, true).done(function (response) {
//       if (response.success && response.data) {
//         const comics = response.data.content || [];
//         const $container = $(containerSelector);
//
//         if (!opts.append) {
//           $container.empty();
//         }
//
//         comics.forEach(function (comic) {
//           const card = ComicsModule.renderComicCard(comic);
//           $container.append(card);
//         });
//         const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện theo dõi');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load followed comics:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//         const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện theo dõi');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load followed comics:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//         const errorMsg = handleApiError(xhr, 'Không thể tải danh sách truyện theo dõi');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load followed comics:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//       }
//     }
//   }
// ).
// fail(function (xhr) {
//   console.error('Failed to load followed comics:', xhr);
// });
// }
// }
// ;
