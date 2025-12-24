package com.truyengg.service.crawl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.ComicCrawlCheckpoint;
import com.truyengg.domain.enums.ChapterCrawlStatus;
import com.truyengg.domain.enums.CrawlEventType;
import com.truyengg.domain.enums.DownloadMode;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.exception.crawl.CrawlException;
import com.truyengg.model.dto.ChapterCrawlProcessingParams;
import com.truyengg.model.dto.ChapterImageInfo;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ChapterMetrics;
import com.truyengg.model.dto.ComicCrawlCheckpointParams;
import com.truyengg.model.dto.ComicCrawlUpdateParams;
import com.truyengg.model.dto.CrawlContext;
import com.truyengg.model.dto.CrawlRange;
import com.truyengg.model.dto.CrawlRequestParams;
import com.truyengg.model.dto.CrawlState;
import com.truyengg.model.dto.FinalizedComicCrawlParams;
import com.truyengg.model.dto.ImageProcessingContext;
import com.truyengg.model.dto.PauseContextParams;
import com.truyengg.model.request.CrawlRequest;
import com.truyengg.model.response.ChapterCrawlProgress;
import com.truyengg.model.response.ComicCrawlProgressResponse;
import com.truyengg.service.ComicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.truyengg.domain.enums.ChapterCrawlStatus.DOWNLOADING;
import static com.truyengg.domain.enums.ChapterCrawlStatus.PENDING;
import static com.truyengg.domain.enums.ComicCrawlStatus.CANCELLED;
import static com.truyengg.domain.enums.ComicCrawlStatus.COMPLETED;
import static com.truyengg.domain.enums.ComicCrawlStatus.FAILED;
import static com.truyengg.domain.enums.ComicCrawlStatus.PAUSED;
import static com.truyengg.domain.enums.ComicCrawlStatus.RUNNING;
import static com.truyengg.domain.enums.CrawlSourceType.detectSourceFromUrl;
import static com.truyengg.service.crawl.CrawlConstants.ERROR_PREFIX;
import static com.truyengg.service.crawl.CrawlConstants.IMAGE_FILE_PATTERN;
import static com.truyengg.service.crawl.CrawlConstants.KEY_IMAGES;
import static com.truyengg.service.crawl.CrawlConstants.KEY_MINIO_URLS;
import static com.truyengg.service.crawl.CrawlConstants.KEY_ORIGINAL_IMAGE_URLS;
import static com.truyengg.service.crawl.CrawlConstants.KEY_SUCCESS;
import static com.truyengg.service.crawl.CrawlConstants.MSG_DOWNLOADING_CHAPTER;
import static com.truyengg.service.crawl.CrawlConstants.MSG_DOWNLOADING_CHAPTERS_FROM;
import static com.truyengg.service.crawl.CrawlConstants.PATTERN_CHAP;
import static java.lang.Boolean.TRUE;
import static java.time.Duration.between;
import static java.time.ZonedDateTime.now;
import static java.util.Collections.emptyMap;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Component
@Slf4j
@RequiredArgsConstructor
public class ComicCrawlStrategy {

  private final CrawlHttpClient crawlHttpClient;
  private final CrawlImageProcessor crawlImageProcessor;
  private final ComicCrawlProgressService comicCrawlProgressService;
  private final ComicCrawlRepository comicCrawlRepository;
  private final ComicCrawlCheckpointService comicCrawlCheckpointService;
  private final ChapterCrawlService chapterCrawlService;
  private final CrawlEventService crawlEventService;
  private final PauseStateService pauseStateService;
  private final ProgressMessagePublisher progressMessagePublisher;
  private final ObjectMapper objectMapper;
  private final CrawlHandlerFactory handlerFactory;
  private final ComicService comicService;

  protected void updateProgress(ComicCrawlUpdateParams params) {
    var now = now();
    var elapsed = between(params.startTime(), now).getSeconds();

    // Try to calculate progress from database (more accurate, especially after resume)
    var detailsProgress = comicCrawlProgressService.calculateProgressFromDetails(params.crawlId());
    ComicCrawlProgressResponse progress;

    if (detailsProgress != null && detailsProgress.totalChapters() > 0) {
      // Use database-calculated progress but keep current message and status from params
      progress = new ComicCrawlProgressResponse(
          params.crawlId(),
          params.status(),
          detailsProgress.currentChapter(),
          detailsProgress.totalChapters(),
          detailsProgress.downloadedImages(),
          detailsProgress.totalImages(),
          params.chapterCrawlProgress() != null ? params.chapterCrawlProgress() : detailsProgress.chapterProgress(),
          params.currentMessage(),
          params.messages(),
          params.startTime(),
          now,
          elapsed
      );
    } else {
      // Fallback to params-based progress if no details available yet
      progress = new ComicCrawlProgressResponse(
          params.crawlId(), params.status(), params.currentChapter(), params.totalChapters(),
          params.downloadedImages(), params.totalImages(),
          params.chapterCrawlProgress() != null ? params.chapterCrawlProgress() : emptyMap(),
          params.currentMessage(), params.messages(), params.startTime(), now, elapsed
      );
    }

    comicCrawlProgressService.updateProgressCache(progress);
  }

