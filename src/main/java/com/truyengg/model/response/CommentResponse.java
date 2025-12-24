package com.truyengg.model.response;

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
}

