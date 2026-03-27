package com.clickchecker.route.controller;

import com.clickchecker.route.controller.request.RouteTemplateActiveUpdateRequest;
import com.clickchecker.route.controller.request.RouteTemplateCreateRequest;
import com.clickchecker.route.controller.request.RouteTemplateUpdateRequest;
import com.clickchecker.route.controller.response.RouteTemplateItem;
import com.clickchecker.route.controller.response.RouteTemplateListResponse;
import com.clickchecker.route.service.AdminRouteTemplateService;
import com.clickchecker.security.principal.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/organizations/{organizationId}/route-templates")
@RequiredArgsConstructor
public class AdminRouteTemplateController {

    private final AdminRouteTemplateService adminRouteTemplateService;

    @GetMapping
    public RouteTemplateListResponse getAll(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId
    ) {
        return new RouteTemplateListResponse(
                adminRouteTemplateService.getAll(principal.accountId(), organizationId)
        );
    }

    @PostMapping
    public ResponseEntity<RouteTemplateItem> create(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @RequestBody @Valid RouteTemplateCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminRouteTemplateService.create(principal.accountId(), organizationId, request));
    }

    @PutMapping("/{routeTemplateId}")
    public RouteTemplateItem update(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long routeTemplateId,
            @RequestBody @Valid RouteTemplateUpdateRequest request
    ) {
        return adminRouteTemplateService.update(
                principal.accountId(),
                organizationId,
                routeTemplateId,
                request
        );
    }

    @PutMapping("/{routeTemplateId}/active")
    public RouteTemplateItem updateActive(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long routeTemplateId,
            @RequestBody @Valid RouteTemplateActiveUpdateRequest request
    ) {
        return adminRouteTemplateService.updateActive(
                principal.accountId(),
                organizationId,
                routeTemplateId,
                request
        );
    }

    @DeleteMapping("/{routeTemplateId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AdminPrincipal principal,
            @PathVariable Long organizationId,
            @PathVariable Long routeTemplateId
    ) {
        adminRouteTemplateService.delete(principal.accountId(), organizationId, routeTemplateId);
        return ResponseEntity.noContent().build();
    }
}
