package com.clickchecker.analytics.funnel.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminFunnelAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void options_returnsActiveCanonicalEventTypesAndRouteKeys_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("funnel-admin");
        Account viewer = saveAccount("viewer-funnel-options");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        saveEventTypeMapping(organization, "page_view", "PAGE_VIEW");
        saveEventTypeMapping(organization, "signup_submit", "SIGN_UP");
        saveRouteTemplate(organization, "/pricing", "/pricing", 100);
        saveRouteTemplate(organization, "/checkout", "/checkout", 90);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/funnels/options", organization.getId())
                                .header(AUTHORIZATION, bearerToken(viewer))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalEventTypes.length()").value(2))
                .andExpect(jsonPath("$.canonicalEventTypes[0]").value("PAGE_VIEW"))
                .andExpect(jsonPath("$.canonicalEventTypes[1]").value("SIGN_UP"))
                .andExpect(jsonPath("$.routeKeys.length()").value(2))
                .andExpect(jsonPath("$.routeKeys[0]").value("/checkout"))
                .andExpect(jsonPath("$.routeKeys[1]").value("/pricing"));
    }

    @Test
    void report_returnsStepUsersAndConversionRates_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("funnel-admin");
        Account viewer = saveAccount("viewer-funnel");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        saveEventTypeMapping(organization, "page_view", "PAGE_VIEW");
        saveEventTypeMapping(organization, "signup_submit", "SIGN_UP");
        saveEventTypeMapping(organization, "purchase_complete", "PURCHASE");
        saveRouteTemplate(organization, "/pricing", "/pricing", 100);

        var user1 = saveEventUser(organization, "user-1");
        var user2 = saveEventUser(organization, "user-2");
        var user3 = saveEventUser(organization, "user-3");

        saveEvent(organization, user1, "page_view", "/pricing", "2026-03-07T09:00:00Z");
        saveEvent(organization, user1, "signup_submit", "/signup", "2026-03-07T10:00:00Z");
        saveEvent(organization, user1, "purchase_complete", "/purchase", "2026-03-10T09:00:00Z");

        saveEvent(organization, user2, "page_view", "/pricing", "2026-03-07T08:00:00Z");
        saveEvent(organization, user2, "signup_submit", "/signup", "2026-03-07T12:00:00Z");

        saveEvent(organization, user3, "page_view", "/blog", "2026-03-07T14:00:00Z");
        saveEvent(organization, user3, "signup_submit", "/signup", "2026-03-07T16:00:00Z");

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/analytics/funnels/report", organization.getId())
                                .header(AUTHORIZATION, bearerToken(viewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "from": "2026-03-01",
                                          "to": "2026-03-08",
                                          "steps": [
                                            { "canonicalEventType": "PAGE_VIEW", "routeKey": "/pricing" },
                                            { "canonicalEventType": "SIGN_UP" },
                                            { "canonicalEventType": "PURCHASE" }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.conversionWindow").value("7d"))
                .andExpect(jsonPath("$.items[0].users").value(2))
                .andExpect(jsonPath("$.items[1].users").value(2))
                .andExpect(jsonPath("$.items[1].conversionRateFromFirstStep").value(1.0))
                .andExpect(jsonPath("$.items[2].users").value(1))
                .andExpect(jsonPath("$.items[2].conversionRateFromFirstStep").value(0.5))
                .andExpect(jsonPath("$.items[2].conversionRateFromPreviousStep").value(0.5));
    }

    @Test
    void report_returnsForbidden_whenRequesterIsNotMember() throws Exception {
        Organization organization = saveOrganization("funnel-admin");
        Account outsider = saveAccount("outsider-funnel");

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/analytics/funnels/report", organization.getId())
                                .header(AUTHORIZATION, bearerToken(outsider))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "from": "2026-03-01",
                                          "to": "2026-03-08",
                                          "steps": [
                                            { "canonicalEventType": "PAGE_VIEW" },
                                            { "canonicalEventType": "SIGN_UP" }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());
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
