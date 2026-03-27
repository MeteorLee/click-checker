package com.clickchecker.analytics.retention.controller;

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
class AdminRetentionAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void retention_returnsDailyRetention_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer-retention");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        var userA = saveEventUser(organization, "user-a");
        var userB = saveEventUser(organization, "user-b");

        saveEvent(organization, userA, "view", "/landing", "2026-03-11T00:10:00Z");
        saveEvent(organization, userA, "click", "/landing", "2026-03-12T00:10:00Z");
        saveEvent(organization, userA, "click", "/landing", "2026-03-18T00:10:00Z");

        saveEvent(organization, userB, "view", "/signup", "2026-03-12T01:10:00Z");
        saveEvent(organization, userB, "click", "/signup", "2026-04-11T01:10:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/retention", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-03-11")
                                .param("to", "2026-03-20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0]").value(1))
                .andExpect(jsonPath("$.days[1]").value(7))
                .andExpect(jsonPath("$.days[2]").value(30))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].cohortUsers").value(1))
                .andExpect(jsonPath("$.items[0].values[0].day").value(1))
                .andExpect(jsonPath("$.items[0].values[0].users").value(1))
                .andExpect(jsonPath("$.items[0].values[0].retentionRate").value(1.0))
                .andExpect(jsonPath("$.items[0].values[1].day").value(7))
                .andExpect(jsonPath("$.items[0].values[1].users").value(1))
                .andExpect(jsonPath("$.items[1].cohortUsers").value(1))
                .andExpect(jsonPath("$.items[1].values[2].day").value(30))
                .andExpect(jsonPath("$.items[1].values[2].users").value(1));
    }

    @Test
    void retention_returnsForbidden_whenRequesterIsNotMember() throws Exception {
        Organization organization = saveOrganization("acme");
        Account outsider = saveAccount("outsider-retention");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/retention", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(outsider))
                                .param("from", "2026-03-11")
                                .param("to", "2026-03-20")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void retention_returnsBadRequest_whenRangeExceedsNinetyDays() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer-retention-range");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/retention", organization.getId())
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
