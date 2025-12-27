package com.truyengg.model.request;

import com.truyengg.domain.enums.ComicStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComicRequest(
    @NotBlank(message = "Tên truyện không được để trống")
    @Size(max = 255, message = "Tên truyện không được vượt quá 255 ký tự")
    String name,

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 255, message = "Slug không được vượt quá 255 ký tự")
    String slug,

    String originName,
    String content,
    ComicStatus status,
    String mainCategory,
    String thumbUrl,
    String author,
    Boolean isHot
) {
}
