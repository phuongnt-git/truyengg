package com.truyengg.controller.websocket;

import com.truyengg.model.dto.CrawlProgressDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketProgressService {

  SimpMessagingTemplate messagingTemplate;

  /**
   * Send progress update for a crawl job.
   */
  public void sendProgress(CrawlProgressDto progress) {
    if (isEmpty(progress)) {
      return;
    }

    var jobId = progress.jobId();
    if (isEmpty(jobId)) {
      return;
    }

    try {
      var destination = "/topic/crawl-progress/" + jobId;
      messagingTemplate.convertAndSend(destination, progress);
    } catch (Exception e) {
      log.warn("Error sending WebSocket progress update for job: {}", jobId);
    }
  }
}
