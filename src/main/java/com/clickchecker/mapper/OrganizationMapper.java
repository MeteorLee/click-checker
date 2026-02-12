package com.clickchecker.mapper;

import com.clickchecker.organization.dto.OrganizationCreateRequest;
import com.clickchecker.organization.entity.Organization;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganizationMapper {

    Organization toEntity(OrganizationCreateRequest request);
}