  protected void finalizeCrawl(FinalizedComicCrawlParams params) {
    var elapsed = between(params.startTime(), now()).getSeconds();
    var finalProgress = new ComicCrawlProgressResponse(
        params.crawlId(),
        params.status(),
        params.downloadedChapters(),
        params.totalChapters(),
        0,
        0,
        params.chapterProgress() != null && !params.chapterProgress().isEmpty()
            ? params.chapterProgress() : emptyMap(),
        params.status() == COMPLETED ? "Crawl completed!" : "Crawl failed",
        params.messages(),
        params.startTime(),
        now(),
        elapsed
    );

    var updateParams = new ComicCrawlUpdateParams(params.crawlId(), finalProgress.status(),
        finalProgress.currentChapter(),
        finalProgress.totalChapters(), finalProgress.downloadedImages(),
        finalProgress.totalImages(), finalProgress.currentMessage(),
        finalProgress.messages(), params.startTime(), finalProgress.chapterProgress());
    updateProgress(updateParams);

    // Finalize crawl: update database and cleanup memory
    comicCrawlProgressService.finalizeCrawl(finalProgress);

    // Delete checkpoint when crawl completed/failed
    comicCrawlCheckpointService.deleteCheckpoint(params.crawlId());
    pauseStateService.remove(params.crawlId());
  }

  protected boolean handlePauseCheckpoint(ComicCrawlCheckpointParams params) {
    if (!isCrawlPaused(params.crawlId())) {
      return false;
    }
    saveImageLevelCheckpoint(params);
    return true;
  }

  private void handlePauseWithFullContext(PauseContextParams pauseParams) {
    var checkpointOpt = comicCrawlCheckpointService.getCheckpoint(pauseParams.crawlId());
    if (checkpointOpt.isPresent()) {
      var checkpoint = checkpointOpt.get();
      var checkpointParams = new ComicCrawlCheckpointParams(
          pauseParams.crawlId(),
          checkpoint.getCurrentChapterIndex(),
          checkpoint.getCurrentImageIndex(),
          checkpoint.getCurrentImageUrl(),
          checkpoint.getImageUrls(),
          pauseParams.crawledChapters(),
          pauseParams.chapterCrawlProgress(),
          pauseParams.totalChapters(),
          pauseParams.totalDownloadedImages(),
          pauseParams.totalImages(),
          pauseParams.messages(),
          pauseParams.startTime()
      );
      handlePauseCheckpoint(checkpointParams);
      comicCrawlProgressService.persistProgress(pauseParams.crawlId());
    }
  }

  private void saveImageLevelCheckpoint(ComicCrawlCheckpointParams params) {
    var crawlId = params.crawlId();
    var chapterIndex = params.chapterIndex();
    var currentImageIndex = params.currentImageIndex();
    var currentImageUrl = params.currentImageUrl();
    var imageUrls = params.imageUrls();
    var chapterCrawlProgressMap = params.chapterCrawlProgressMap();
    var crawledChapters = params.crawledChapters();
    var totalChapters = params.totalChapters();
    var totalDownloadedImages = params.totalDownloadedImages();
    var totalImages = params.totalImages();
    var messages = params.messages();
    var startTime = params.startTime();
    var chapterProgressJson = "";
    try {
      chapterProgressJson = objectMapper.writeValueAsString(chapterCrawlProgressMap);
    } catch (JsonProcessingException e) {
      log.error("Error serializing chapterCrawlProgress for crawl {}", crawlId, e);
    }

    var checkpoint = ComicCrawlCheckpoint.builder()
        .currentChapterIndex(chapterIndex)
        .currentImageIndex(currentImageIndex)
        .currentImageUrl(currentImageUrl)
        .imageUrls(imageUrls)
        .crawledChapters(crawledChapters)
        .chapterProgress(chapterProgressJson)
        .build();
    comicCrawlCheckpointService.saveCheckpoint(crawlId, checkpoint);

    // Create pause event
    var pauseReason = currentImageIndex != null
        ? String.format("Paused at image %d of chapter %d", currentImageIndex + 1, chapterIndex + 1)
        : "Paused at chapter " + (chapterIndex + 1);
    var pauseEventOpt = crawlEventService.getLatestEventByCrawlIdAndType(crawlId, CrawlEventType.PAUSE);
    if (pauseEventOpt.isPresent() && isNotEmpty(pauseEventOpt.get().getReason())) {
      pauseReason = "Paused: " + pauseEventOpt.get().getReason();
    }

    var updateParams = new ComicCrawlUpdateParams(crawlId, PAUSED, chapterIndex, totalChapters,
        totalDownloadedImages, totalImages, pauseReason, messages, startTime, chapterCrawlProgressMap);
    updateProgress(updateParams);
    // Persist to DB when paused
    comicCrawlProgressService.persistProgress(crawlId);
  }

  protected boolean handleCancelCheckpoint(ComicCrawlCheckpointParams params) {
    if (!isCrawlCancelled(params.crawlId())) {
      return false;
    }

    // Get reason from crawl message or latest cancel event
    var cancelMessage = "Cancelled at chapter " + (params.chapterIndex() + 1);
    var cancelEventOpt = crawlEventService.getLatestEventByCrawlIdAndType(
        params.crawlId(), CrawlEventType.CANCEL);
    if (cancelEventOpt.isPresent() && isNotEmpty(cancelEventOpt.get().getReason())) {
      cancelMessage = "Cancelled: " + cancelEventOpt.get().getReason();
    }

    // Update progress with cancelled status (no checkpoint saved for cancelled crawls)
    var updateParams = new ComicCrawlUpdateParams(params.crawlId(), CANCELLED,
        params.chapterIndex(), params.totalChapters(),
        params.totalDownloadedImages(), params.totalImages(),
        cancelMessage, params.messages(), params.startTime(),
        params.chapterCrawlProgressMap());
    updateProgress(updateParams);
    return true;
  }

