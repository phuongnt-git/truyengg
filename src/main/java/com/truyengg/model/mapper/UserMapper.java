package com.truyengg.model.mapper;

import com.truyengg.domain.entity.User;
import com.truyengg.model.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {
  UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

  @Mapping(target = "roles", source = "roles")
  UserResponse toResponse(User user);
}

