package com.clickchecker.analytics.funnel.service;

import com.clickchecker.analytics.funnel.controller.request.FunnelStepRequest;
import com.clickchecker.analytics.funnel.controller.response.AdminFunnelOptionsResponse;
import com.clickchecker.analytics.funnel.controller.response.FunnelReportResponse;
import com.clickchecker.eventtype.service.EventTypeMappingService;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.service.OrganizationMemberAccessService;
import com.clickchecker.route.service.RouteTemplateService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdminFunnelAnalyticsService {

    public static final ZoneId DASHBOARD_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final OrganizationMemberAccessService organizationMemberAccessService;
    private final FunnelAnalyticsService funnelAnalyticsService;
    private final EventTypeMappingService eventTypeMappingService;
    private final RouteTemplateService routeTemplateService;

    @Transactional(readOnly = true)
    public FunnelReportResponse report(
            Long accountId,
            Long organizationId,
            LocalDate from,
            LocalDate to,
            Integer conversionWindowDays,
            List<FunnelStepRequest> steps
    ) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        Instant fromInstant = from.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();
        Instant toInstant = to.atStartOfDay(DASHBOARD_ZONE_ID).toInstant();

        return funnelAnalyticsService.report(
                fromInstant,
                toInstant,
                organizationId,
                null,
                conversionWindowDays,
                steps
        );
    }

    @Transactional(readOnly = true)
    public AdminFunnelOptionsResponse getOptions(Long accountId, Long organizationId) {
        organizationMemberAccessService.requireMemberWithAtLeastRole(
                accountId,
                organizationId,
                OrganizationRole.VIEWER
        );

        List<String> canonicalEventTypes = eventTypeMappingService.getAll(organizationId).stream()
                .filter(item -> item.active() && item.canonicalEventType() != null && !item.canonicalEventType().isBlank())
                .map(item -> item.canonicalEventType().trim())
                .distinct()
                .sorted()
                .toList();

        List<String> routeKeys = routeTemplateService.getAll(organizationId).stream()
                .filter(item -> item.active() && item.routeKey() != null && !item.routeKey().isBlank())
                .map(item -> item.routeKey().trim())
                .distinct()
                .sorted()
                .toList();

        return new AdminFunnelOptionsResponse(canonicalEventTypes, routeKeys);
    }
}