  public boolean isCrawlPaused(UUID crawlId) {
    try {
      var paused = pauseStateService.isPaused(crawlId);
      if (paused != null) {
        return paused;
      }

      var crawlOpt = comicCrawlRepository.findById(crawlId);
      var isPaused = crawlOpt.isPresent() &&
          crawlOpt.get().getStatus() == PAUSED;

      if (isPaused) {
        pauseStateService.setPaused(crawlId);
      }

      return isPaused;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isCrawlCancelled(UUID crawlId) {
    try {
      var cancelled = pauseStateService.isCancelled(crawlId);
      if (cancelled != null) {
        return cancelled;
      }

      var crawlOpt = comicCrawlRepository.findById(crawlId);
      var isCancelled = crawlOpt.isPresent() &&
          crawlOpt.get().getStatus() == CANCELLED;

      if (isCancelled) {
        pauseStateService.setCancelled(crawlId);
      }

      return isCancelled;
    } catch (Exception e) {
      return false;
    }
  }

  public void crawl(CrawlRequestParams params) {
    try {
      var crawlContext = initializeCrawlContext(params);
      var chapterUrls = extractOrLoadChapterUrls(params, crawlContext);
      if (chapterUrls.isEmpty()) {
        throw new CrawlException("No chapters found.");
      }
      var crawlRange = determineCrawlRange(params.request(), chapterUrls, params.resumeFromIndex(), params.crawlId());
      var crawlState = initializeCrawlState(params, chapterUrls, crawlRange);
      processChapters(params, crawlContext, crawlState, chapterUrls, crawlRange);
    } catch (Exception e) {
      log.error("Error in async crawl", e);
      progressMessagePublisher.publishMessage(params.crawlId(), ERROR_PREFIX + e.getMessage());
      finalizeCrawl(new FinalizedComicCrawlParams(params.crawlId(), FAILED, 0, 0, params.messages(), params.startTime(), emptyMap()));
    }
  }

  private CrawlContext initializeCrawlContext(CrawlRequestParams params) {
    var crawlOpt = comicCrawlRepository.findById(params.crawlId());
    if (crawlOpt.isEmpty()) {
      throw new IllegalArgumentException("Crawl not found: " + params.crawlId());
    }
    var crawlEntity = crawlOpt.get();
    var downloadMode = crawlEntity.getDownloadMode() != null ? crawlEntity.getDownloadMode() : DownloadMode.FULL;
    var downloadChapters = crawlEntity.getDownloadChapters();

    var sourceType = detectSourceFromUrl(params.request().url());
    var handler = handlerFactory.getHandler(sourceType);
    var domain = handler.extractDomainFromUrl(params.request().url());
    progressMessagePublisher.publishMessage(params.crawlId(), "Server: " + domain + "/");

    var normalizedUrl = handler.normalizeUrl(params.request().url(), domain);
    if (handler.isHtmlBased() && normalizedUrl.contains(PATTERN_CHAP)) {
      progressMessagePublisher.publishMessage(params.crawlId(), "This is a single chapter link. Starting download...");
    }

    var comic = detectComicInfo(handler, params.request(), normalizedUrl, domain, params.crawlId());
    return new CrawlContext(handler, domain, normalizedUrl, comic, downloadMode, downloadChapters);
  }

  private Comic detectComicInfo(CrawlHandler handler, CrawlRequest request, String normalizedUrl, String domain,
                                UUID crawlId) {
    Comic comic = null;
    if (handler.isHtmlBased()) {
      var headers = crawlHttpClient.buildHeaders(domain);
      var htmlContent = crawlHttpClient.fetchUrl(normalizedUrl, headers, false);
      if (isNotBlank(htmlContent)) {
        var doc = Jsoup.parse(htmlContent);
        var comicInfo = handler.detectComicInfo(normalizedUrl, doc, null);
        comic = comicService.createOrUpdateComic(comicInfo);
        progressMessagePublisher.publishMessage(crawlId, "Detected and saved manga info: " + comic.getName());
      }
    } else {
      comic = detectComicInfoFromApi(handler, request, crawlId);
    }
    return comic;
  }

  private Comic detectComicInfoFromApi(CrawlHandler handler, CrawlRequest request, UUID crawlId) {
    var mangaId = extractMangaIdFromUrl(request.url());
    if (mangaId.isEmpty()) {
      throw new IllegalArgumentException("Invalid URL. Please enter URL in format https://mimihentai.com/g/60986.");
    }

    var headers = crawlHttpClient.buildHeaders(handler.getBaseUrl());
    var baseUrl = handler.getBaseUrl();
    var apiUrl = baseUrl + "/api/gallery/" + mangaId;
    var apiResponseJson = crawlHttpClient.fetchUrl(apiUrl, headers, true);

    Object apiResponse = null;
    if (isNotEmpty(apiResponseJson)) {
      try {
        apiResponse = objectMapper.readValue(apiResponseJson, Map.class);
      } catch (Exception e) {
        log.error("Failed to parse API response JSON", e);
      }
    }

    var htmlUrl = baseUrl + "/g/" + mangaId;
    var htmlContent = crawlHttpClient.fetchUrl(htmlUrl, headers, false);

    if (isNotBlank(htmlContent)) {
      var doc = Jsoup.parse(htmlContent);
      var comicInfo = handler.detectComicInfo(request.url(), doc, apiResponse);
      var comic = comicService.createOrUpdateComic(comicInfo);
      progressMessagePublisher.publishMessage(crawlId, "Detected and saved manga info: " + comic.getName());
      return comic;
    }
    return null;
  }

  private List<String> extractOrLoadChapterUrls(CrawlRequestParams params, CrawlContext context) {
    var storedCrawlEntity = comicCrawlRepository.findById(params.crawlId()).orElseThrow();
    if (params.resumeFromIndex() > 0 && storedCrawlEntity.getChapterUrls() != null
        && !storedCrawlEntity.getChapterUrls().isEmpty()) {
      progressMessagePublisher.publishMessage(params.crawlId(),
          "Using stored chapter URLs for resume (" + storedCrawlEntity.getChapterUrls().size() + " chapters)");
      return storedCrawlEntity.getChapterUrls();
    }
    progressMessagePublisher.publishMessage(params.crawlId(), "Extracting chapter list from HTML");
    var chapterUrls = context.handler().extractChapterList(context.normalizedUrl(), context.domain(), params.messages());
    storedCrawlEntity.setChapterUrls(chapterUrls);
    comicCrawlRepository.save(storedCrawlEntity);
    return chapterUrls;
  }

  private CrawlRange determineCrawlRange(CrawlRequest request, List<String> chapterUrls, int resumeFromIndex, UUID crawlId) {
    var startIndex = 0;
    var endIndex = chapterUrls.size();
    var partsOnly = request.partStart() != null || request.partEnd() != null;

    if (partsOnly) {
      startIndex = Math.max(0, (request.partStart() != null ? request.partStart() : 1) - 1);
      endIndex = Math.min(chapterUrls.size(), request.partEnd() != null ? request.partEnd() : chapterUrls.size());
      var chaptersToCrawl = endIndex - startIndex;
      progressMessagePublisher.publishMessage(crawlId,
          "Found " + chapterUrls.size() + " total chapters in manga. Will crawl " + chaptersToCrawl + " chapters from "
              + (startIndex + 1) + " to " + endIndex + ".");
      progressMessagePublisher.publishMessage(crawlId, MSG_DOWNLOADING_CHAPTERS_FROM + (startIndex + 1) + " to " + endIndex + ".");
    } else {
      progressMessagePublisher.publishMessage(crawlId,
          "Found " + chapterUrls.size() + " total chapters in manga. Will crawl all " + chapterUrls.size() + " chapters.");
    }

    if (resumeFromIndex > 0) {
      startIndex = Math.max(startIndex, resumeFromIndex);
    }

    return new CrawlRange(startIndex, endIndex, endIndex - startIndex);
  }

  private CrawlState initializeCrawlState(CrawlRequestParams params, List<String> chapterUrls, CrawlRange range) {
    var chapterCrawlProgress = new HashMap<String, ChapterCrawlProgress>();
    for (var idx = range.startIndex(); idx < range.endIndex() && idx < chapterUrls.size(); idx++) {
      final var chapterIndex = idx;
      var chapterUrl = chapterUrls.get(chapterIndex);
      var chapterKey = String.valueOf(chapterIndex);
      chapterCrawlProgress.put(chapterKey, new ChapterCrawlProgress(chapterIndex, chapterUrl, 0, 0, PENDING));
      var existingDetail = chapterCrawlService.getChapterCrawlByCrawlIdAll(params.crawlId()).stream()
          .filter(d -> d.getChapterIndex().equals(chapterIndex))
          .findFirst();
      if (existingDetail.isPresent() && existingDetail.get().getStatus() != PENDING) {
        chapterCrawlService.incrementRetryCount(params.crawlId(), chapterIndex);
      } else {
        chapterCrawlService.createChapterCrawl(params.crawlId(), chapterIndex, chapterUrl);
      }
    }

    var resumeCheckpointOpt = comicCrawlCheckpointService.getCheckpoint(params.crawlId());
    var resumeImageUrls = resumeCheckpointOpt
        .filter(cp -> params.resumeFromChapterIndex() == cp.getCurrentChapterIndex() && cp.getImageUrls() != null)
        .map(ComicCrawlCheckpoint::getImageUrls)
        .orElse(null);

    return new CrawlState(chapterCrawlProgress, resumeImageUrls, params.crawledChapters().size(), 0, 0);
  }

  private void processChapters(CrawlRequestParams params, CrawlContext context, CrawlState state,
                               List<String> chapterUrls, CrawlRange range) {
    var crawledChapters = new ArrayList<>(params.crawledChapters());

    for (var i = range.startIndex(); i < range.endIndex() && i < chapterUrls.size(); i++) {
      var shouldContinue = processSingleChapter(params, context, state, chapterUrls, i, crawledChapters);
      if (!shouldContinue) {
        return;
      }
    }

    finalizeCrawl(new FinalizedComicCrawlParams(params.crawlId(), COMPLETED, range.totalChapters(),
        state.getDownloadedChapters(), params.messages(), params.startTime(), state.getChapterCrawlProgress()));
  }

  private boolean processSingleChapter(CrawlRequestParams params, CrawlContext context, CrawlState state,
                                       List<String> chapterUrls, int chapterIndex, List<String> crawledChapters) {
    var chapterUrl = chapterUrls.get(chapterIndex);
    var chapterKey = String.valueOf(chapterIndex);

    var checkpointParams = new ComicCrawlCheckpointParams(params.crawlId(), chapterIndex, null, null, null,
        crawledChapters, state.getChapterCrawlProgress(), state.getChapterCrawlProgress().size(),
        state.getTotalDownloadedImages(), state.getTotalImages(), params.messages(), params.startTime());

    if (handleCancelCheckpoint(checkpointParams) || handlePauseCheckpoint(checkpointParams)) {
      return false;
    }

    updateChapterStatusToDownloading(params.crawlId(), chapterIndex, chapterUrl, chapterKey, state);

    var chapterParams = buildChapterProcessingParams(params, context, state, chapterUrl, chapterKey, chapterIndex);
    var processed = processChapterCrawl(context.handler(), chapterParams, now(), context.domain(),
        shouldDownloadImagesForChapter(chapterIndex, context.downloadMode(), context.downloadChapters()));

    if (isCrawlPaused(params.crawlId())) {
      handlePauseWithFullContext(new PauseContextParams(params.crawlId(), crawledChapters,
          state.getChapterCrawlProgress(), state.getChapterCrawlProgress().size(), state.getTotalDownloadedImages(),
          state.getTotalImages(), params.messages(), params.startTime()));
      return false;
    }

    updateStateAfterChapter(state, processed, crawledChapters, chapterUrl, chapterIndex, params);
    return true;
  }

  private void updateChapterStatusToDownloading(UUID crawlId, int chapterIndex, String chapterUrl,
                                                String chapterKey, CrawlState state) {
    state.getChapterCrawlProgress().put(chapterKey, new ChapterCrawlProgress(chapterIndex, chapterUrl, 0, 0, DOWNLOADING));
    chapterCrawlService.updateChapterCrawlProgress(crawlId, chapterIndex, 0, 0, 0, 0);
  }

  private ChapterCrawlProcessingParams buildChapterProcessingParams(CrawlRequestParams params, CrawlContext context,
                                                                    CrawlState state, String chapterUrl,
                                                                    String chapterKey, int chapterIndex) {
    var shouldResumeFromImage = (chapterIndex == params.resumeFromChapterIndex()
        && params.resumeFromImageIndex() >= 0 && state.getResumeImageUrls() != null);
    var chapterResumeFromImageIndex = shouldResumeFromImage ? params.resumeFromImageIndex() : -1;
    var chapterResumeImageUrls = shouldResumeFromImage ? state.getResumeImageUrls() : null;

    return new ChapterCrawlProcessingParams(chapterUrl, context.domain(), params.crawlId(), chapterKey, chapterIndex,
        state.getChapterCrawlProgress(), params.messages(), params.startTime(), state.getChapterCrawlProgress().size(),
        state.getTotalImages(), state.getTotalDownloadedImages(), context.comic(), chapterResumeFromImageIndex,
        chapterResumeImageUrls);
  }

  private void updateStateAfterChapter(CrawlState state, ChapterProcessResult processed, List<String> crawledChapters,
                                       String chapterUrl, int chapterIndex, CrawlRequestParams params) {
    state.setTotalImages(state.getTotalImages() + processed.images());
    state.setTotalDownloadedImages(state.getTotalDownloadedImages() + processed.images());
    if (processed.success()) {
      crawledChapters.add(chapterUrl);
      state.setDownloadedChapters(state.getDownloadedChapters() + 1);
    }

    var updateParams = new ComicCrawlUpdateParams(params.crawlId(), RUNNING, chapterIndex + 1,
        state.getChapterCrawlProgress().size(), state.getTotalDownloadedImages(), state.getTotalImages(),
        "Completed chapter " + (chapterIndex + 1), params.messages(), params.startTime(),
        state.getChapterCrawlProgress());
    updateProgress(updateParams);
  }

  private String extractMangaIdFromUrl(String url) {
    var pattern = compile("/g/(\\d+)");
    var matcher = pattern.matcher(url);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  private boolean shouldDownloadImagesForChapter(int chapterIndex, DownloadMode downloadMode, List<Integer> downloadChapters) {
    return switch (downloadMode) {
      case NONE -> false;
      case FULL -> true;
      case PARTIAL -> {
        // If downloadChapters is null or empty, download images for all chapters in the crawl range
        // If downloadChapters has values, only download for specified chapters
        // Note: chapterIndex is 0-based (array index), but downloadChapters contains 1-based indices (user input)
        var chapterNumber = chapterIndex + 1;
        yield downloadChapters == null || downloadChapters.isEmpty() || downloadChapters.contains(chapterNumber);
      }
    };
  }

  private ChapterProcessResult processChapterCrawl(CrawlHandler handler, ChapterCrawlProcessingParams chapterParams,
                                                   ZonedDateTime chapterStartTime, String domain,
                                                   boolean shouldDownloadImages) {
    updateChapterStatusToDownloading(chapterParams);
    var chapterResult = crawlChapterWithProgress(handler, chapterParams, domain, shouldDownloadImages);

    if (TRUE.equals(chapterResult.get("PAUSED"))) {
      return new ChapterProcessResult(false, 0);
    }

    var chapterMetrics = extractChapterMetrics(chapterResult);
    if (chapterMetrics.success() && chapterMetrics.images() > 0) {
      saveChapterInfo(handler, chapterParams, chapterMetrics, shouldDownloadImages);
      markChapterCompleted(chapterParams, chapterMetrics, chapterStartTime);
      return new ChapterProcessResult(true, chapterMetrics.images());
    }

    markChapterFailed(chapterParams, chapterResult);
    return new ChapterProcessResult(false, 0);
  }

  private void updateChapterStatusToDownloading(ChapterCrawlProcessingParams chapterParams) {
    chapterParams.chapterProgress().put(chapterParams.chapterKey(),
        new ChapterCrawlProgress(chapterParams.chapterIndex(), chapterParams.url(), 0, 0, DOWNLOADING));
    var comicParams = new ComicCrawlUpdateParams(chapterParams.crawlId(), RUNNING,
        chapterParams.chapterIndex() + 1, chapterParams.totalChapters(),
        chapterParams.currentTotalDownloadedImages(), chapterParams.currentTotalImages(),
        MSG_DOWNLOADING_CHAPTER + (chapterParams.chapterIndex() + 1), chapterParams.messages(),
        chapterParams.startTime(), chapterParams.chapterProgress());
    updateProgress(comicParams);
  }

  private Map<String, Object> crawlChapterWithProgress(CrawlHandler handler, ChapterCrawlProcessingParams chapterParams,
                                                       String domain, boolean shouldDownloadImages) {
    var resumeMessage = chapterParams.resumeFromImageIndex() >= 0
        ? " (resuming from image " + (chapterParams.resumeFromImageIndex() + 1) + ")"
        : "";
    progressMessagePublisher.publishMessage(chapterParams.crawlId(),
        MSG_DOWNLOADING_CHAPTER + (chapterParams.chapterIndex() + 1) + ": " + chapterParams.url() + resumeMessage);
    return crawlSingleChapterWithProgress(handler, chapterParams, domain, shouldDownloadImages);
  }

  private ChapterMetrics extractChapterMetrics(Map<String, Object> chapterResult) {
    var chapterImagesCount = chapterResult.get(KEY_IMAGES);
    var chapterSuccess = (Boolean) chapterResult.get(KEY_SUCCESS);
    @SuppressWarnings("unchecked")
    var imagePaths = (List<String>) chapterResult.get(KEY_MINIO_URLS);
    @SuppressWarnings("unchecked")
    var originalImagePaths = (List<String>) chapterResult.getOrDefault(KEY_ORIGINAL_IMAGE_URLS, new ArrayList<String>());
    var fileSizeBytes = chapterResult.get(CrawlConstants.FILE_SIZE_BYTES) != null
        ? ((Number) chapterResult.get(CrawlConstants.FILE_SIZE_BYTES)).longValue() : 0L;
    var requestCount = chapterResult.get(CrawlConstants.REQUEST_COUNT) != null
        ? ((Number) chapterResult.get(CrawlConstants.REQUEST_COUNT)).intValue() : 0;
    var errorCount = chapterResult.get(CrawlConstants.ERROR_COUNT) != null
        ? ((Number) chapterResult.get(CrawlConstants.ERROR_COUNT)).intValue() : 0;
    var images = chapterImagesCount != null ? ((Number) chapterImagesCount).intValue() : 0;

    return new ChapterMetrics(TRUE.equals(chapterSuccess), images, imagePaths, originalImagePaths,
        fileSizeBytes, requestCount, errorCount);
  }

  private void saveChapterInfo(CrawlHandler handler, ChapterCrawlProcessingParams chapterParams,
                               ChapterMetrics metrics, boolean shouldDownloadImages) {
    if (chapterParams.comic() == null || metrics.originalImagePaths().isEmpty()) {
      return;
    }

    try {
      var chapterDoc = fetchChapterDocument(handler, chapterParams);
      if (chapterDoc != null || !handler.isHtmlBased()) {
        var chapterImageInfos = createChapterImageInfoList(metrics.originalImagePaths(), metrics.imagePaths(),
            shouldDownloadImages);
        var chapterInfo = handler.detectChapterInfo(chapterParams.url(), chapterDoc, metrics.originalImagePaths(), null);
        var updatedChapterInfo = new ChapterInfo(chapterInfo.chapterName(), chapterInfo.chapterTitle(),
            chapterInfo.source(), chapterImageInfos);
        comicService.createOrUpdateChapter(chapterParams.comic(), updatedChapterInfo);
        progressMessagePublisher.publishMessage(chapterParams.crawlId(), "Saved chapter info: " + chapterInfo.chapterName());
      }
    } catch (Exception e) {
      log.warn("Failed to save chapter info for chapter {}: {}", chapterParams.chapterIndex(), e.getMessage());
    }
  }

  private Document fetchChapterDocument(CrawlHandler handler, ChapterCrawlProcessingParams chapterParams) {
    if (!handler.isHtmlBased()) {
      return null;
    }
    var chapterHeaders = crawlHttpClient.buildHeaders(chapterParams.domain());
    var chapterHtmlContent = crawlHttpClient.fetchUrl(chapterParams.url(), chapterHeaders, false);
    return isNotEmpty(chapterHtmlContent) ? Jsoup.parse(chapterHtmlContent) : null;
  }

  private void markChapterCompleted(ChapterCrawlProcessingParams chapterParams, ChapterMetrics metrics,
                                    ZonedDateTime chapterStartTime) {
    chapterParams.chapterProgress().put(chapterParams.chapterKey(),
        new ChapterCrawlProgress(chapterParams.chapterIndex(), chapterParams.url(), metrics.images(), metrics.images(),
            ChapterCrawlStatus.COMPLETED));
    var downloadTimeSeconds = between(chapterStartTime, now()).getSeconds();
    chapterCrawlService.markChapterCrawlCompleted(chapterParams.crawlId(), chapterParams.chapterIndex(),
        metrics.imagePaths(), metrics.originalImagePaths(), metrics.fileSizeBytes(), downloadTimeSeconds,
        metrics.images(), metrics.images(), metrics.requestCount(), metrics.errorCount());
  }

  private void markChapterFailed(ChapterCrawlProcessingParams chapterParams, Map<String, Object> chapterResult) {
    chapterParams.chapterProgress().put(chapterParams.chapterKey(),
        new ChapterCrawlProgress(chapterParams.chapterIndex(), chapterParams.url(), 0, 0, ChapterCrawlStatus.FAILED));

    var errorMessages = collectErrorMessages(chapterResult, chapterParams);
    chapterCrawlService.markChapterCrawlFailed(chapterParams.crawlId(), chapterParams.chapterIndex(), errorMessages);
  }

  private List<String> collectErrorMessages(Map<String, Object> chapterResult,
                                            ChapterCrawlProcessingParams chapterParams) {
    var errorMessages = new ArrayList<String>();
    if (chapterResult.containsKey(CrawlConstants.ERROR) && chapterResult.get(CrawlConstants.ERROR) != null) {
      errorMessages.add(String.valueOf(chapterResult.get(CrawlConstants.ERROR)));
    }
    chapterParams.messages().stream()
        .filter(msg -> msg != null && (msg.contains(ERROR_PREFIX) || msg.toLowerCase().contains(CrawlConstants.ERROR)
            || msg.toLowerCase().contains("failed")))
        .forEach(errorMessages::add);
    if (errorMessages.isEmpty()) {
      errorMessages.add("Failed to download chapter");
    }
    return errorMessages;
  }

  private List<ChapterImageInfo> createChapterImageInfoList(List<String> originalUrls, List<String> storedUrls, boolean downloaded) {
    var chapterImages = new ArrayList<ChapterImageInfo>();
    if (originalUrls == null || originalUrls.isEmpty()) {
      return chapterImages;
    }

    for (int i = 0; i < originalUrls.size(); i++) {
      var originalUrl = originalUrls.get(i);
      var storedUrl = (downloaded && storedUrls != null && i < storedUrls.size())
          ? storedUrls.get(i)
          : originalUrl; // Use original URL as stored URL if not downloaded

      chapterImages.add(new ChapterImageInfo(
          null, // id - will be set when saved
          null, // chapterId - will be set when saved
          storedUrl, // path - stored URL (or original if not downloaded)
          originalUrl, // originalUrl
          i + 1, // imageOrder
          null, // manualOrder
          downloaded && storedUrls != null && i < storedUrls.size(), // isDownloaded
          true, // isVisible
          null, // deletedAt
          null, // createdAt
          null // updatedAt
      ));
    }
    return chapterImages;
  }

  private Map<String, Object> crawlSingleChapterWithProgress(CrawlHandler handler,
                                                             ChapterCrawlProcessingParams params,
                                                             String domain,
                                                             boolean shouldDownloadImages) {
    var result = initializeResultMap();

    try {
      var imageUrls = extractOrResumeImageUrls(handler, params);
      if (imageUrls.isEmpty()) {
        progressMessagePublisher.publishMessage(params.crawlId(), "No images found.");
        return result;
      }

      saveImageUrlsToCrawl(params, imageUrls);
      updateChapterProgressWithTotalImages(params, imageUrls.size(), shouldDownloadImages);

      if (shouldDownloadImages) {
        var downloadResult = downloadImages(params, domain, imageUrls);
        result.putAll(downloadResult);
      } else {
        setExtractionOnlyResult(result, params.crawlId(), imageUrls.size());
      }

      result.put(KEY_MINIO_URLS, new ArrayList<String>());
      result.put(KEY_ORIGINAL_IMAGE_URLS, imageUrls);
      return result;
    } catch (Exception e) {
      log.error("Error crawling single chapter with progress", e);
      progressMessagePublisher.publishMessage(params.crawlId(), ERROR_PREFIX + e.getMessage());
      return result;
    }
  }

  private Map<String, Object> initializeResultMap() {
    var result = new HashMap<String, Object>();
    result.put(KEY_SUCCESS, false);
    result.put(KEY_IMAGES, 0);
    result.put(KEY_MINIO_URLS, new ArrayList<String>());
    return result;
  }

  private List<String> extractOrResumeImageUrls(CrawlHandler handler, ChapterCrawlProcessingParams params) {
    if (params.resumeImageUrls() != null && !params.resumeImageUrls().isEmpty()) {
      progressMessagePublisher.publishMessage(params.crawlId(),
          "Using stored image URLs for resume (" + params.resumeImageUrls().size() + " images)");
      return params.resumeImageUrls();
    }
    return handler.extractImageUrls(params);
  }

  private void saveImageUrlsToCrawl(ChapterCrawlProcessingParams params, List<String> imageUrls) {
    var imageCrawlEntity = comicCrawlRepository.findById(params.crawlId()).orElseThrow();
    var existingImageUrlsMap = imageCrawlEntity.getChapterImageUrls();
    if (existingImageUrlsMap == null || !existingImageUrlsMap.containsKey(params.chapterIndex())) {
      var imageUrlsMap = existingImageUrlsMap != null
          ? new HashMap<>(existingImageUrlsMap)
          : new HashMap<Integer, List<String>>();
      imageUrlsMap.put(params.chapterIndex(), imageUrls);
      imageCrawlEntity.setChapterImageUrls(imageUrlsMap);
      comicCrawlRepository.save(imageCrawlEntity);
    }
  }

  private void updateChapterProgressWithTotalImages(ChapterCrawlProcessingParams params, int totalImages,
                                                    boolean shouldDownloadImages) {
    var statusMessage = shouldDownloadImages ? DOWNLOADING : PENDING;
    params.chapterProgress().put(params.chapterKey(),
        new ChapterCrawlProgress(params.chapterIndex(), params.url(), 0, totalImages, statusMessage));
  }

  private Map<String, Object> downloadImages(ChapterCrawlProcessingParams params, String domain,
                                             List<String> imageUrls) {
    var result = new HashMap<String, Object>();
    var downloadContext = createImageDownloadContext(params, domain);
    var downloadMetrics = processImages(params, imageUrls, downloadContext, result);
    result.put(CrawlConstants.FILE_SIZE_BYTES, downloadMetrics.totalFileSize());
    result.put(CrawlConstants.REQUEST_COUNT, downloadMetrics.totalRequests());
    result.put(CrawlConstants.ERROR_COUNT, downloadMetrics.totalErrors());
    result.put(KEY_SUCCESS, downloadMetrics.downloadedImages() > 0);
    result.put(KEY_IMAGES, downloadMetrics.downloadedImages());
    return result;
  }

  private ImageDownloadContext createImageDownloadContext(ChapterCrawlProcessingParams params, String domain) {
    var comicId = params.comic() != null ? String.valueOf(params.comic().getId()) : "unknown";
    var chapterId = "chapter-" + params.chapterIndex();
    var headers = crawlHttpClient.buildHeaders(domain);
    return new ImageDownloadContext(comicId, chapterId, headers);
  }

  private DownloadMetrics processImages(ChapterCrawlProcessingParams params, List<String> imageUrls,
                                        ImageDownloadContext downloadContext, Map<String, Object> result) {
    var imagePaths = new ArrayList<String>();
    var startImageIndex = Math.max(params.resumeFromImageIndex(), 0);
    var totalImages = imageUrls.size();
    var downloadedImages = 0;
    var totalFileSize = 0L;
    var totalRequests = 0;
    var totalErrors = 0;

    for (var i = startImageIndex; i < imageUrls.size(); i++) {
      if (isCrawlCancelled(params.crawlId())) {
        break;
      }

      var imageUrl = imageUrls.get(i);
      var fileName = String.format(IMAGE_FILE_PATTERN, i + 1);
      var processingContext = new ImageProcessingContext(imageUrl, downloadContext.headers(),
          downloadContext.comicId(), downloadContext.chapterId(), fileName, totalImages, params.chapterIndex(),
          downloadedImages);
      var imageResult = crawlImageProcessor.processImageWithProgress(processingContext, params, imagePaths);

      downloadedImages += imageResult.successCount();
      totalFileSize += imageResult.fileSizeBytes();
      totalRequests += imageResult.requestCount();
      totalErrors += imageResult.errorCount();

      updateImageProgress(params, downloadedImages, totalImages);

      if (isCrawlPaused(params.crawlId())) {
        handleImageLevelPause(params, imageUrls, i, imageUrl, downloadedImages, totalImages);
        result.put("PAUSED", true);
        break;
      }
    }

    result.put(KEY_MINIO_URLS, imagePaths);
    return new DownloadMetrics(downloadedImages, totalFileSize, totalRequests, totalErrors);
  }

  private void handleImageLevelPause(ChapterCrawlProcessingParams params, List<String> imageUrls, int imageIndex,
                                     String imageUrl, int downloadedImages, int totalImages) {
    var totalDownloadedImages = params.currentTotalDownloadedImages() + downloadedImages;
    var totalImagesCount = params.currentTotalImages() + totalImages;
    var checkpointParams = new ComicCrawlCheckpointParams(params.crawlId(), params.chapterIndex(), imageIndex,
        imageUrl, imageUrls, new ArrayList<>(), params.chapterProgress(), params.totalChapters(),
        totalDownloadedImages, totalImagesCount, params.messages(), params.startTime());
    saveImageLevelCheckpoint(checkpointParams);
  }

  private void updateImageProgress(ChapterCrawlProcessingParams params, int downloadedImages, int totalImages) {
    var currentChapter = params.chapterIndex() + 1;
    var totalDownloadedImages = params.currentTotalDownloadedImages() + downloadedImages;
    var totalImagesCount = params.currentTotalImages() + totalImages;
    var currentMessage = String.format("Downloading image %d/%d of chapter %d", downloadedImages, totalImages,
        currentChapter);

    var updateParams = new ComicCrawlUpdateParams(params.crawlId(), RUNNING, currentChapter,
        params.totalChapters(), totalDownloadedImages, totalImagesCount, currentMessage, params.messages(),
        params.startTime(), params.chapterProgress());
    updateProgress(updateParams);
  }

  private void setExtractionOnlyResult(Map<String, Object> result, UUID crawlId, int totalImages) {
    progressMessagePublisher.publishMessage(crawlId, "Extracted " + totalImages + " image URLs (no download)");
    result.put(KEY_SUCCESS, true);
    result.put(KEY_IMAGES, totalImages);
    result.put(CrawlConstants.FILE_SIZE_BYTES, 0L);
    result.put(CrawlConstants.REQUEST_COUNT, 0);
    result.put(CrawlConstants.ERROR_COUNT, 0);
  }

  private record ImageDownloadContext(String comicId, String chapterId, List<String> headers) {
  }

  private record DownloadMetrics(int downloadedImages, long totalFileSize, int totalRequests, int totalErrors) {
  }

  private record ChapterProcessResult(boolean success, int images) {
  }
}

