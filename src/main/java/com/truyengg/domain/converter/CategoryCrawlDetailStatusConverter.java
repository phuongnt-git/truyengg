package com.truyengg.domain.converter;

import com.truyengg.domain.enums.CategoryCrawlDetailStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import static com.truyengg.domain.enums.CategoryCrawlDetailStatus.fromString;

@Converter(autoApply = true)
public class CategoryCrawlDetailStatusConverter implements AttributeConverter<CategoryCrawlDetailStatus, String> {

  @Override
  public String convertToDatabaseColumn(CategoryCrawlDetailStatus status) {
    if (status == null) {
      return null;
    }
    return status.name();
  }

  @Override
  public CategoryCrawlDetailStatus convertToEntityAttribute(String value) {
    if (value == null) {
      return null;
    }
    return fromString(value);
  }
}

