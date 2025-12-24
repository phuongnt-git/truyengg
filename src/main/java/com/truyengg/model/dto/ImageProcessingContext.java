package com.truyengg.model.dto;

import java.util.List;

public record ImageProcessingContext(
    String imageUrl,
    List<String> headers,
    String comicId,
    String chapterId,
    String fileName,
    int totalImages,
    int chapterIndex,
    int currentDownloadedCount
) {
}
