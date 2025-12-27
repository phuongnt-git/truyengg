package com.truyengg.model.response;

public record CrawlJobStartResponse(
    String jobId,
    String sseToken // Deprecated: kept for backward compatibility, always null
) {
  public CrawlJobStartResponse(String jobId) {
    this(jobId, null);
  }
}

