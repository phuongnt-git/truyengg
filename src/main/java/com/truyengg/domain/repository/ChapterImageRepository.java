package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.ChapterImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterImageRepository extends JpaRepository<ChapterImage, Long>, JpaSpecificationExecutor<ChapterImage> {

  List<ChapterImage> findByChapter(Chapter chapter);

  List<ChapterImage> findByChapterIdOrderByImageOrderAsc(Long chapterId);

  @Query("""
      SELECT ci FROM ChapterImage ci 
      WHERE ci.chapter = :chapter 
        AND ci.isVisible = true 
        AND ci.deletedAt IS NULL 
      ORDER BY COALESCE(ci.manualOrder, ci.imageOrder) ASC
      """)
  List<ChapterImage> findVisibleImagesByChapter(Chapter chapter);

  @Query("SELECT COUNT(ci) FROM ChapterImage ci WHERE ci.chapter = :chapter AND ci.deletedAt IS NULL")
  long countByChapter(Chapter chapter);

  @Query("SELECT COUNT(ci) FROM ChapterImage ci WHERE ci.chapter = :chapter AND ci.isDownloaded = true AND ci.deletedAt IS NULL")
  long countDownloadedImagesByChapter(Chapter chapter);
}

