package com.clickchecker.organization.controller;

import com.clickchecker.organization.controller.request.AdminOrganizationCreateRequest;
import com.clickchecker.organization.controller.response.AdminOrganizationCreateResponse;
import com.clickchecker.organization.mapper.AdminOrganizationResponseMapper;
import com.clickchecker.organization.service.AdminOrganizationService;
import com.clickchecker.organization.service.result.AdminOrganizationCreateResult;
import com.clickchecker.web.resolver.CurrentAccountId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/organizations")
public class AdminOrganizationController {

    private final AdminOrganizationService adminOrganizationService;
    private final AdminOrganizationResponseMapper adminOrganizationResponseMapper;

    @PostMapping
    public ResponseEntity<AdminOrganizationCreateResponse> create(
            @CurrentAccountId Long accountId,
            @RequestBody @Valid AdminOrganizationCreateRequest request
    ) {
        AdminOrganizationCreateResult result = adminOrganizationService.create(accountId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminOrganizationResponseMapper.toCreateResponse(result));
    }
}
