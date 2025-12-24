package com.truyengg.service.crawl;

import com.truyengg.domain.entity.ComicCrawlCheckpoint;
import com.truyengg.model.dto.CrawlRequestParams;
import com.truyengg.model.request.CrawlRequest;
import com.truyengg.model.response.ComicCrawlProgressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

import static com.truyengg.domain.enums.ComicCrawlStatus.FAILED;
import static com.truyengg.domain.enums.ComicCrawlStatus.RUNNING;
import static com.truyengg.service.crawl.CrawlConstants.ERROR_PREFIX;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlService {

  private final ComicCrawlStrategy comicCrawlStrategy;
  private final ComicCrawlProgressService comicCrawlProgressService;
  private final ComicCrawlCheckpointService comicCrawlCheckpointService;
  private final ChapterCrawlService chapterCrawlService;

  public void crawlMangaAsync(CrawlRequest request, UUID crawlId) {
    var startTime = now();
    var messages = new ArrayList<String>();

    try {
      var progressResponse = new ComicCrawlProgressResponse(
          crawlId,
          RUNNING,
          "Initializing crawl...",
          messages,
          startTime
      );
      comicCrawlProgressService.updateProgressCache(progressResponse);

      var checkpointOpt = comicCrawlCheckpointService.getCheckpoint(crawlId);
      var crawledChapters = checkpointOpt
          .map(cp -> new ArrayList<>(cp.getCrawledChapters() != null ? cp.getCrawledChapters() : new ArrayList<>()))
          .orElse(new ArrayList<>());

      // Determine resume point: chapter-level or image-level
      int resumeFromChapterIndex = checkpointOpt
          .map(ComicCrawlCheckpoint::getCurrentChapterIndex)
          .orElse(-1);
      int resumeFromImageIndex = checkpointOpt
          .map(cp -> cp.getCurrentImageIndex() != null ? cp.getCurrentImageIndex() + 1 : -1)
          .orElse(-1);

      // If paused at image level, resume from same chapter; otherwise from next chapter
      int resumeFromIndex;
      if (resumeFromImageIndex >= 0) {
        resumeFromIndex = resumeFromChapterIndex;
      } else if (resumeFromChapterIndex >= 0) {
        resumeFromIndex = resumeFromChapterIndex + 1;
      } else {
        resumeFromIndex = 0;
      }

      var existingDetails = chapterCrawlService.getChapterCrawlByCrawlIdAll(crawlId);
      var isRetry = !existingDetails.isEmpty() && checkpointOpt.isEmpty();

      if (isRetry) {
        messages.add("Retrying crawl - will increment retry_count for chapters being retried");
      } else if (checkpointOpt.isPresent()) {
        if (resumeFromImageIndex >= 0) {
          messages.add("Resuming from checkpoint: chapter " + (resumeFromChapterIndex + 1) + ", image " + (resumeFromImageIndex + 1));
        } else {
          messages.add("Resuming from checkpoint: chapter " + (resumeFromChapterIndex + 1));
        }
      }

      var params = new CrawlRequestParams(request, crawlId, startTime, messages, crawledChapters,
          resumeFromIndex, resumeFromChapterIndex, resumeFromImageIndex);
      comicCrawlStrategy.crawl(params);
    } catch (Exception e) {
      messages.add(ERROR_PREFIX + e.getMessage());
      var errorProgress = new ComicCrawlProgressResponse(
          crawlId,
          FAILED,
          ERROR_PREFIX + getMessage(e),
          messages,
          startTime
      );
      comicCrawlProgressService.finalizeCrawl(errorProgress);
    }
  }
}
