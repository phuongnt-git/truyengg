package com.truyengg.model.response;

import com.truyengg.domain.entity.Comic;
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

  public static ComicResponse from(Comic comic) {
    return new ComicResponse(
        comic.getId(),
        comic.getName(),
        comic.getSlug(),
        comic.getOriginName(),
        comic.getContent(),
        comic.getStatus(),
        comic.getProgressStatus(),
        comic.getThumbUrl(),
        comic.getViews(),
        comic.getAuthor(),
        comic.getIsHot(),
        comic.getLikes(),
        comic.getFollows(),
        null,
        null,
        comic.getTotalChapters(),
        comic.getLastChapterUpdatedAt(),
        comic.getSource(),
        comic.getAlternativeNames(),
        comic.getAgeRating(),
        comic.getGender(),
        comic.getCountry(),
        comic.getCreatedAt(),
        comic.getUpdatedAt()
    );
  }

  public static ComicResponse from(Comic comic, Long followCount, Long chapterCount) {
    return new ComicResponse(
        comic.getId(),
        comic.getName(),
        comic.getSlug(),
        comic.getOriginName(),
        comic.getContent(),
        comic.getStatus(),
        comic.getProgressStatus(),
        comic.getThumbUrl(),
        comic.getViews(),
        comic.getAuthor(),
        comic.getIsHot(),
        comic.getLikes(),
        comic.getFollows(),
        followCount,
        chapterCount,
        comic.getTotalChapters(),
        comic.getLastChapterUpdatedAt(),
        comic.getSource(),
        comic.getAlternativeNames(),
        comic.getAgeRating(),
        comic.getGender(),
        comic.getCountry(),
        comic.getCreatedAt(),
        comic.getUpdatedAt()
    );
  }
}
