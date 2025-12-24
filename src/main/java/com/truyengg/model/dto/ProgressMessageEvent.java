package com.truyengg.model.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ProgressMessageEvent(
    UUID crawlId,
    String message,
    ZonedDateTime timestamp) {
}

