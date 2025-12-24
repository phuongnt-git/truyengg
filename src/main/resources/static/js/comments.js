// /**
//  * Comments module for TruyenGG
//  * Handles comment CRUD and real-time updates
//  */
//
// const CommentsModule = {
//   /**
//    * Load comments for a comic
//    */
//   loadComments: function (comicId, containerSelector, options = {}) {
//     // Show loading state
//     if (typeof showLoading === x27functionx27 && !opts.append) {
//       showLoading(containerSelector, x27Đang
//       tải
//       bình
//       luận
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
//     ApiClient.get(`/comments/comic/${comicId}`, {
//       page: opts.page,
//       size: opts.size
//     }, false).done(function (response) {
//       if (response.success && response.data) {
//         const comments = response.data.content || [];
//         const $container = $(containerSelector);
//
//         if (!opts.append) {
//           $container.empty();
//         }
//
//         comments.forEach(function (comment) {
//           const commentHtml = CommentsModule.renderComment(comment);
//           $container.append(commentHtml);
//         });
//         const errorMsg = handleApiError(xhr, 'Không thể tải bình luận');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load comments:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//         const errorMsg = handleApiError(xhr, 'Không thể tải bình luận');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load comments:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//         const errorMsg = handleApiError(xhr, 'Không thể tải bình luận');
//         if (typeof showError === 'function') {
//           showError(containerSelector, errorMsg, xhr);
//         } else {
//           console.error('Failed to load comments:', xhr);
//         }
//         if (opts.onError) {
//           opts.onError(xhr);
//         }
//       }
//     }
//   }
// ).
// fail(function (xhr) {
//   console.error('Failed to load comments:', xhr);
// });
// },
//
// /**
//  * Render comment HTML
//  */
// renderComment: function (comment) {
//   const avatar = comment.userAvatar || comment.user_avatar || '/img/default-avatar.jpg';
//   const username = comment.username || 'Người dùng';
//   const content = comment.content || '';
//   const createdAt = comment.createdAt || comment.created_at || '';
//   const timeAgoText = timeAgo(createdAt);
//
//   return `
//             <div class="comment-item" data-comment-id="${comment.id}">
//                 <div class="comment-avatar">
//                     <img src="${avatar}" alt="${username}">
//                 </div>
//                 <div class="comment-content">
//                     <div class="comment-header">
//                         <span class="comment-username">${username}</span>
//                         <span class="comment-time">${timeAgoText}</span>
//                     </div>
//                     <div class="comment-text">${content}</div>
//                 </div>
//             </div>
//         `;
// }
// ,
//
// /**
//  * Submit a new comment
//  */
// submitComment: function (comicId, content, onSuccess) {
//   if (!ApiClient.getToken()) {
//     showToast('Vui lòng đăng nhập để bình luận', false);
//     popup('login');
//     return;
//   }
//
//   if (!content || content.trim().length === 0) {
//     showToast('Vui lòng nhập nội dung bình luận', false);
//     return;
//   }
//
//   ApiClient.post('/comments', {
//     comicId: comicId,
//     content: content.trim()
//   }, true).done(function (response) {
//     if (response.success) {
//       showToast('Bình luận thành công', true);
//       if (onSuccess) {
//         onSuccess(response.data);
//       }
//     }
//   }).fail(function (xhr) {
//     const errorMsg = xhr.responseJSON?.message || 'Không thể gửi bình luận';
//     showToast(errorMsg, false);
//   });
// }
// ,
//
// /**
//  * Delete a comment
//  */
// deleteComment: function (commentId, onSuccess) {
//   if (!confirm('Bạn có chắc muốn xóa bình luận này?')) {
//     return;
//   }
//
//   ApiClient.delete(`/comments/${commentId}`, true).done(function (response) {
//     if (response.success) {
//       showToast('Đã xóa bình luận', true);
//       $(`.comment-item[data-comment-id="${commentId}"]`).remove();
//       if (onSuccess) {
//         onSuccess();
//       }
//     }
//   }).fail(function (xhr) {
//     const errorMsg = xhr.responseJSON?.message || 'Không thể xóa bình luận';
//     showToast(errorMsg, false);
//   });
// }
// }
// ;
