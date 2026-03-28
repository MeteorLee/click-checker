package com.clickchecker.analytics.user.controller;

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
class AdminUserAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void users_returnsIdentifiedNewReturningAndAverage_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        var newUser = saveEventUser(organization, "new-user");
        var returningUser = saveEventUser(organization, "returning-user");

        saveEvent(organization, returningUser, "view", "/landing", "2026-03-01T10:00:00Z");
        saveEvent(organization, newUser, "click", "/signup", "2026-03-12T10:00:00Z");
        saveEvent(organization, newUser, "click", "/signup", "2026-03-13T10:00:00Z");
        saveEvent(organization, returningUser, "click", "/home", "2026-03-14T10:00:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/users", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-03-10")
                                .param("to", "2026-03-17")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.identifiedEvents").value(3))
                .andExpect(jsonPath("$.anonymousEvents").value(0))
                .andExpect(jsonPath("$.identifiedUsers").value(2))
                .andExpect(jsonPath("$.newUsers").value(1))
                .andExpect(jsonPath("$.returningUsers").value(1))
                .andExpect(jsonPath("$.newUserEvents").value(2))
                .andExpect(jsonPath("$.returningUserEvents").value(1))
                .andExpect(jsonPath("$.avgEventsPerIdentifiedUser").value(1.5));
    }

    @Test
    void users_returnsForbidden_whenRequesterIsNotMember() throws Exception {
        Organization organization = saveOrganization("acme");
        Account outsider = saveAccount("outsider01");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/users", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(outsider))
                                .param("from", "2026-03-10")
                                .param("to", "2026-03-17")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void users_returnsBadRequest_whenRangeExceedsNinetyDays() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer02");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/users", organization.getId())
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
