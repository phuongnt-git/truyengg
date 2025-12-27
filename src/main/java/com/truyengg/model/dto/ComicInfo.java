package com.truyengg.model.dto;

import com.truyengg.domain.enums.AgeRating;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.enums.Gender;

import java.time.ZonedDateTime;
import java.util.List;

public record ComicInfo(
    String name,
    String slug,
    String originName,
    String content,
    ComicStatus status,
    ComicProgressStatus progressStatus,
    String thumbUrl,
    String author,
    Long likes,
    Long follows,
    Integer totalChapters,
    ZonedDateTime lastChapterUpdatedAt,
    String source,
    List<String> alternativeNames,
    AgeRating ageRating,
    Gender gender,
    String country
) {
}

