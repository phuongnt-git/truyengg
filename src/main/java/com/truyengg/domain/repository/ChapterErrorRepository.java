package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Chapter;
import com.truyengg.domain.entity.ChapterError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterErrorRepository extends JpaRepository<ChapterError, Long>, JpaSpecificationExecutor<ChapterError> {
  List<ChapterError> findByChapterOrderByCreatedAtDesc(Chapter chapter);
}

