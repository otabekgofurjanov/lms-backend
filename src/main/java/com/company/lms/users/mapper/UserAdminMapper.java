package com.company.lms.users.mapper;

import com.company.lms.users.dto.PermissionResponse;
import com.company.lms.users.dto.RoleResponse;
import com.company.lms.users.dto.UserResponse;
import com.company.lms.users.entity.PermissionAdminEntity;
import com.company.lms.users.entity.RoleAdminEntity;
import com.company.lms.users.entity.UserAdminEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserAdminMapper {
    @Mapping(target = "roles", expression = "java(toRoles(user))")
    UserResponse toResponse(UserAdminEntity user);

    RoleResponse toResponse(RoleAdminEntity role);

    PermissionResponse toResponse(PermissionAdminEntity permission);

    default Set<String> toRoles(UserAdminEntity user) {
        return user.getRoles().stream().map(RoleAdminEntity::getCode).collect(Collectors.toSet());
    }
}
