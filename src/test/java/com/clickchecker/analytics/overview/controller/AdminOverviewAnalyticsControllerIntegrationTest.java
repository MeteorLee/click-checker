package com.clickchecker.analytics.overview.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.analytics.support.AnalyticsControllerIntegrationTestSupport;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminOverviewAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void overview_returnsSummary_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);
        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        var eventUserA = saveEventUser(organization, "u-1001");
        var eventUserB = saveEventUser(organization, "u-1002");

        saveEvent(organization, eventUserA, "button_click", "/posts/1", "2026-02-12T15:10:00Z");
        saveEvent(organization, eventUserA, "button_click", "/posts/2", "2026-02-12T15:20:00Z");
        saveEvent(organization, eventUserB, "page_view", "/landing", "2026-02-12T15:30:00Z");
        saveEvent(organization, eventUserA, "button_click", "/posts/3", "2026-02-11T15:10:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/overview", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.from").value("2026-02-12T15:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-02-13T15:00:00Z"))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.uniqueUsers").value(2))
                .andExpect(jsonPath("$.comparison.current").value(3))
                .andExpect(jsonPath("$.comparison.previous").value(1))
                .andExpect(jsonPath("$.comparison.delta").value(2))
                .andExpect(jsonPath("$.topRoutes[0].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.topEventTypes[0].eventType").value("click"));
    }

    @Test
    void overview_returnsUnauthorized_whenAccessTokenIsMissing() throws Exception {
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/overview", organization.getId())
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overview_returnsForbidden_whenRequesterIsNotMember() throws Exception {
        Organization organization = saveOrganization("acme");
        Account outsider = saveAccount("outsider01");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/overview", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(outsider))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void overview_returnsNotFound_whenOrganizationDoesNotExist() throws Exception {
        Account viewer = saveAccount("viewer01");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/overview", 9999L)
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organization not found."));
    }

    @Test
    void overview_returnsBadRequest_whenFromIsNotBeforeTo() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/overview", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-13")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void overview_matchesKeyBasedOverviewForCoreMetrics() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        var eventUser = saveEventUser(organization, "u-1001");
        saveEvent(organization, eventUser, "click", "/landing", "2026-02-12T15:05:00Z");
        saveEvent(organization, eventUser, "click", "/landing", "2026-02-12T15:10:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/overview", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.uniqueUsers").value(1));

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/overview")
                                .param("from", "2026-02-12T15:00:00Z")
                                .param("to", "2026-02-13T15:00:00Z")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.uniqueUsers").value(1));
    }

    private Account saveAccount(String loginId) {
        return accountRepository.save(Account.builder()
                .loginId(loginId)
                .passwordHash("hashed-password")
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private OrganizationMember saveMembership(Account account, Organization organization, OrganizationRole role) {
        return organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(organization)
                .role(role)
                .build());
    }

    private String bearerToken(Account account) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(account.getId());
    }
}
