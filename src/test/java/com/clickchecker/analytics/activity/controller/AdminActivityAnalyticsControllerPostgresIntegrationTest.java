package com.clickchecker.analytics.activity.controller;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgres")
@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminActivityAnalyticsControllerPostgresIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("click_checker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void activity_returnsSummaryDailyActivityAndHourlyDistribution_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer-activity");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        var firstUser = saveEventUser(organization, "user-1");
        var secondUser = saveEventUser(organization, "user-2");

        saveEvent(organization, firstUser, "view", "/landing", "2026-03-11T00:10:00Z");
        saveEvent(organization, secondUser, "click", "/signup", "2026-03-11T02:20:00Z");
        saveEvent(organization, firstUser, "click", "/pricing", "2026-03-12T05:00:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/activity", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-03-11")
                                .param("to", "2026-03-13")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.activeDays").value(2))
                .andExpect(jsonPath("$.dailyActivity.length()").value(2))
                .andExpect(jsonPath("$.dailyActivity[0].eventCount").value(2))
                .andExpect(jsonPath("$.dailyActivity[0].uniqueUserCount").value(2))
                .andExpect(jsonPath("$.dailyActivity[1].eventCount").value(1))
                .andExpect(jsonPath("$.hourlyDistribution.length()").value(24))
                .andExpect(jsonPath("$.hourlyDistribution[9].eventCount").value(1))
                .andExpect(jsonPath("$.hourlyDistribution[11].eventCount").value(1))
                .andExpect(jsonPath("$.hourlyDistribution[14].eventCount").value(1))
                .andExpect(jsonPath("$.peakDayEventCount").value(2));
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
