package com.truyengg.model.response;

import com.truyengg.domain.entity.Comment;

import java.time.ZonedDateTime;
import java.util.List;

public record CommentResponse(
    Long id,
    Long userId,
    String username,
    String avatar,
    Integer level,
    String content,
    Long parentId,
    Integer likes,
    Integer dislikes,
    List<CommentResponse> replies,
    ZonedDateTime createdAt
) {

  public static CommentResponse from(Comment comment) {
    return from(comment, null);
  }

  public static CommentResponse from(Comment comment, List<CommentResponse> replies) {
    var user = comment.getUser();
    var parent = comment.getParent();

    return new CommentResponse(
        comment.getId(),
        user != null ? user.getId() : null,
        user != null ? user.getUsername() : null,
        user != null ? user.getAvatar() : null,
        user != null ? user.getLevel() : null,
        comment.getContent(),
        parent != null ? parent.getId() : null,
        comment.getLikes(),
        comment.getDislikes(),
        replies,
        comment.getCreatedAt()
    );
  }

  public static List<CommentResponse> fromList(List<Comment> comments) {
    return comments.stream()
        .map(CommentResponse::from)
        .toList();
  }
}
