package com.truyengg.domain.specification;

import com.truyengg.domain.entity.ComicCrawl;
import com.truyengg.domain.enums.ComicCrawlStatus;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZonedDateTime;

@UtilityClass
public class CrawlJobSpecifications {

  public static Specification<ComicCrawl> withFilter(
      ComicCrawlStatus status,
      Long createdBy,
      String search,
      ZonedDateTime fromDate,
      ZonedDateTime toDate,
      Boolean includeDeleted
  ) {
    return (root, query, cb) -> {
      var spec = new BaseSpecification<ComicCrawl>() {
      };
      spec.root = root;
      spec.cb = cb;

      spec.addEqualPredicate("status", status);
      spec.addNestedEqualPredicate("createdBy", "id", createdBy);
      spec.addLikePredicateOr(search, "url", "errorMessage");
      spec.addDateRangePredicate("createdAt", fromDate, toDate);
      spec.addSoftDeletePredicate(includeDeleted);

      return cb.and(spec.toArray());
    };
  }
}

