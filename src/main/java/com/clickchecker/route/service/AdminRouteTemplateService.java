package com.clickchecker.route.service;

import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.service.OrganizationMemberAccessService;
import com.clickchecker.route.controller.request.RouteTemplateActiveUpdateRequest;
import com.clickchecker.route.controller.request.RouteTemplateCreateRequest;
import com.clickchecker.route.controller.request.RouteTemplateUpdateRequest;
import com.clickchecker.route.controller.response.RouteTemplateItem;
import com.clickchecker.route.entity.RouteTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRouteTemplateService {

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final RouteTemplateService routeTemplateService;

    @Transactional(readOnly = true)
    public List<RouteTemplateItem> getAll(Long accountId, Long organizationId) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );
        return routeTemplateService.getAll(organizationId);
    }

    @Transactional
    public RouteTemplateItem create(Long accountId, Long organizationId, RouteTemplateCreateRequest request) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        RouteTemplate routeTemplate = routeTemplateService.create(organizationId, request);
        return toItem(routeTemplate);
    }

    @Transactional
    public RouteTemplateItem update(
            Long accountId,
            Long organizationId,
            Long routeTemplateId,
            RouteTemplateUpdateRequest request
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        RouteTemplate routeTemplate = routeTemplateService.update(organizationId, routeTemplateId, request);
        return toItem(routeTemplate);
    }

    @Transactional
    public RouteTemplateItem updateActive(
            Long accountId,
            Long organizationId,
            Long routeTemplateId,
            RouteTemplateActiveUpdateRequest request
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        RouteTemplate routeTemplate = routeTemplateService.updateActive(organizationId, routeTemplateId, request);
        return toItem(routeTemplate);
    }

    @Transactional
    public void delete(Long accountId, Long organizationId, Long routeTemplateId) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.ADMIN
        );
        routeTemplateService.delete(organizationId, routeTemplateId);
    }

    private RouteTemplateItem toItem(RouteTemplate routeTemplate) {
        return new RouteTemplateItem(
                routeTemplate.getId(),
                routeTemplate.getTemplate(),
                routeTemplate.getRouteKey(),
                routeTemplate.getPriority(),
                routeTemplate.isActive()
        );
    }
}
