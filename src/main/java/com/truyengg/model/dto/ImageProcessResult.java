package com.truyengg.model.dto;

public record ImageProcessResult(
    int successCount,
    long fileSizeBytes,
    int requestCount,
    int errorCount
) {
}
