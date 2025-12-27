package com.truyengg.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommentRequest(
    @NotNull(message = "Comic ID không được để trống")
    Long comicId,

    @NotBlank(message = "Nội dung bình luận không được để trống")
    String content,

    Long parentId
) {
}

