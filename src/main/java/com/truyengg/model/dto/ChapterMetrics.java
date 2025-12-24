package com.truyengg.model.dto;

import java.util.List;

public record ChapterMetrics(boolean success, int images, List<String> imagePaths,
                             List<String> originalImagePaths, long fileSizeBytes,
                             int requestCount, int errorCount) {
}
