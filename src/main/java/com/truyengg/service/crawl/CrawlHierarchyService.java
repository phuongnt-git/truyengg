package com.truyengg.service.crawl;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.UUID.fromString;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Service for crawl job hierarchy operations.
 * Uses Apache AGE for graph queries when available, falls back to recursive CTE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CrawlHierarchyService {

  NamedParameterJdbcTemplate jdbcTemplate;

  @NonFinal
  @Value("${crawl.age.enabled:false}")
  boolean ageEnabled;

  /**
   * -- GETTER --
   * Check if Apache AGE is available.
   */
  @NonFinal
  @Getter
  boolean ageAvailable = false;

  @PostConstruct
  void checkAgeAvailability() {
    if (!ageEnabled) {
      log.info("Apache AGE is disabled by configuration - using recursive CTE");
      return;
    }

    try {
      jdbcTemplate.getJdbcTemplate().execute("SELECT * FROM ag_catalog.ag_graph LIMIT 1");
      ageAvailable = true;
      log.info("Apache AGE is available - using graph queries");
    } catch (Exception e) {
      ageAvailable = false;
      log.info("Apache AGE not available - falling back to recursive CTE");
    }
  }

  /**
   * Find all descendants of a job (any depth).
   */
  public List<UUID> findAllDescendants(UUID jobId) {
    if (jobId == null) {
      return emptyList();
    }

    if (isAgeAvailable()) {
      return findDescendantsWithAge(jobId);
    }
    return findDescendantsWithRecursiveCTE(jobId);
  }

  /**
   * Find all ancestors of a job (path to root).
   */
  public List<UUID> findAllAncestors(UUID jobId) {
    if (jobId == null) {
      return emptyList();
    }

    if (isAgeAvailable()) {
      return findAncestorsWithAge(jobId);
    }
    return findAncestorsWithRecursiveCTE(jobId);
  }

  /**
   * Find direct children of a job.
   */
  public List<UUID> findDirectChildren(UUID parentId) {
    if (parentId == null) {
      return emptyList();
    }

    var sql = """
        SELECT id FROM crawl_jobs
        WHERE parent_job_id = :parentId AND deleted_at IS NULL
        ORDER BY item_index
        """;

    return jdbcTemplate.queryForList(sql, singletonMap("parentId", parentId), UUID.class);
  }

  /**
   * Get max depth of hierarchy from a job.
   */
  public int getMaxDepth(UUID rootJobId) {
    if (rootJobId == null) {
      return 0;
    }

    var sql = """
        WITH RECURSIVE tree AS (
            SELECT id, 0 as level FROM crawl_jobs WHERE id = :jobId
            UNION ALL
            SELECT j.id, t.level + 1 FROM crawl_jobs j
            INNER JOIN tree t ON j.parent_job_id = t.id
            WHERE j.deleted_at IS NULL
        )
        SELECT COALESCE(MAX(level), 0) FROM tree
        """;

    var depth = jdbcTemplate.queryForObject(sql, singletonMap("jobId", rootJobId), Integer.class);
    return depth != null ? depth : 0;
  }

  /**
   * Count total descendants of a job.
   */
  public long countDescendants(UUID jobId) {
    if (jobId == null) {
      return 0;
    }

    var sql = """
        WITH RECURSIVE descendants AS (
            SELECT id FROM crawl_jobs WHERE parent_job_id = :jobId AND deleted_at IS NULL
            UNION ALL
            SELECT j.id FROM crawl_jobs j
            INNER JOIN descendants d ON j.parent_job_id = d.id
            WHERE j.deleted_at IS NULL
        )
        SELECT COUNT(*) FROM descendants
        """;

    var count = jdbcTemplate.queryForObject(sql, singletonMap("jobId", jobId), Long.class);
    return count != null ? count : 0;
  }

  /**
   * Find root job for a given job.
   */
  public UUID findRootJob(UUID jobId) {
    if (jobId == null) {
      return null;
    }

    var sql = """
        WITH RECURSIVE ancestors AS (
            SELECT id, parent_job_id, root_job_id FROM crawl_jobs WHERE id = :jobId
            UNION ALL
            SELECT j.id, j.parent_job_id, j.root_job_id FROM crawl_jobs j
            INNER JOIN ancestors a ON j.id = a.parent_job_id
            WHERE j.deleted_at IS NULL
        )
        SELECT id FROM ancestors WHERE parent_job_id IS NULL
        """;

    var roots = jdbcTemplate.queryForList(sql, singletonMap("jobId", jobId), UUID.class);
    return roots.isEmpty() ? jobId : roots.getFirst();
  }

  // ===== Private methods for recursive CTE fallback =====

  private List<UUID> findDescendantsWithRecursiveCTE(UUID jobId) {
    String sql = """
        WITH RECURSIVE descendants AS (
            SELECT id FROM crawl_jobs WHERE parent_job_id = :jobId AND deleted_at IS NULL
            UNION ALL
            SELECT j.id FROM crawl_jobs j
            INNER JOIN descendants d ON j.parent_job_id = d.id
            WHERE j.deleted_at IS NULL
        )
        SELECT id FROM descendants
        """;

    return jdbcTemplate.queryForList(sql, singletonMap("jobId", jobId), UUID.class);
  }

  private List<UUID> findAncestorsWithRecursiveCTE(UUID jobId) {
    var sql = """
        WITH RECURSIVE ancestors AS (
            SELECT id, parent_job_id FROM crawl_jobs WHERE id = :jobId
            UNION ALL
            SELECT j.id, j.parent_job_id FROM crawl_jobs j
            INNER JOIN ancestors a ON j.id = a.parent_job_id
            WHERE j.deleted_at IS NULL
        )
        SELECT id FROM ancestors WHERE id != :jobId
        """;

    return jdbcTemplate.queryForList(sql, singletonMap("jobId", jobId), UUID.class);
  }

  // ===== Private methods for Apache AGE =====

  private List<UUID> findDescendantsWithAge(UUID jobId) {
    try {
      var cypher = String.format("""
          SELECT * FROM cypher('crawl_graph', $$
              MATCH (root:CrawlJob {job_id: '%s'})-[:PARENT_OF*]->(descendant)
              RETURN descendant.job_id
          $$) AS (job_id agtype)
          """, jobId.toString());

      return jdbcTemplate.getJdbcTemplate().query(cypher, (rs, rowNum) -> {
        var jobIdStr = rs.getString("job_id");
        // AGE returns strings with quotes, need to remove them
        return fromString(jobIdStr.replace("\"", ""));
      });
    } catch (Exception e) {
      log.warn("AGE query failed, falling back to CTE: {}", e.getMessage());
      return findDescendantsWithRecursiveCTE(jobId);
    }
  }

  private List<UUID> findAncestorsWithAge(UUID jobId) {
    try {
      var cypher = String.format("""
          SELECT * FROM cypher('crawl_graph', $$
              MATCH (ancestor)-[:PARENT_OF*]->(job:CrawlJob {job_id: '%s'})
              RETURN ancestor.job_id
          $$) AS (job_id agtype)
          """, jobId.toString());

      return jdbcTemplate.getJdbcTemplate().query(cypher, (rs, rowNum) -> {
        var jobIdStr = rs.getString("job_id");
        return fromString(jobIdStr.replace("\"", ""));
      });
    } catch (Exception e) {
      log.warn("AGE query failed, falling back to CTE: {}", e.getMessage());
      return findAncestorsWithRecursiveCTE(jobId);
    }
  }

  // ===== Graph node management (only when AGE is available) =====

  /**
   * Create a node in the graph for a crawl job.
   * Only called when AGE is available.
   */
  public void createGraphNode(UUID jobId, String crawlType, String targetUrl, int depth) {
    if (!isAgeAvailable()) return;

    try {
      var cypher = String.format("""
          SELECT * FROM cypher('crawl_graph', $$
              CREATE (j:CrawlJob {
                  job_id: '%s',
                  crawl_type: '%s',
                  target_url: '%s',
                  depth: %d
              })
              RETURN j
          $$) AS (job agtype)
          """, jobId.toString(), crawlType, escapeForCypher(targetUrl), depth);

      jdbcTemplate.getJdbcTemplate().execute(cypher);
    } catch (Exception e) {
      log.warn("Failed to create AGE node: {}", e.getMessage());
    }
  }

  /**
   * Create parent-child edge in the graph.
   * Only called when AGE is available.
   */
  public void createParentChildEdge(UUID parentId, UUID childId) {
    if (!isAgeAvailable() || parentId == null || childId == null) return;

    try {
      var cypher = String.format("""
          SELECT * FROM cypher('crawl_graph', $$
              MATCH (parent:CrawlJob {job_id: '%s'})
              MATCH (child:CrawlJob {job_id: '%s'})
              CREATE (parent)-[:PARENT_OF]->(child)
          $$) AS (result agtype)
          """, parentId, childId);

      jdbcTemplate.getJdbcTemplate().execute(cypher);
    } catch (Exception e) {
      log.warn("Failed to create AGE edge: {}", e.getMessage());
    }
  }

  /**
   * Delete node and all descendants from the graph.
   * Only called when AGE is available.
   */
  public void deleteNodeAndDescendants(UUID jobId) {
    if (!isAgeAvailable() || jobId == null) return;

    try {
      var cypher = String.format("""
          SELECT * FROM cypher('crawl_graph', $$
              MATCH (root:CrawlJob {job_id: '%s'})-[:PARENT_OF*0..]->(descendant)
              DETACH DELETE descendant
          $$) AS (result agtype)
          """, jobId);

      jdbcTemplate.getJdbcTemplate().execute(cypher);
    } catch (Exception e) {
      log.warn("Failed to delete AGE nodes: {}", e.getMessage());
    }
  }

  private String escapeForCypher(String value) {
    if (isBlank(value)) return EMPTY;
    return value.replace("'", "\\'").replace("\\", "\\\\");
  }
}

