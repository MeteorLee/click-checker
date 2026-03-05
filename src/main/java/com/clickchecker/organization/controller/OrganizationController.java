package com.clickchecker.organization.controller;

import com.clickchecker.organization.dto.OrganizationCreateRequest;
import com.clickchecker.organization.service.OrganizationRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationRegistrationService organizationRegistrationService;

    @PostMapping
    public ResponseEntity<CreateResponse> create(@RequestBody @Valid OrganizationCreateRequest request) {
        OrganizationRegistrationService.CreateResult created = organizationRegistrationService.register(request);
        return ResponseEntity.ok(new CreateResponse(created.id(), created.apiKey(), created.apiKeyPrefix()));
    }

    public record CreateResponse(
            Long id,
            String apiKey,
            String apiKeyPrefix
    ) {
    }
}
