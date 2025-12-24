package com.truyengg.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ChapterRequest(
    @NotNull(message = "Comic ID không được để trống")
    Long comicId,

    @NotBlank(message = "Tên chapter không được để trống")
    String chapterName,

    String chapterTitle,
    String contentUrl,
    List<String> images
) {
}
