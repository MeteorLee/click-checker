package com.clickchecker.analytics.activity.controller;

import com.clickchecker.analytics.support.AnalyticsControllerIntegrationTestSupport;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.organization.entity.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class ActivityAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Test
    void aggregateOverview_returnsSummaryWithComparisonAndTopBreakdowns() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        saveEvent(organization, eventUserA, "button_click", "/posts/1", "2026-02-13T00:10:00Z");
        saveEvent(organization, eventUserA, "button_click", "/posts/2", "2026-02-13T00:20:00Z");
        saveEvent(organization, eventUserB, "page_view", "/landing", "2026-02-13T00:30:00Z");
        saveEvent(organization, eventUserA, "button_click", "/posts/3", "2026-02-12T00:10:00Z");

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/overview")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.uniqueUsers").value(2))
                .andExpect(jsonPath("$.identifiedEventRate").isNumber())
                .andExpect(jsonPath("$.eventTypeMappingCoverage").isNumber())
                .andExpect(jsonPath("$.routeMatchCoverage").isNumber())
                .andExpect(jsonPath("$.comparison.current").value(3))
                .andExpect(jsonPath("$.comparison.previous").value(1))
                .andExpect(jsonPath("$.comparison.delta").value(2))
                .andExpect(jsonPath("$.comparison.deltaRate").value(2.0))
                .andExpect(jsonPath("$.comparison.hasPreviousBaseline").value(true))
                .andExpect(jsonPath("$.topRoutes.length()").value(2))
                .andExpect(jsonPath("$.topRoutes[0].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.topRoutes[0].count").value(2))
                .andExpect(jsonPath("$.topRoutes[1].routeKey").value("/landing"))
                .andExpect(jsonPath("$.topRoutes[1].count").value(1))
                .andExpect(jsonPath("$.topEventTypes.length()").value(2))
                .andExpect(jsonPath("$.topEventTypes[0].eventType").value("click"))
                .andExpect(jsonPath("$.topEventTypes[0].count").value(2))
                .andExpect(jsonPath("$.topEventTypes[1].eventType").value("view"))
                .andExpect(jsonPath("$.topEventTypes[1].count").value(1));
    }
}
