package com.truyengg.model.dto;

/**
 * Result of image compression including blurhash for preview.
 */
public record CompressedImageResult(
    byte[] compressedBytes,
    String contentType,
    long originalSize,
    long compressedSize,
    double compressionRatio,
    String blurhash
) {
}
