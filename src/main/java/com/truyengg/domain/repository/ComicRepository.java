package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Comic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface ComicRepository extends JpaRepository<Comic, Long>, JpaSpecificationExecutor<Comic> {
  Optional<Comic> findBySlug(String slug);

  Optional<Comic> findBySource(String source);

  boolean existsBySlug(String slug);

  boolean existsBySource(String source);

  @Query("SELECT COUNT(c) FROM Comic c WHERE c.isHot = true")
  long countHotComics();

  @Query("SELECT c FROM Comic c ORDER BY c.views DESC")
  java.util.List<Comic> findTopByViews();

  @Query("""
      SELECT c FROM Comic c 
      WHERE c.updatedAt >= :since OR c.createdAt >= :since
      ORDER BY c.views DESC
      """)
  Page<Comic> findTopComicsByViewsSince(ZonedDateTime since, Pageable pageable);

  @Query(value = """
      SELECT c.*, 
        ts_rank(c.content_search_vector, plainto_tsquery('simple', :query)) as rank
      FROM comics c
      WHERE c.content_search_vector @@ plainto_tsquery('simple', :query)
        AND c.status = 'active'
        AND c.merged_comic_id IS NULL
      ORDER BY rank DESC, c.views DESC
      """, nativeQuery = true)
  Page<Comic> searchByFulltext(String query, Pageable pageable);

  @Query(value = """
      SELECT c.*, 
        GREATEST(
          similarity(c.name, :query),
          similarity(c.origin_name, :query),
          similarity(array_to_string(c.alternative_names, ' '), :query)
        ) as similarity_score
      FROM comics c
      WHERE similarity(c.name, :query) > :threshold
         OR word_similarity(:query, c.name) > :threshold
         OR similarity(c.origin_name, :query) > :threshold
         OR word_similarity(:query, c.origin_name) > :threshold
      ORDER BY similarity_score DESC, c.views DESC
      """, nativeQuery = true)
  Page<Comic> searchByFuzzySimilarity(String query, double threshold, Pageable pageable);

  @Modifying
  @Query(value = "REFRESH MATERIALIZED VIEW comics_search_cache", nativeQuery = true)
  void refreshComicsSearchCache();
}
