package com.truyengg.service;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.entity.UserFollow;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.domain.repository.UserFollowRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.exception.ResourceNotFoundException;
import com.truyengg.model.mapper.ComicMapper;
import com.truyengg.model.response.ComicResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

  private final UserFollowRepository userFollowRepository;
  private final UserRepository userRepository;
  private final ComicRepository comicRepository;
  private final ComicMapper comicMapper;

  @Transactional
  @CacheEvict(value = {"comicBySlug", "followedComics"}, allEntries = true)
  public boolean toggleFollow(Long userId, Long comicId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Comic comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    return userFollowRepository.findByUserAndComic(user, comic)
        .map(follow -> {
          userFollowRepository.delete(follow);
          return false;
        })
        .orElseGet(() -> {
          UserFollow follow = UserFollow.builder()
              .user(user)
              .comic(comic)
              .build();
          userFollowRepository.save(follow);
          return true;
        });
  }

  @Transactional(readOnly = true)
  public boolean isFollowing(Long userId, Long comicId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Comic comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    return userFollowRepository.existsByUserAndComic(user, comic);
  }

  @Transactional(readOnly = true)
  public long getFollowCount(Long comicId) {
    Comic comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));
    return userFollowRepository.countByComic(comic);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "followedComics", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
  public Page<ComicResponse> getFollowedComics(Long userId, Pageable pageable) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    List<UserFollow> follows = userFollowRepository.findByUserOrderByCreatedAtDesc(user);
    List<ComicResponse> comics = follows.stream()
        .map(follow -> comicMapper.toResponse(follow.getComic()))
        .skip(pageable.getOffset())
        .limit(pageable.getPageSize())
        .toList();

    return new org.springframework.data.domain.PageImpl<>(comics, pageable, follows.size());
  }
}
