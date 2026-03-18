package com.clickchecker.organization.mapper;

import com.clickchecker.organization.controller.response.AdminOrganizationApiKeyMetadataResponse;
import com.clickchecker.organization.controller.response.AdminOrganizationApiKeyRotateResponse;
import com.clickchecker.organization.controller.response.AdminOrganizationCreateResponse;
import com.clickchecker.organization.service.result.AdminOrganizationApiKeyMetadataResult;
import com.clickchecker.organization.service.result.AdminOrganizationApiKeyRotateResult;
import com.clickchecker.organization.service.result.AdminOrganizationCreateResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminOrganizationResponseMapper {

    AdminOrganizationCreateResponse toCreateResponse(AdminOrganizationCreateResult result);

    AdminOrganizationApiKeyMetadataResponse toApiKeyMetadataResponse(AdminOrganizationApiKeyMetadataResult result);

    AdminOrganizationApiKeyRotateResponse toApiKeyRotateResponse(AdminOrganizationApiKeyRotateResult result);
}
