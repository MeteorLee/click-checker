package com.clickchecker.mapper;

import com.clickchecker.event.dto.EventCreateRequest;
import com.clickchecker.event.entity.Event;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventMapper {

    Event toEntity(EventCreateRequest req);
}
