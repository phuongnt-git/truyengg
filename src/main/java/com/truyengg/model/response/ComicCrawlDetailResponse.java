package com.truyengg.model.response;

import com.truyengg.model.dto.ChapterCrawlDto;
import com.truyengg.model.dto.ComicCrawlMetricsDto;
import com.truyengg.model.dto.CommicCrawlCheckpointResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public record ComicCrawlDetailResponse(
    ComicCrawlResponse crawl,
    CommicCrawlCheckpointResponse checkpoint,
    ComicCrawlProgressResponse currentProgress,
    List<String> downloadedFiles,
    Page<ChapterCrawlDto> details,
    ComicCrawlMetricsDto metrics
) {
}

