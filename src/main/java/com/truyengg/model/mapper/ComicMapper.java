package com.truyengg.model.mapper;

import com.truyengg.domain.entity.Comic;
import com.truyengg.model.response.ComicResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ComicMapper {
  ComicMapper INSTANCE = Mappers.getMapper(ComicMapper.class);

  @Mapping(target = "followCount", ignore = true)
  @Mapping(target = "chapterCount", ignore = true)
  ComicResponse toResponse(Comic comic);
}

