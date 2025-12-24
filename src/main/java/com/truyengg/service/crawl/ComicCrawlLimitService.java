package com.truyengg.service.crawl;

import com.truyengg.config.CrawlJobLimitProperties;
import com.truyengg.domain.repository.ComicCrawlRepository;
import com.truyengg.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.truyengg.domain.enums.ComicCrawlStatus.RUNNING;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicCrawlLimitService {

  private final ComicCrawlRepository comicCrawlRepository;
  private final UserRepository userRepository;
  private final CrawlJobLimitProperties limitProperties;

  public boolean canStartCrawl(Long userId) {
    return checkUserLimit(userId) && checkServerLimit();
  }

  public boolean checkUserLimit(Long userId) {
    var user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    var runningCrawls = comicCrawlRepository.countByStatusAndCreatedByAndDeletedAtIsNull(RUNNING, user.getId());
    var limit = limitProperties.getLimit().getPerAdmin();
    return runningCrawls < limit;
  }

  public boolean checkServerLimit() {
    var runningCrawls = comicCrawlRepository.countByStatusAndDeletedAtIsNull(RUNNING);
    var limit = limitProperties.getLimit().getPerServer();
    return runningCrawls < limit;
  }
}

