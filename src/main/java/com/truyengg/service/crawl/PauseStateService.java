package com.truyengg.service.crawl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PauseStateService {

  private final Map<UUID, Boolean> pauseFlags = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> cancelFlags = new ConcurrentHashMap<>();

  public void setPaused(UUID crawlId) {
    pauseFlags.put(crawlId, true);
  }

  public void clearPaused(UUID crawlId) {
    pauseFlags.remove(crawlId);
  }

  public Boolean isPaused(UUID crawlId) {
    return pauseFlags.get(crawlId);
  }

  public void setCancelled(UUID crawlId) {
    cancelFlags.put(crawlId, true);
  }

  public void clearCancelled(UUID crawlId) {
    cancelFlags.remove(crawlId);
  }

  public Boolean isCancelled(UUID crawlId) {
    return cancelFlags.get(crawlId);
  }

  public void remove(UUID crawlId) {
    pauseFlags.remove(crawlId);
    cancelFlags.remove(crawlId);
  }

  /**
   * Cleanup flags for completed/failed crawls.
   * This method is now called by JobRunr instead of @Scheduled
   */
  public void cleanup() {
    var pauseSize = pauseFlags.size();
    var cancelSize = cancelFlags.size();
    if (pauseSize > 0 || cancelSize > 0) {
      log.debug("Crawl state flags cleanup: {} pause flags, {} cancel flags in memory", pauseSize, cancelSize);
    }
  }
}

