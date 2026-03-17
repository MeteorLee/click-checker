package com.clickchecker.route.controller;

import com.clickchecker.route.controller.request.RouteTemplateCreateRequest;
import com.clickchecker.route.controller.request.RouteTemplateActiveUpdateRequest;
import com.clickchecker.route.controller.request.RouteTemplateUpdateRequest;
import com.clickchecker.route.controller.response.RouteTemplateListResponse;
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.service.RouteTemplateService;
import com.clickchecker.web.resolver.CurrentOrganizationId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/route-templates")
@RequiredArgsConstructor
public class RouteTemplateController {

    private final RouteTemplateService routeTemplateService;

    @GetMapping
    public RouteTemplateListResponse getAll(@CurrentOrganizationId Long authOrgId) {
        return new RouteTemplateListResponse(routeTemplateService.getAll(authOrgId));
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(
            @CurrentOrganizationId Long authOrgId,
            @RequestBody @Valid RouteTemplateCreateRequest request
    ) {
        RouteTemplate routeTemplate = routeTemplateService.create(authOrgId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(
                        routeTemplate.getId(),
                        routeTemplate.getTemplate(),
                        routeTemplate.getRouteKey(),
                        routeTemplate.getPriority(),
                        routeTemplate.isActive()
                ));
    }

    @PutMapping("/{routeTemplateId}")
    public CreateResponse update(
            @CurrentOrganizationId Long authOrgId,
            @PathVariable Long routeTemplateId,
            @RequestBody @Valid RouteTemplateUpdateRequest request
    ) {
        RouteTemplate routeTemplate = routeTemplateService.update(authOrgId, routeTemplateId, request);

        return new CreateResponse(
                routeTemplate.getId(),
                routeTemplate.getTemplate(),
                routeTemplate.getRouteKey(),
                routeTemplate.getPriority(),
                routeTemplate.isActive()
        );
    }

    @PutMapping("/{routeTemplateId}/active")
    public CreateResponse updateActive(
            @CurrentOrganizationId Long authOrgId,
            @PathVariable Long routeTemplateId,
            @RequestBody @Valid RouteTemplateActiveUpdateRequest request
    ) {
        RouteTemplate routeTemplate = routeTemplateService.updateActive(authOrgId, routeTemplateId, request);

        return new CreateResponse(
                routeTemplate.getId(),
                routeTemplate.getTemplate(),
                routeTemplate.getRouteKey(),
                routeTemplate.getPriority(),
                routeTemplate.isActive()
        );
    }

    @DeleteMapping("/{routeTemplateId}")
    public ResponseEntity<Void> delete(
            @CurrentOrganizationId Long authOrgId,
            @PathVariable Long routeTemplateId
    ) {
        routeTemplateService.delete(authOrgId, routeTemplateId);
        return ResponseEntity.noContent().build();
    }

    public record CreateResponse(
            Long id,
            String template,
            String routeKey,
            int priority,
            boolean active
    ) {
    }
}
