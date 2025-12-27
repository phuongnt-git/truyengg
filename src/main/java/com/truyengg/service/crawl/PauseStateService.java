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

  public void setPaused(UUID jobId) {
    pauseFlags.put(jobId, true);
  }

  public void clearPaused(UUID jobId) {
    pauseFlags.remove(jobId);
  }

  public Boolean isPaused(UUID jobId) {
    return pauseFlags.get(jobId);
  }

  public void setCancelled(UUID jobId) {
    cancelFlags.put(jobId, true);
  }

  public void clearCancelled(UUID jobId) {
    cancelFlags.remove(jobId);
  }

  public Boolean isCancelled(UUID jobId) {
    return cancelFlags.get(jobId);
  }

  public void remove(UUID jobId) {
    pauseFlags.remove(jobId);
    cancelFlags.remove(jobId);
  }

  public void cleanup() {
    pauseFlags.clear();
    cancelFlags.clear();
  }
}

