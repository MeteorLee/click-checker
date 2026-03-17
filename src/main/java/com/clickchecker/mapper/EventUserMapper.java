package com.clickchecker.mapper;

import com.clickchecker.eventuser.controller.request.EventUserCreateRequest;
import com.clickchecker.eventuser.entity.EventUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventUserMapper {

    @Mapping(target = "organization", ignore = true)
    EventUser toEntity(EventUserCreateRequest request);
}
