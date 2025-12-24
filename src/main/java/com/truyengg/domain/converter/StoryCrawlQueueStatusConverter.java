package com.truyengg.domain.converter;

import com.truyengg.domain.enums.StoryCrawlQueueStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import static com.truyengg.domain.enums.StoryCrawlQueueStatus.fromString;

@Converter(autoApply = true)
public class StoryCrawlQueueStatusConverter implements AttributeConverter<StoryCrawlQueueStatus, String> {

  @Override
  public String convertToDatabaseColumn(StoryCrawlQueueStatus status) {
    if (status == null) {
      return null;
    }
    return status.name();
  }

  @Override
  public StoryCrawlQueueStatus convertToEntityAttribute(String value) {
    if (value == null) {
      return null;
    }
    return fromString(value);
  }
}

