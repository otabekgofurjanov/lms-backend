package com.company.lms.auth.mapper;

import com.company.lms.auth.dto.MeResponse;
import com.company.lms.auth.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    @Mapping(target = "roles", expression = "java(toRoles(user))")
    MeResponse toMe(UserEntity user);

    default Set<String> toRoles(UserEntity user) {
        return user.getRoles().stream().map(r -> r.getCode()).collect(Collectors.toSet());
    }
}
