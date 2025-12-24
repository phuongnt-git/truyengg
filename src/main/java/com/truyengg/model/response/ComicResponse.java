package com.truyengg.model.response;

import com.truyengg.domain.enums.AgeRating;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.enums.Gender;

import java.time.ZonedDateTime;
import java.util.List;

public record ComicResponse(
    Long id,
    String name,
    String slug,
    String originName,
    String content,
    ComicStatus status,
    ComicProgressStatus progressStatus,
    String thumbUrl,
    Long views,
    String author,
    Boolean isHot,
    Long likes,
    Long follows,
    Long followCount,
    Long chapterCount,
    Integer totalChapters,
    ZonedDateTime lastChapterUpdatedAt,
    String source,
    List<String> alternativeNames,
    AgeRating ageRating,
    Gender gender,
    String country,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
}

