package com.truyengg.model.dto;

public record CompressedImageResult(
    byte[] compressedBytes,
    String contentType,
    long originalSize,
    long compressedSize,
    double compressionRatio
) {
}
