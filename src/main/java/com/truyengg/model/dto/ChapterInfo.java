package com.truyengg.model.dto;

import java.util.List;

public record ChapterInfo(
    String chapterName,
    String chapterTitle,
    String source,
    List<ChapterImageInfo> imageUrls
) {
}

