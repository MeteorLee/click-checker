package com.clickchecker.route.service;

import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.route.controller.request.RouteTemplateCreateRequest;
import com.clickchecker.route.controller.request.RouteTemplateActiveUpdateRequest;
import com.clickchecker.route.controller.request.RouteTemplateUpdateRequest;
import com.clickchecker.route.controller.response.RouteTemplateItem;
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteTemplateService {

    private final RouteTemplateRepository routeTemplateRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional
    public RouteTemplate create(Long organizationId, RouteTemplateCreateRequest request) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new EntityNotFoundException("Organization not found: " + organizationId));

        RouteTemplate routeTemplate = RouteTemplate.builder()
                .organization(organization)
                .template(request.template())
                .routeKey(request.routeKey())
                .priority(request.priority())
                .active(true)
                .build();

        return routeTemplateRepository.save(routeTemplate);
    }

    @Transactional
    public RouteTemplate update(Long organizationId, Long routeTemplateId, RouteTemplateUpdateRequest request) {
        RouteTemplate routeTemplate = routeTemplateRepository.findByIdAndOrganizationId(routeTemplateId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("RouteTemplate not found: " + routeTemplateId));

        routeTemplate.update(
                request.template(),
                request.routeKey(),
                request.priority()
        );

        return routeTemplate;
    }

    @Transactional
    public RouteTemplate updateActive(Long organizationId, Long routeTemplateId, RouteTemplateActiveUpdateRequest request) {
        RouteTemplate routeTemplate = routeTemplateRepository.findByIdAndOrganizationId(routeTemplateId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("RouteTemplate not found: " + routeTemplateId));

        if (Boolean.TRUE.equals(request.active())) {
            routeTemplate.activate();
        } else {
            routeTemplate.deactivate();
        }

        return routeTemplate;
    }

    @Transactional
    public void delete(Long organizationId, Long routeTemplateId) {
        RouteTemplate routeTemplate = routeTemplateRepository.findByIdAndOrganizationId(routeTemplateId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException("RouteTemplate not found: " + routeTemplateId));

        routeTemplateRepository.delete(routeTemplate);
    }

    @Transactional(readOnly = true)
    public List<RouteTemplateItem> getAll(Long organizationId) {
        return routeTemplateRepository.findAll().stream()
                .filter(routeTemplate -> routeTemplate.getOrganization().getId().equals(organizationId))
                .sorted(Comparator
                        .comparingInt(RouteTemplate::getPriority)
                        .reversed()
                        .thenComparing(RouteTemplate::getId))
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
