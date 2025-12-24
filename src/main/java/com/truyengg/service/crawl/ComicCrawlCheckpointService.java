package com.truyengg.service.crawl;

import com.truyengg.domain.entity.ComicCrawlCheckpoint;
import com.truyengg.domain.repository.ComicCrawlCheckpointRepository;
import com.truyengg.domain.repository.ComicCrawlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicCrawlCheckpointService {

  private final ComicCrawlCheckpointRepository checkpointRepository;
  private final ComicCrawlRepository comicCrawlRepository;

  @Transactional
  public void saveCheckpoint(UUID crawlId, ComicCrawlCheckpoint checkpoint) {
    var crawl = comicCrawlRepository.findById(crawlId)
        .orElseThrow(() -> new IllegalArgumentException("Crawl not found: " + crawlId));

    var existingOpt = checkpointRepository.findByCrawlId(crawlId);
    if (existingOpt.isPresent()) {
      var existing = existingOpt.get();
      existing.setCurrentChapterIndex(checkpoint.getCurrentChapterIndex());
      existing.setCurrentImageIndex(checkpoint.getCurrentImageIndex());
      existing.setCurrentImageUrl(checkpoint.getCurrentImageUrl());
      existing.setImageUrls(checkpoint.getImageUrls());
      existing.setCrawledChapters(checkpoint.getCrawledChapters());
      existing.setChapterProgress(checkpoint.getChapterProgress());
      checkpointRepository.save(existing);
    } else {
      checkpoint.setCrawl(crawl);
      checkpointRepository.save(checkpoint);
    }
  }

  @Transactional(readOnly = true)
  public Optional<ComicCrawlCheckpoint> getCheckpoint(UUID crawlId) {
    return checkpointRepository.findByCrawlId(crawlId);
  }

  @Transactional
  public void deleteCheckpoint(UUID crawlId) {
    checkpointRepository.deleteByCrawlId(crawlId);
  }
}

