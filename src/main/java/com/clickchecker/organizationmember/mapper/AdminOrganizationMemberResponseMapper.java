package com.clickchecker.organizationmember.mapper;

import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberListResponse;
import com.clickchecker.organizationmember.controller.response.AdminOrganizationMemberResponse;
import com.clickchecker.organizationmember.service.result.OrganizationMemberResult;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminOrganizationMemberResponseMapper {

    AdminOrganizationMemberResponse toResponse(OrganizationMemberResult result);

    List<AdminOrganizationMemberResponse> toResponses(List<OrganizationMemberResult> results);

    default AdminOrganizationMemberListResponse toListResponse(List<OrganizationMemberResult> results) {
        return new AdminOrganizationMemberListResponse(toResponses(results));
    }
}
