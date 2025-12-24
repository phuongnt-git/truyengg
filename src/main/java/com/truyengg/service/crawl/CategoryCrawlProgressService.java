package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CategoryCrawlJob;
import com.truyengg.domain.entity.CategoryCrawlProgress;
import com.truyengg.domain.repository.CategoryCrawlDetailRepository;
import com.truyengg.domain.repository.CategoryCrawlProgressRepository;
import com.truyengg.model.response.CategoryCrawlDetailResponse;
import com.truyengg.model.response.CategoryCrawlProgressResponse;
import com.truyengg.service.WebSocketProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.time.Duration.between;
import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryCrawlProgressService {

  private static final Map<UUID, ZonedDateTime> jobStartTimes = new HashMap<>();
  private final CategoryCrawlProgressRepository categoryCrawlProgressRepository;
  private final CategoryCrawlDetailRepository categoryCrawlDetailRepository;
  private final WebSocketProgressService webSocketProgressService;

  @Transactional
  public void updateProgress(UUID jobId, int currentPage, int currentStoryIndex, int totalStories,
                             int crawledStories, int totalChapters, int crawledChapters,
                             int totalImages, int downloadedImages) {
    var progressOpt = categoryCrawlProgressRepository.findByCategoryCrawlJobId(jobId);
    CategoryCrawlProgress progress;

    if (progressOpt.isEmpty()) {
      progress = CategoryCrawlProgress.builder()
          .categoryCrawlJobId(jobId)
          .build();
      jobStartTimes.put(jobId, now());
    } else {
      progress = progressOpt.get();
      if (!jobStartTimes.containsKey(jobId)) {
        jobStartTimes.put(jobId, progress.getUpdatedAt());
      }
    }

    progress.setCurrentPage(currentPage);
    progress.setCurrentStoryIndex(currentStoryIndex);
    progress.setTotalStories(totalStories);
    progress.setCrawledStories(crawledStories);
    progress.setTotalChapters(totalChapters);
    progress.setCrawledChapters(crawledChapters);
    progress.setTotalImages(totalImages);
    progress.setDownloadedImages(downloadedImages);
    progress.setUpdatedAt(now());

    categoryCrawlProgressRepository.save(progress);

    // Broadcast via WebSocket
    broadcastProgress(jobId, progress);
  }

  private void broadcastProgress(UUID jobId, CategoryCrawlProgress progress) {
    try {
      var details = categoryCrawlDetailRepository.findByCategoryCrawlJob_Id(jobId);
      var storyProgress = details.stream()
          .collect(Collectors.toMap(
              d -> String.valueOf(d.getId()),
              CategoryCrawlDetailResponse::fromEntity
          ));

      var startTime = jobStartTimes.getOrDefault(jobId, progress.getUpdatedAt());
      var elapsedSeconds = between(startTime, now()).getSeconds();

      var response = new CategoryCrawlProgressResponse(
          jobId,
          "RUNNING",
          progress.getCurrentPage(),
          progress.getCurrentStoryIndex(),
          progress.getTotalStories(),
          progress.getCrawledStories(),
          progress.getTotalChapters(),
          progress.getCrawledChapters(),
          progress.getTotalImages(),
          progress.getDownloadedImages(),
          String.format("Processing: %d/%d stories, %d/%d chapters",
              progress.getCrawledStories(), progress.getTotalStories(),
              progress.getCrawledChapters(), progress.getTotalChapters()),
          List.of(),
          startTime,
          progress.getUpdatedAt(),
          elapsedSeconds,
          storyProgress
      );

      webSocketProgressService.sendCategoryCrawlProgress(jobId, response);
    } catch (Exception e) {
      log.error("Error broadcasting category crawl progress for job: {}", jobId, e);
    }
  }

  @Transactional(readOnly = true)
  public Optional<CategoryCrawlProgress> getProgress(UUID jobId) {
    return categoryCrawlProgressRepository.findByCategoryCrawlJobId(jobId);
  }

  @Transactional(readOnly = true)
  public CategoryCrawlProgressResponse getProgressResponse(UUID jobId, CategoryCrawlJob categoryJob) {
    var progressOpt = getProgress(jobId);
    if (progressOpt.isEmpty()) {
      return createEmptyProgressResponse(jobId);
    }

    var progress = progressOpt.get();
    var details = categoryCrawlDetailRepository.findByCategoryCrawlJob_Id(jobId);
    var storyProgress = details.stream()
        .collect(Collectors.toMap(
            d -> String.valueOf(d.getId()),
            CategoryCrawlDetailResponse::fromEntity
        ));

    var startTime = jobStartTimes.getOrDefault(jobId, progress.getUpdatedAt());
    var elapsedSeconds = between(startTime, now()).getSeconds();

    return new CategoryCrawlProgressResponse(
        jobId,
        categoryJob.getStatus().name(),
        progress.getCurrentPage(),
        progress.getCurrentStoryIndex(),
        progress.getTotalStories(),
        progress.getCrawledStories(),
        progress.getTotalChapters(),
        progress.getCrawledChapters(),
        progress.getTotalImages(),
        progress.getDownloadedImages(),
        String.format("Status: %s - %d/%d stories, %d/%d chapters",
            categoryJob.getStatus().name(),
            progress.getCrawledStories(), progress.getTotalStories(),
            progress.getCrawledChapters(), progress.getTotalChapters()),
        List.of(),
        startTime,
        progress.getUpdatedAt(),
        elapsedSeconds,
        storyProgress
    );
  }

  private CategoryCrawlProgressResponse createEmptyProgressResponse(UUID jobId) {
    return new CategoryCrawlProgressResponse(
        jobId,
        "PENDING",
        0, 0, 0, 0, 0, 0, 0, 0,
        "Initializing...",
        List.of(),
        now(), now(), 0L,
        Map.of()
    );
  }
}

