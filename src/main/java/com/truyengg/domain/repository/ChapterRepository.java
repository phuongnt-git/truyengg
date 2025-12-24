package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.Comic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Long>, JpaSpecificationExecutor<Chapter> {
  List<Chapter> findByComicOrderByCreatedAtAsc(Comic comic);

  List<Chapter> findByComic(Comic comic);

  Optional<Chapter> findByComicAndChapterName(Comic comic, String chapterName);

  Optional<Chapter> findBySource(String source);

  boolean existsByComicAndChapterName(Comic comic, String chapterName);

  @Query("SELECT COUNT(c) FROM Chapter c WHERE c.comic = :comic AND c.deletedAt IS NULL")
  long countByComic(Comic comic);

  @Query("SELECT c FROM Chapter c WHERE c.comic = :comic AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
  List<Chapter> findActiveChaptersByComic(Comic comic);
}

