package com.clickchecker.analytics.eventtype.controller;

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
class AdminEventTypeAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void eventTypes_returnsAggregatedCanonicalEventTypes_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        var eventUser = saveEventUser(organization, "u-1001");
        saveEvent(organization, eventUser, "button_click", "/posts/1", "2026-02-12T15:10:00Z");
        saveEvent(organization, eventUser, "button_click", "/posts/2", "2026-02-12T15:20:00Z");
        saveEvent(organization, eventUser, "page_view", "/landing", "2026-02-12T15:30:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/event-types", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void eventTypes_returnsForbidden_whenRequesterIsNotMember() throws Exception {
        Organization organization = saveOrganization("acme");
        Account outsider = saveAccount("outsider01");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/event-types", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(outsider))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void eventTypes_returnsBadRequest_whenFromIsNotBeforeTo() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/event-types", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-13")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventTypes_returnsBadRequest_whenRangeExceedsNinetyDays() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer02");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/event-types", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-01-01")
                                .param("to", "2026-04-02")
                )
                .andExpect(status().isBadRequest());
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
