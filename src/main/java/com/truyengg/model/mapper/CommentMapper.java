package com.truyengg.model.mapper;

import com.truyengg.domain.entity.Comment;
import com.truyengg.model.response.CommentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {
  CommentMapper INSTANCE = Mappers.getMapper(CommentMapper.class);

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "avatar", source = "user.avatar")
  @Mapping(target = "level", source = "user.level")
  @Mapping(target = "parentId", source = "parent.id")
  @Mapping(target = "replies", ignore = true)
  CommentResponse toResponse(Comment comment);

  List<CommentResponse> toResponseList(List<Comment> comments);
}

