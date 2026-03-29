package com.clickchecker.analytics.trend.controller;

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
class AdminTrendAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void trends_returnsForbidden_whenRequesterIsNotMember() throws Exception {
        Organization organization = saveOrganization("acme");
        Account outsider = saveAccount("outsider01");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/trends", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(outsider))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                                .param("bucket", "DAY")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void trends_returnsBadRequest_whenHourlyRangeExceedsSevenDays() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer02");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/trends", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-01")
                                .param("to", "2026-02-10")
                                .param("bucket", "HOUR")
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
