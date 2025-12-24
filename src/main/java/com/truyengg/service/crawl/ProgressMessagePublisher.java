package com.truyengg.service.crawl;

import com.truyengg.model.dto.ProgressMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static java.time.ZonedDateTime.now;

@Service
@RequiredArgsConstructor
public class ProgressMessagePublisher {

  private final ApplicationEventPublisher eventPublisher;

  public void publishMessage(UUID crawlId, String message) {
    eventPublisher.publishEvent(new ProgressMessageEvent(crawlId, message, now()));
  }
}

