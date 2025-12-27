package com.truyengg.service.crawl;

import com.truyengg.domain.entity.CrawlJob;
import com.truyengg.model.graphql.Connection;
import com.truyengg.model.graphql.CrawlJobFilter;
import com.truyengg.model.graphql.CrawlJobSort;
import com.truyengg.model.graphql.PageInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.springframework.util.StringUtils.hasText;

/**
 * Service for building dynamic GraphQL queries with filtering, sorting, and cursor-based pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlJobQueryService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  EntityManager entityManager;

  /**
   * Find jobs with dynamic filtering, sorting, and cursor-based pagination.
   */
  public Connection<CrawlJob> findJobsWithFilter(
      CrawlJobFilter filter,
      List<CrawlJobSort> sorts,
      Integer first,
      String after,
      Integer last,
      String before
  ) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(CrawlJob.class);
    var root = query.from(CrawlJob.class);

    // Build predicates from filter
    var predicates = buildPredicates(cb, root, filter);

    // Handle cursor pagination
    var pageSize = resolvePageSize(first, last);
    var cursorId = decodeCursor(after != null ? after : before);

    if (cursorId != null) {
      // Forward pagination (after cursor)
      if (after != null) {
        predicates.add(cb.greaterThan(root.get("id"), cursorId));
      } else {
        // Backward pagination (before cursor)
        predicates.add(cb.lessThan(root.get("id"), cursorId));
      }
    }

    query.where(predicates.toArray(new Predicate[0]));

    // Build ORDER BY
    var orders = buildOrders(cb, root, sorts);
    query.orderBy(orders);

    // Execute query with limit
    var results = entityManager.createQuery(query)
        .setMaxResults(pageSize + 1) // Fetch one extra to determine hasNextPage
        .getResultList();

    // Determine pagination info
    var hasMorePages = results.size() > pageSize;
    if (hasMorePages) {
      results = results.subList(0, pageSize);
    }

    // Get total count (cached)
    var totalCount = countJobsWithFilter(filter);

    // Build edges
    var edges = results.stream()
        .map(job -> Connection.Edge.<CrawlJob>builder()
            .node(job)
            .cursor(encodeCursor(job.getId()))
            .build())
        .toList();

    // Build page info
    var pageInfo = PageInfo.builder()
        .hasNextPage(hasMorePages)
        .hasPreviousPage(cursorId != null)
        .startCursor(edges.isEmpty() ? null : edges.getFirst().getCursor())
        .endCursor(edges.isEmpty() ? null : edges.getLast().getCursor())
        .build();

    return Connection.<CrawlJob>builder()
        .edges(edges)
        .pageInfo(pageInfo)
        .totalCount(totalCount)
        .build();
  }

  /**
   * Count jobs with filter (cacheable for pagination).
   */
  @Cacheable(value = "jobCounts", key = "#filter != null ? #filter.hashCode() : 'all'")
  public long countJobsWithFilter(CrawlJobFilter filter) {
    var cb = entityManager.getCriteriaBuilder();
    var query = cb.createQuery(Long.class);
    var root = query.from(CrawlJob.class);

    var predicates = buildPredicates(cb, root, filter);
    query.select(cb.count(root));
    query.where(predicates.toArray(new Predicate[0]));

    return entityManager.createQuery(query).getSingleResult();
  }

  /**
   * Find search suggestions for autocomplete.
   */
  public List<String> findSearchSuggestions(String query, int limit) {
    if (!hasText(query) || query.length() < 2) {
      return List.of();
    }

    var searchPattern = "%" + query.toLowerCase() + "%";

    // Native query for better performance with DISTINCT + LIMIT
    var sql = """
        SELECT DISTINCT target_name
        FROM crawl_jobs
        WHERE LOWER(target_name) LIKE :pattern
        AND target_name IS NOT NULL
        AND deleted_at IS NULL
        ORDER BY target_name
        LIMIT :limit
        """;

    @SuppressWarnings("unchecked")
    var results = (List<String>) entityManager.createNativeQuery(sql)
        .setParameter("pattern", searchPattern)
        .setParameter("limit", limit)
        .getResultList();

    return results;
  }

  /**
   * Find children of a job with filter, sort, and pagination.
   */
  public Connection<CrawlJob> findChildrenWithFilter(
      UUID parentJobId,
      CrawlJobFilter filter,
      List<CrawlJobSort> sorts,
      Integer first,
      String after
  ) {
    // Create a filter that includes parent job constraint
    var childFilter = filter != null ? filter : new CrawlJobFilter();
    childFilter.setParentJobId(parentJobId);

    return findJobsWithFilter(childFilter, sorts, first, after, null, null);
  }

  // ===== Private helper methods =====

  private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<CrawlJob> root, CrawlJobFilter filter) {
    var predicates = new ArrayList<Predicate>();

    if (filter == null) {
      // Default: only root jobs
      predicates.add(cb.isNull(root.get("parentJob")));
      return predicates;
    }

    // Type filter
    if (isNotEmpty(filter.getTypes())) {
      predicates.add(root.get("crawlType").in(filter.getTypes()));
    }

    // Exclude types
    if (isNotEmpty(filter.getExcludeTypes())) {
      predicates.add(cb.not(root.get("crawlType").in(filter.getExcludeTypes())));
    }

    // Status filter
    if (isNotEmpty(filter.getStatuses())) {
      predicates.add(root.get("status").in(filter.getStatuses()));
    }

    // Exclude statuses
    if (isNotEmpty(filter.getExcludeStatuses())) {
      predicates.add(cb.not(root.get("status").in(filter.getExcludeStatuses())));
    }

    // Download modes
    if (isNotEmpty(filter.getDownloadModes())) {
      predicates.add(root.get("downloadMode").in(filter.getDownloadModes()));
    }

    // Text search (ILIKE on name, url, slug)
    if (hasText(filter.getSearch())) {
      var searchPattern = "%" + filter.getSearch().toLowerCase() + "%";
      predicates.add(cb.or(
          cb.like(cb.lower(root.get("targetName")), searchPattern),
          cb.like(cb.lower(root.get("targetUrl")), searchPattern),
          cb.like(cb.lower(root.get("targetSlug")), searchPattern)
      ));
    }

    // URL contains
    if (hasText(filter.getUrlContains())) {
      predicates.add(cb.like(root.get("targetUrl"), "%" + filter.getUrlContains() + "%"));
    }

    // URL starts with
    if (hasText(filter.getUrlStartsWith())) {
      predicates.add(cb.like(root.get("targetUrl"), filter.getUrlStartsWith() + "%"));
    }

    // Date range filters
    if (filter.getCreatedAfter() != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedAfter()));
    }
    if (filter.getCreatedBefore() != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.getCreatedBefore()));
    }
    if (filter.getStartedAfter() != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), filter.getStartedAfter()));
    }
    if (filter.getStartedBefore() != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), filter.getStartedBefore()));
    }
    if (filter.getCompletedAfter() != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("completedAt"), filter.getCompletedAfter()));
    }
    if (filter.getCompletedBefore() != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get("completedAt"), filter.getCompletedBefore()));
    }

    // Progress range
    if (filter.getPercentMin() != null || filter.getPercentMax() != null) {
      // Calculate percent: (completedItems * 100.0 / NULLIF(totalItems, 0))
      var percentExpr = cb.quot(
          cb.prod(root.<Integer>get("completedItems"), 100.0),
          cb.<Number>selectCase()
              .when(cb.equal(root.get("totalItems"), 0), 1)
              .otherwise(root.get("totalItems"))
      );

      if (filter.getPercentMin() != null) {
        predicates.add(cb.ge(percentExpr, filter.getPercentMin()));
      }
      if (filter.getPercentMax() != null) {
        predicates.add(cb.le(percentExpr, filter.getPercentMax()));
      }
    }

    // Total items range
    if (filter.getTotalItemsMin() != null) {
      predicates.add(cb.ge(root.get("totalItems"), filter.getTotalItemsMin()));
    }
    if (filter.getTotalItemsMax() != null) {
      predicates.add(cb.le(root.get("totalItems"), filter.getTotalItemsMax()));
    }

    // Failed items range
    if (filter.getFailedItemsMin() != null) {
      predicates.add(cb.ge(root.get("failedItems"), filter.getFailedItemsMin()));
    }
    if (filter.getFailedItemsMax() != null) {
      predicates.add(cb.le(root.get("failedItems"), filter.getFailedItemsMax()));
    }

    // Root only (no parent)
    if (Boolean.TRUE.equals(filter.getRootOnly())) {
      predicates.add(cb.isNull(root.get("parentJob")));
    }

    // Parent job filter
    if (filter.getParentJobId() != null) {
      predicates.add(cb.equal(root.get("parentJob").get("id"), filter.getParentJobId()));
    }

    // Root job filter
    if (filter.getRootJobId() != null) {
      predicates.add(cb.equal(root.get("rootJob").get("id"), filter.getRootJobId()));
    }

    // Depth filter
    if (filter.getDepth() != null) {
      predicates.add(cb.equal(root.get("depth"), filter.getDepth()));
    }
    if (filter.getDepthMin() != null) {
      predicates.add(cb.ge(root.get("depth"), filter.getDepthMin()));
    }
    if (filter.getDepthMax() != null) {
      predicates.add(cb.le(root.get("depth"), filter.getDepthMax()));
    }

    // Has children filter (requires subquery)
    if (filter.getHasChildren() != null) {
      var subquery = cb.createQuery().subquery(Long.class);
      var childRoot = subquery.from(CrawlJob.class);
      subquery.select(cb.count(childRoot));
      subquery.where(cb.equal(childRoot.get("parentJob"), root));

      if (Boolean.TRUE.equals(filter.getHasChildren())) {
        predicates.add(cb.greaterThan(subquery, 0L));
      } else {
        predicates.add(cb.equal(subquery, 0L));
      }
    }

    // Retry count range
    if (filter.getRetryCountMin() != null) {
      predicates.add(cb.ge(root.get("retryCount"), filter.getRetryCountMin()));
    }
    if (filter.getRetryCountMax() != null) {
      predicates.add(cb.le(root.get("retryCount"), filter.getRetryCountMax()));
    }

    return predicates;
  }

  private List<Order> buildOrders(CriteriaBuilder cb, Root<CrawlJob> root, List<CrawlJobSort> sorts) {
    if (isEmpty(sorts)) {
      // Default sort: createdAt DESC
      return List.of(cb.desc(root.get("createdAt")));
    }

    return sorts.stream()
        .map(sort -> {
          var fieldName = toFieldName(sort.getField());
          var path = root.<Comparable<?>>get(fieldName);
          return sort.getDirection() == CrawlJobSort.SortDirection.ASC
              ? cb.asc(path)
              : cb.desc(path);
        })
        .toList();
  }

  private String toFieldName(CrawlJobSort.CrawlJobSortField field) {
    return switch (field) {
      case CREATED_AT -> "createdAt";
      case UPDATED_AT -> "updatedAt";
      case STARTED_AT -> "startedAt";
      case COMPLETED_AT -> "completedAt";
      case TARGET_NAME -> "targetName";
      case STATUS -> "status";
      case TYPE -> "crawlType";
      case PERCENT -> "completedItems"; // Will need post-processing or computed column
      case TOTAL_ITEMS -> "totalItems";
      case FAILED_ITEMS -> "failedItems";
    };
  }

  private int resolvePageSize(Integer first, Integer last) {
    var size = first != null ? first : (last != null ? last : DEFAULT_PAGE_SIZE);
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private String encodeCursor(UUID id) {
    return Base64.getEncoder().encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
  }

  private UUID decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      var decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
      return UUID.fromString(decoded);
    } catch (Exception e) {
      log.warn("Failed to decode cursor: {}", cursor, e);
      return null;
    }
  }
}

