package com.clickchecker.auth.mapper;

import com.clickchecker.auth.controller.response.AdminLoginResponse;
import com.clickchecker.auth.controller.response.AdminRefreshResponse;
import com.clickchecker.auth.service.result.AdminTokenResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminAuthResponseMapper {

    AdminLoginResponse toLoginResponse(AdminTokenResult tokenResult);

    AdminRefreshResponse toRefreshResponse(AdminTokenResult tokenResult);
}
