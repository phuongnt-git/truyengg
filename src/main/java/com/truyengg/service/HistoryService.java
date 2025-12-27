package com.truyengg.service;

import com.truyengg.domain.entity.Comic;
import com.truyengg.domain.entity.ReadingHistory;
import com.truyengg.domain.entity.User;
import com.truyengg.domain.repository.ComicRepository;
import com.truyengg.domain.repository.ReadingHistoryRepository;
import com.truyengg.domain.repository.UserRepository;
import com.truyengg.domain.exception.ResourceNotFoundException;
import com.truyengg.model.response.ComicResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoryService {

  private final ReadingHistoryRepository historyRepository;
  private final UserRepository userRepository;
  private final ComicRepository comicRepository;

  @Transactional
  @CacheEvict(value = "readingHistory", allEntries = true)
  public void saveReadingHistory(Long userId, Long comicId, Long chapterId, String chapterName) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Comic comic = comicRepository.findById(comicId)
        .orElseThrow(() -> new ResourceNotFoundException("Comic not found"));

    ReadingHistory history = historyRepository.findByUserAndComic(user, comic)
        .orElse(ReadingHistory.builder()
            .user(user)
            .comic(comic)
            .build());

    history.setChapterName(chapterName);
    history.setSlug(comic.getSlug());
    history.setName(comic.getName());
    history.setThumbUrl(comic.getThumbUrl());

    if (chapterId != null) {
      // Set chapter if needed
    }

    historyRepository.save(history);
  }

  @Transactional(readOnly = true)
  public Page<ComicResponse> getReadingHistory(Long userId, Pageable pageable) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    List<ReadingHistory> histories = historyRepository.findByUserOrderByLastReadAtDesc(user);
    List<ComicResponse> comics = histories.stream()
        .map(history -> ComicResponse.from(history.getComic()))
        .skip(pageable.getOffset())
        .limit(pageable.getPageSize())
        .toList();

    return new org.springframework.data.domain.PageImpl<>(comics, pageable, histories.size());
  }

  @Transactional
  @CacheEvict(value = "readingHistory", allEntries = true)
  public void clearHistory(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    List<ReadingHistory> histories = historyRepository.findByUserOrderByLastReadAtDesc(user);
    historyRepository.deleteAll(histories);
  }
}
