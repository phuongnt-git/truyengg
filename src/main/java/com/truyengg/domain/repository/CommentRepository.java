package com.truyengg.domain.repository;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {
  List<Comment> findByComicAndParentIsNullOrderByCreatedAtDesc(Comic comic);

  List<Comment> findByParentOrderByCreatedAtAsc(Comment parent);

  @Query("SELECT COUNT(c) FROM Comment c WHERE c.comic = :comic AND c.parent IS NULL")
  long countTopLevelCommentsByComic(Comic comic);
}

