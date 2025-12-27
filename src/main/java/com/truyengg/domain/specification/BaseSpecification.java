package com.truyengg.domain.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;

public abstract class BaseSpecification<T> {

  protected final List<Predicate> predicates = new ArrayList<>();
  protected CriteriaBuilder cb;
  protected Root<T> root;

  protected void addEqualPredicate(String field, Object value) {
    if (value != null) {
      predicates.add(cb.equal(root.get(field), value));
    }
  }

  protected void addLikePredicate(String field, String value) {
    if (hasText(value)) {
      predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
    }
  }

  protected void addLikePredicateOr(String value, String... fields) {
    if (hasText(value)) {
      var searchPattern = "%" + value.toLowerCase() + "%";
      var orPredicates = new Predicate[fields.length];
      for (int i = 0; i < fields.length; i++) {
        orPredicates[i] = cb.like(cb.lower(root.get(fields[i])), searchPattern);
      }
      predicates.add(cb.or(orPredicates));
    }
  }

  protected void addDateRangePredicate(String field, ZonedDateTime fromDate, ZonedDateTime toDate) {
    if (fromDate != null) {
      predicates.add(cb.greaterThanOrEqualTo(root.get(field), fromDate));
    }
    if (toDate != null) {
      predicates.add(cb.lessThanOrEqualTo(root.get(field), toDate));
    }
  }

  protected void addSoftDeletePredicate(Boolean includeDeleted) {
    if (includeDeleted == null || !includeDeleted) {
      predicates.add(cb.isNull(root.get("deletedAt")));
    }
  }

  protected void addNestedEqualPredicate(String parentField, String childField, Object value) {
    if (value != null) {
      predicates.add(cb.equal(root.get(parentField).get(childField), value));
    }
  }

  protected Predicate[] toArray() {
    return predicates.toArray(new Predicate[0]);
  }
}
