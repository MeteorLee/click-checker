package com.clickchecker.route.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.route.controller.request.RouteTemplateCreateRequest;
import com.clickchecker.route.controller.request.RouteTemplateActiveUpdateRequest;
import com.clickchecker.route.controller.request.RouteTemplateUpdateRequest;
import com.clickchecker.route.controller.response.RouteTemplateItem;
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import com.clickchecker.web.error.ApiErrorMessages;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RouteTemplateService {

    private final RouteTemplateRepository routeTemplateRepository;
    private final OrganizationRepository organizationRepository;
    private final RouteTemplateCacheService routeTemplateCacheService;

    @Transactional
    public RouteTemplate create(Long organizationId, RouteTemplateCreateRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ORGANIZATION_NOT_FOUND));

        RouteTemplate routeTemplate = RouteTemplate.builder()
                .organization(organization)
                .template(request.template())
                .routeKey(request.routeKey())
                .priority(request.priority())
                .active(true)
                .build();

        RouteTemplate saved = routeTemplateRepository.save(routeTemplate);
        routeTemplateCacheService.evictActiveTemplatesAfterCommit(organizationId);
        return saved;
    }

    @Transactional
    public RouteTemplate update(Long organizationId, Long routeTemplateId, RouteTemplateUpdateRequest request) {
        RouteTemplate routeTemplate = routeTemplateRepository.findByIdAndOrganizationId(routeTemplateId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ROUTE_TEMPLATE_NOT_FOUND));

        routeTemplate.update(
                request.template(),
                request.routeKey(),
                request.priority()
        );

        routeTemplateCacheService.evictActiveTemplatesAfterCommit(organizationId);
        return routeTemplate;
    }

    @Transactional
    public RouteTemplate updateActive(Long organizationId, Long routeTemplateId, RouteTemplateActiveUpdateRequest request) {
        RouteTemplate routeTemplate = routeTemplateRepository.findByIdAndOrganizationId(routeTemplateId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ROUTE_TEMPLATE_NOT_FOUND));

        if (Boolean.TRUE.equals(request.active())) {
            routeTemplate.activate();
        } else {
            routeTemplate.deactivate();
        }

        routeTemplateCacheService.evictActiveTemplatesAfterCommit(organizationId);
        return routeTemplate;
    }

    @Transactional
    public void delete(Long organizationId, Long routeTemplateId) {
        RouteTemplate routeTemplate = routeTemplateRepository.findByIdAndOrganizationId(routeTemplateId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ApiErrorMessages.ROUTE_TEMPLATE_NOT_FOUND));

        routeTemplateRepository.delete(routeTemplate);
        routeTemplateCacheService.evictActiveTemplatesAfterCommit(organizationId);
    }

    @Transactional(readOnly = true)
    public List<RouteTemplateItem> getAll(Long organizationId) {
        return routeTemplateRepository.findByOrganizationIdOrderByPriorityDescIdAsc(organizationId).stream()
                .map(routeTemplate -> new RouteTemplateItem(
                        routeTemplate.getId(),
                        routeTemplate.getTemplate(),
                        routeTemplate.getRouteKey(),
                        routeTemplate.getPriority(),
                        routeTemplate.isActive()
                ))
                .toList();
    }
}
