package com.truyengg.domain.converter;

import com.truyengg.domain.enums.CategoryCrawlJobStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import static com.truyengg.domain.enums.CategoryCrawlJobStatus.fromString;

@Converter(autoApply = true)
public class CategoryCrawlJobStatusConverter implements AttributeConverter<CategoryCrawlJobStatus, String> {

  @Override
  public String convertToDatabaseColumn(CategoryCrawlJobStatus status) {
    if (status == null) {
      return null;
    }
    return status.name();
  }

  @Override
  public CategoryCrawlJobStatus convertToEntityAttribute(String value) {
    if (value == null) {
      return null;
    }
    return fromString(value);
  }
}

