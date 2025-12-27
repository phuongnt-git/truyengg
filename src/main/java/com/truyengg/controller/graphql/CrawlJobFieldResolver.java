package com.truyengg.controller.graphql;

import com.truyengg.domain.entity.CrawlCheckpoint;
import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.domain.entity.CrawlProgress;
import com.truyengg.domain.entity.CrawlSettings;
import com.truyengg.domain.enums.CrawlType;
import com.truyengg.domain.repository.CrawlCheckpointRepository;
import com.truyengg.domain.repository.CrawlJobRepository;
import com.truyengg.domain.repository.CrawlProgressRepository;
import com.truyengg.domain.repository.CrawlSettingsRepository;
import com.truyengg.model.graphql.AggregatedStats;
import com.truyengg.model.graphql.Connection;
import com.truyengg.model.graphql.CrawlJobFilter;
import com.truyengg.model.graphql.CrawlJobSort;
import com.truyengg.model.graphql.FailedItemsResult;
import com.truyengg.model.graphql.ImageDownloadStatus;
import com.truyengg.model.graphql.MessageDto;
import com.truyengg.model.graphql.MessageFilter;
import com.truyengg.model.graphql.PageInfo;
import com.truyengg.service.crawl.CrawlJobQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.truyengg.controller.graphql.CrawlJobCounterUtils.countJobs;
import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/**
 * GraphQL field resolvers for CrawlJob type.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class CrawlJobFieldResolver {

  CrawlJobRepository jobRepository;
  CrawlProgressRepository progressRepository;
  CrawlCheckpointRepository checkpointRepository;
  CrawlSettingsRepository settingsRepository;
  CrawlJobQueryService queryService;

  /**
   * Resolve type field (maps crawlType to type).
   */
  @SchemaMapping(typeName = "CrawlJob", field = "type")
  public CrawlType type(CrawlJob job) {
    return job.getCrawlType();
  }

  /**
   * Resolve percent field (calculated from completedItems / totalItems).
   */
  @SchemaMapping(typeName = "CrawlJob", field = "percent")
  public float percent(CrawlJob job) {
    if (job.getTotalItems() == 0) {
      return 0.0f;
    }
    return (float) (job.getCompletedItems() * 100.0 / job.getTotalItems());
  }

  /**
   * Resolve hasChildren field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "hasChildren")
  public boolean hasChildren(CrawlJob job) {
    return jobRepository.existsByParentJobId(job.getId());
  }

  /**
   * Resolve childrenCount field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "childrenCount")
  public int childrenCount(CrawlJob job) {
    return (int) jobRepository.countByParentJobId(job.getId());
  }

  /**
   * Resolve children connection with filter, sort, and pagination.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "children")
  public Connection<CrawlJob> children(
      CrawlJob job,
      @Argument Integer first,
      @Argument String after,
      @Argument CrawlJobFilter filter,
      @Argument List<CrawlJobSort> sort
  ) {
    return queryService.findChildrenWithFilter(
        job.getId(),
        filter,
        sort,
        first != null ? first : 10,
        after
    );
  }

  /**
   * Resolve progress field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "progress")
  public CrawlProgress progress(CrawlJob job) {
    return progressRepository.findById(job.getId()).orElse(null);
  }

  /**
   * Resolve checkpoint field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "checkpoint")
  public CrawlCheckpoint checkpoint(CrawlJob job) {
    return checkpointRepository.findById(job.getId()).orElse(null);
  }

  /**
   * Resolve settings field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "settings")
  public CrawlSettings settings(CrawlJob job) {
    return settingsRepository.findById(job.getId()).orElse(null);
  }

  /**
   * Resolve aggregatedStats field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "aggregatedStats")
  public AggregatedStats aggregatedStats(CrawlJob job) {
    var descendants = jobRepository.findByRootJobId(job.getId());
    if (isEmpty(descendants)) {
      return emptyStats();
    }

    var counter = countJobs(descendants);
    var progressSum = descendants.stream()
        .filter(desc -> desc.getTotalItems() > 0)
        .mapToDouble(desc -> desc.getCompletedItems() * 100.0 / desc.getTotalItems())
        .sum();
    var avgProgress = progressSum / descendants.size();

    return AggregatedStats.builder()
        .totalChapters(counter.getChapter())
        .totalImages(counter.getImage())
        .totalBytes(0)
        .byStatus(AggregatedStats.StatusCounts.builder()
            .pending(counter.getPending())
            .running(counter.getRunning())
            .completed(counter.getCompleted())
            .failed(counter.getFailed())
            .paused(counter.getPaused())
            .cancelled(counter.getCancelled())
            .build())
        .byType(AggregatedStats.TypeCounts.builder()
            .category(counter.getCategory())
            .comic(counter.getComic())
            .chapter(counter.getChapter())
            .image(counter.getImage())
            .build())
        .avgProgress(avgProgress)
        .build();
  }

  /**
   * Resolve messages connection with filter and pagination.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "messages")
  public Connection<MessageDto> messages(
      CrawlJob job,
      @Argument Integer first,
      @Argument String after,
      @Argument Integer last,
      @Argument String before,
      @Argument MessageFilter filter
  ) {
    var progress = progressRepository.findById(job.getId()).orElse(null);
    if (progress == null || isEmpty(progress.getMessages())) {
      return emptyMessageConnection();
    }

    var messages = progress.getMessages();
    var filteredMessages = filterMessages(messages, filter);

    if (filteredMessages.isEmpty()) {
      return emptyMessageConnection();
    }

    // Apply cursor-based pagination
    var startIndex = 0;
    var endIndex = filteredMessages.size();
    var pageSize = first != null ? first : (last != null ? last : 50);

    // Forward pagination with 'after' cursor
    if (after != null) {
      try {
        startIndex = Integer.parseInt(after) + 1;
      } catch (NumberFormatException e) {
        log.debug("Invalid 'after' cursor: {}", after);
      }
    }

    // Backward pagination with 'before' cursor
    if (before != null) {
      try {
        endIndex = Integer.parseInt(before);
      } catch (NumberFormatException e) {
        log.debug("Invalid 'before' cursor: {}", before);
      }
    }

    // Apply page size
    if (last != null) {
      // Backward: take last N items before endIndex
      startIndex = Math.max(startIndex, endIndex - pageSize);
    } else {
      // Forward: take first N items after startIndex
      endIndex = Math.min(endIndex, startIndex + pageSize);
    }

    // Ensure valid bounds
    startIndex = Math.max(0, startIndex);
    endIndex = Math.min(filteredMessages.size(), endIndex);

    // Convert to MessageDto
    var messageDtos = new ArrayList<MessageDto>();
    for (var i = startIndex; i < endIndex; i++) {
      messageDtos.add(MessageDto.fromString(filteredMessages.get(i), i));
    }

    var edges = messageDtos.stream()
        .map(msg -> Connection.Edge.<MessageDto>builder()
            .node(msg)
            .cursor(String.valueOf(msg.getId()))
            .build())
        .toList();

    return Connection.<MessageDto>builder()
        .edges(edges)
        .pageInfo(PageInfo.builder()
            .hasNextPage(endIndex < filteredMessages.size())
            .hasPreviousPage(startIndex > 0)
            .startCursor(edges.isEmpty() ? null : edges.getFirst().getCursor())
            .endCursor(edges.isEmpty() ? null : edges.getLast().getCursor())
            .build())
        .totalCount(filteredMessages.size())
        .build();
  }

  /**
   * Resolve failedItemsList field.
   */
  @SchemaMapping(typeName = "CrawlJob", field = "failedItemsList")
  public FailedItemsResult failedItemsList(CrawlJob job) {
    var checkpoint = checkpointRepository.findById(job.getId()).orElse(null);
    var failedIndices = checkpoint != null ? checkpoint.getFailedItemIndices() : null;

    if (isEmpty(failedIndices)) {
      return FailedItemsResult.empty();
    }

    var items = failedIndices.stream()
        .map(index -> FailedItemsResult.FailedItem.builder()
            .index(index)
            .retryCount(0)
            .build())
        .toList();

    return FailedItemsResult.builder()
        .totalCount(items.size())
        .items(items)
        .build();
  }

  /**
   * Resolve images field (for IMAGE type jobs with downloadedImages in stateSnapshot).
   */
  @SchemaMapping(typeName = "CrawlJob", field = "images")
  @SuppressWarnings("unchecked")
  public List<ImageDownloadStatus> images(CrawlJob job) {
    var checkpoint = checkpointRepository.findById(job.getId()).orElse(null);
    if (checkpoint == null || checkpoint.getStateSnapshot() == null) {
      return emptyList();
    }

    var snapshot = checkpoint.getStateSnapshot();
    var downloadedImages = (List<Map<String, Object>>) snapshot.get("downloadedImages");
    if (isEmpty(downloadedImages)) {
      return emptyList();
    }

    return downloadedImages.stream()
        .map(img -> ImageDownloadStatus.builder()
            .index(((Number) img.getOrDefault("index", 0)).intValue())
            .originalUrl((String) img.get("url"))
            .path((String) img.get("path"))
            .blurhash((String) img.get("blurhash"))
            .status(parseImageStatus((String) img.get("status")))
            .size(img.get("size") != null ? ((Number) img.get("size")).longValue() : null)
            .error((String) img.get("error"))
            .build())
        .toList();
  }

  // ===== CrawlCheckpoint field resolvers =====

  @SchemaMapping(typeName = "CrawlCheckpoint", field = "hasFailedItems")
  public boolean hasFailedItems(CrawlCheckpoint checkpoint) {
    return checkpoint.hasFailedItems();
  }

  // ===== CrawlSettings field resolvers =====

  @SchemaMapping(typeName = "CrawlSettings", field = "hasRange")
  public boolean hasRange(CrawlSettings settings) {
    return settings.hasRange();
  }

  // ===== Helper methods =====

  private List<String> filterMessages(List<String> messages, MessageFilter filter) {
    if (filter == null) {
      return messages;
    }

    return messages.stream()
        .filter(msg -> {
          // Filter by level
          if (isNotEmpty(filter.getLevels())) {
            var hasMatchingLevel = false;
            for (var level : filter.getLevels()) {
              if (level == MessageFilter.MessageLevel.ERROR &&
                  (msg.contains("[ERROR]") || msg.toLowerCase().contains("error") || msg.toLowerCase().contains("failed"))) {
                hasMatchingLevel = true;
                break;
              }
              if (level == MessageFilter.MessageLevel.WARN &&
                  (msg.contains("[WARN]") || msg.toLowerCase().contains("warn"))) {
                hasMatchingLevel = true;
                break;
              }
              if (level == MessageFilter.MessageLevel.INFO) {
                hasMatchingLevel = true;
                break;
              }
            }
            if (!hasMatchingLevel) return false;
          }

          // Filter by search
          if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            return msg.toLowerCase().contains(filter.getSearch().toLowerCase());
          }

          return true;
        })
        .toList();
  }

  private ImageDownloadStatus.Status parseImageStatus(String status) {
    if (status == null) return ImageDownloadStatus.Status.PENDING;
    return switch (status.toUpperCase()) {
      case "COMPLETED" -> ImageDownloadStatus.Status.COMPLETED;
      case "FAILED" -> ImageDownloadStatus.Status.FAILED;
      case "DOWNLOADING" -> ImageDownloadStatus.Status.DOWNLOADING;
      case "SKIPPED" -> ImageDownloadStatus.Status.SKIPPED;
      default -> ImageDownloadStatus.Status.PENDING;
    };
  }

  private AggregatedStats emptyStats() {
    return AggregatedStats.builder()
        .totalChapters(0)
        .totalImages(0)
        .totalBytes(0)
        .byStatus(AggregatedStats.StatusCounts.builder()
            .pending(0).running(0).completed(0).failed(0).paused(0).cancelled(0)
            .build())
        .byType(AggregatedStats.TypeCounts.builder()
            .category(0).comic(0).chapter(0).image(0)
            .build())
        .avgProgress(0)
        .build();
  }

  private Connection<MessageDto> emptyMessageConnection() {
    return Connection.<MessageDto>builder()
        .edges(emptyList())
        .pageInfo(PageInfo.builder()
            .hasNextPage(false)
            .hasPreviousPage(false)
            .build())
        .totalCount(0)
        .build();
  }
}
