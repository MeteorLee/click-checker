package com.clickchecker.organization.mapper;

import com.clickchecker.organization.controller.response.AdminOrganizationCreateResponse;
import com.clickchecker.organization.service.result.AdminOrganizationCreateResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminOrganizationResponseMapper {

    AdminOrganizationCreateResponse toCreateResponse(AdminOrganizationCreateResult result);
}
