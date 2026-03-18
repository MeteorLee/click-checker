package com.clickchecker.organization.controller;

import com.clickchecker.organization.controller.request.AdminOrganizationCreateRequest;
import com.clickchecker.organization.controller.response.AdminOrganizationApiKeyMetadataResponse;
import com.clickchecker.organization.controller.response.AdminOrganizationApiKeyRotateResponse;
import com.clickchecker.organization.controller.response.AdminOrganizationCreateResponse;
import com.clickchecker.organization.mapper.AdminOrganizationResponseMapper;
import com.clickchecker.organization.service.AdminOrganizationApiKeyCommandService;
import com.clickchecker.organization.service.AdminOrganizationApiKeyQueryService;
import com.clickchecker.organization.service.AdminOrganizationService;
import com.clickchecker.organization.service.result.AdminOrganizationApiKeyMetadataResult;
import com.clickchecker.organization.service.result.AdminOrganizationApiKeyRotateResult;
import com.clickchecker.organization.service.result.AdminOrganizationCreateResult;
import com.clickchecker.web.resolver.CurrentAccountId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/organizations")
public class AdminOrganizationController {

    private final AdminOrganizationService adminOrganizationService;
    private final AdminOrganizationApiKeyCommandService adminOrganizationApiKeyCommandService;
    private final AdminOrganizationApiKeyQueryService adminOrganizationApiKeyQueryService;
    private final AdminOrganizationResponseMapper adminOrganizationResponseMapper;

    @PostMapping
    public ResponseEntity<AdminOrganizationCreateResponse> create(
            @CurrentAccountId Long accountId,
            @RequestBody @Valid AdminOrganizationCreateRequest request
    ) {
        AdminOrganizationCreateResult result = adminOrganizationService.create(accountId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminOrganizationResponseMapper.toCreateResponse(result));
    }

    @GetMapping("/{organizationId}/api-key")
    public ResponseEntity<AdminOrganizationApiKeyMetadataResponse> getApiKeyMetadata(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId
    ) {
        AdminOrganizationApiKeyMetadataResult result =
                adminOrganizationApiKeyQueryService.getMetadata(accountId, organizationId);
        return ResponseEntity.ok(adminOrganizationResponseMapper.toApiKeyMetadataResponse(result));
    }

    @PostMapping("/{organizationId}/api-key/rotate")
    public ResponseEntity<AdminOrganizationApiKeyRotateResponse> rotateApiKey(
            @CurrentAccountId Long accountId,
            @PathVariable Long organizationId
    ) {
        AdminOrganizationApiKeyRotateResult result =
                adminOrganizationApiKeyCommandService.rotate(accountId, organizationId);
        return ResponseEntity.ok(adminOrganizationResponseMapper.toApiKeyRotateResponse(result));
    }
}
