package com.truyengg.model.dto;

/**
 * Record holding downloaded image data including blurhash for DB persistence.
 */
public record DownloadedImage(int imageOrder, String originalUrl, String path, String blurhash) {
}
