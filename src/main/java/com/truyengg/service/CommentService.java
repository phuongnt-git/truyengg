package com.truyengg.service;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.Comment;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.domain.repository.CommentRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.exception.ResourceNotFoundException;
import com.truyengg.model.mapper.CommentMapper;
import com.truyengg.model.request.CommentRequest;
import com.truyengg.model.response.CommentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

  private final CommentRepository commentRepository;
  private final ComicRepository comicRepository;
  private final UserRepository userRepository;
  private final CommentMapper commentMapper;

  @Transactional(readOnly = true)
  @Cacheable(value = "commentsByComic", key = "#comicId")
  public List<CommentResponse> getCommentsByComicId(Long comicId) {
    var comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    var topLevelComments = commentRepository.findByComicAndParentIsNullOrderByCreatedAtDesc(comic);

    return topLevelComments.stream()
        .map(comment -> {
          var response = commentMapper.toResponse(comment);
          var replies = commentRepository.findByParentOrderByCreatedAtAsc(comment);
          var replyResponses = replies.stream()
              .map(commentMapper::toResponse)
              .toList();
          return new CommentResponse(
              response.id(),
              response.userId(),
              response.username(),
              response.avatar(),
              response.level(),
              response.content(),
              response.parentId(),
              response.likes(),
              response.dislikes(),
              replyResponses,
              response.createdAt()
          );
        })
        .toList();
  }

  @Transactional
  @CacheEvict(value = "commentsByComic", key = "#request.comicId()")
  public CommentResponse createComment(Long userId, CommentRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new AuthenticationServiceException("User not found"));

    Comic comic = comicRepository.findById(request.comicId())
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    Comment parent = null;
    if (request.parentId() != null) {
      parent = commentRepository.findById(request.parentId())
          .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));

      if (!parent.getComic().getId().equals(comic.getId())) {
        throw new ResourceNotFoundException("Parent comment does not belong to this comic");
      }
    }

    Comment comment = Comment.builder()
        .user(user)
        .comic(comic)
        .parent(parent)
        .content(request.content())
        .likes(0)
        .dislikes(0)
        .build();

    comment = commentRepository.save(comment);
    return commentMapper.toResponse(comment);
  }

  @Transactional
  @CacheEvict(value = "commentsByComic", allEntries = true)
  public void deleteComment(Long commentId, Long userId) {
    Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    if (!comment.getUser().getId().equals(userId)) {
      throw new AuthenticationServiceException("You can only delete your own comments");
    }

    commentRepository.delete(comment);
  }

  @Transactional(readOnly = true)
  public long countCommentsByComicId(Long comicId) {
    Comic comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));
    return commentRepository.countTopLevelCommentsByComic(comic);
  }
}
